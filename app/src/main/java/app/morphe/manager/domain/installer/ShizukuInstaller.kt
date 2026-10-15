/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/domain/installer/ShizukuInstaller.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.domain.installer

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageInstallerSession
import android.content.pm.IPackageManager
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import androidx.core.content.pm.PackageInfoCompat
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * Performs silent APK installation via the Shizuku/Sui privileged service.
 *
 * Communicates directly with the system [IPackageInstaller] through Shizuku binder IPC,
 * bypassing the user confirmation dialog required by the standard PackageInstaller API.
 *
 * Expects the binder bootstrap [ShizukuEnvironment] performs to have already run.
 */
class ShizukuInstaller(private val app: Application) {

    /** Result of a silent PackageInstaller operation. */
    data class OperationResult(val status: Int, val message: String?)

    /**
     * An [IntentSender] paired with the [OperationResult] the intent delivered to it completes,
     * which is the only way the system reports the outcome of a session.
     */
    private class PendingOperation(onUserAction: (Intent) -> Unit) {
        val result = CompletableDeferred<OperationResult>()
        val intentSender: IntentSender = IntentSenderCompat.create { intent ->
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            Log.d(InstallWaitPolicy.LOG_TAG, "step=shizuku-status status=$status")
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                // The system waits for a confirmation; the status that follows is the real outcome, so keep waiting
                onUserAction(intent)
            } else {
                result.complete(
                    OperationResult(
                        status = status,
                        message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    )
                )
            }
        }
    }

    /**
     * Silently installs [sourceFile] via Shizuku/Sui. Suspends until the installation completes.
     *
     * If no status arrives within [InstallWaitPolicy.INSTALL_STATUS_LIMIT_MS] after the commit, the package
     * manager decides: a matching installed version code is a success, anything else a timeout failure.
     *
     * @param sourceFile The APK file to install.
     * @param expectedPackage The expected package name of the APK, used to configure the session.
     * @param installerPackageNameOverride Optional package name to report as the installer.
     * @param onUserAction Shows the confirmation the system asks for; the install keeps waiting for its result.
     * @throws InstallerOperationException if the installation fails or is aborted.
     */
    @SuppressLint("RequestInstallPackagesPolicy")
    suspend fun install(
        sourceFile: File,
        expectedPackage: String,
        installerPackageNameOverride: String? = null,
        onUserAction: (Intent) -> Unit = {}
    ): OperationResult = withContext(Dispatchers.IO) {
        val packageInstaller = obtainPackageInstaller()
        val packageInstallerWrapper = packageInstaller.asPackageInstaller(installerPackageNameOverride)
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            runCatching { setAppPackageName(expectedPackage) }
            @Suppress("WrongConstant")
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setRequestUpdateOwnership(true)
            }
        }
        PackageInstallerCompat.applyFlags(params)
        val sessionId = packageInstallerWrapper.createSession(params)
        val sessionBinder = IPackageInstallerSession.Stub.asInterface(
            ShizukuBinderWrapper(packageInstaller.openSession(sessionId).asBinder())
        )
        val session = PackageInstallerCompat.createSession(sessionBinder)
        // Set once the system holds the session (committed, or its result arrived). Cancelling the wait before
        // that abandons the session, so a Stop never leaves a half-written session behind
        var completed = false

        try {
            Log.d(InstallWaitPolicy.LOG_TAG, "step=shizuku-write package=$expectedPackage session=$sessionId")
            sourceFile.inputStream().use { input ->
                session.openWrite(BASE_APK_NAME, 0, sourceFile.length()).use { output ->
                    input.copyApkTo(output)
                    session.fsync(output)
                }
            }

            val operation = PendingOperation(onUserAction)
            Log.d(InstallWaitPolicy.LOG_TAG, "step=shizuku-commit package=$expectedPackage session=$sessionId")
            session.commit(operation.intentSender)
            val result = withTimeoutOrNull(InstallWaitPolicy.INSTALL_STATUS_LIMIT_MS) { operation.result.await() }
            if (result == null) {
                // The commit is with the system now; abandoning it could cancel an install that is still going
                completed = true
                val landed = installedVersionMatches(expectedPackage, sourceFile)
                Log.w(InstallWaitPolicy.LOG_TAG, "step=shizuku-status-timeout package=$expectedPackage landed=$landed")
                return@withContext if (landed) {
                    OperationResult(PackageInstaller.STATUS_SUCCESS, null)
                } else {
                    OperationResult(PackageInstaller.STATUS_FAILURE, "Timed out waiting for the install result")
                }
            }
            // Set before the status check - a delivered failure leaves nothing to abandon
            completed = true
            Log.d(InstallWaitPolicy.LOG_TAG, "step=shizuku-result package=$expectedPackage status=${result.status}")
            result.requireSuccess()
        } finally {
            if (!completed) {
                runCatching { packageInstallerWrapper.abandonSession(sessionId) }
            }
            runCatching { session.close() }
        }
    }

    /**
     * Silently uninstalls [packageName] via Shizuku/Sui. Suspends until uninstall completes.
     *
     * @throws InstallerOperationException if the uninstall fails or is aborted.
     */
    suspend fun uninstall(
        packageName: String,
        onUserAction: (Intent) -> Unit = {}
    ): OperationResult = withContext(Dispatchers.IO) {
        val packageInstallerWrapper = obtainPackageInstaller().asPackageInstaller()
        val operation = PendingOperation(onUserAction)

        Log.d(InstallWaitPolicy.LOG_TAG, "step=shizuku-uninstall package=$packageName")
        packageInstallerWrapper.uninstall(packageName, operation.intentSender)
        withTimeout(UNINSTALL_RESULT_TIMEOUT) { operation.result.await() }.requireSuccess()
    }

    /** Whether [packageName] is installed at the version code [apk] carries. Read off the package manager. */
    private fun installedVersionMatches(packageName: String, apk: File): Boolean {
        val installed = runCatching { app.packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val archive = app.packageManager.getPackageArchiveInfo(apk.path, 0)
        return InstallWaitPolicy.installLanded(
            installedVersionCode = installed?.let { PackageInfoCompat.getLongVersionCode(it) },
            archiveVersionCode = archive?.let { PackageInfoCompat.getLongVersionCode(it) }
        )
    }

    /** Returns this result, or throws [InstallerOperationException] when the operation failed. */
    private fun OperationResult.requireSuccess(): OperationResult {
        if (status != PackageInstaller.STATUS_SUCCESS) {
            throw InstallerOperationException(status, message)
        }
        return this
    }

    /** Resolves [IPackageInstaller] via the Shizuku binder IPC. */
    private fun obtainPackageInstaller(): IPackageInstaller {
        val binder = SystemServiceHelper.getSystemService("package")
            ?: throw IOException("Package service unavailable")
        try {
            val manager = IPackageManager.Stub.asInterface(ShizukuBinderWrapper(binder))
            val installer = manager.packageInstaller
            return IPackageInstaller.Stub.asInterface(ShizukuBinderWrapper(installer.asBinder()))
        } catch (error: RemoteException) {
            throw IOException(error)
        }
    }

    /** Wraps this remote installer in a [PackageInstaller] attributed to the caller's identity. */
    private fun IPackageInstaller.asPackageInstaller(
        installerPackageNameOverride: String? = null
    ): PackageInstaller {
        val identity = installerIdentity(installerPackageNameOverride)
        return PackageInstallerCompat.createPackageInstaller(
            this,
            identity.packageName,
            identity.attributionTag,
            identity.userId
        )
    }

    /** Returns the user ID derived from the current process UID. */
    private fun currentUserId(): Int = Process.myUid() / 100000

    private fun installerIdentity(installerPackageNameOverride: String? = null): InstallerIdentity {
        val isRoot = runCatching { Shizuku.getUid() }.getOrDefault(-1) == 0
        return InstallerIdentity(
            packageName = installerPackageNameOverride
                ?: if (isRoot) app.packageName else SHELL_PACKAGE,
            attributionTag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) app.attributionTag else null,
            userId = if (isRoot) currentUserId() else 0
        )
    }

    private data class InstallerIdentity(
        val packageName: String,
        val attributionTag: String?,
        val userId: Int
    )

    /** Thrown when a package operation fails or is aborted by the system. */
    class InstallerOperationException(val status: Int, override val message: String?) : Exception(message)

    companion object {
        private const val SHELL_PACKAGE = "com.android.shell"
        private const val BASE_APK_NAME = "base.apk"
        private val UNINSTALL_RESULT_TIMEOUT = 30.seconds
    }
}

/**
 * Reflection-based compatibility helpers for constructing [PackageInstaller] and
 * [PackageInstaller.Session] instances from raw Shizuku binder objects.
 *
 * Required because the public API does not expose constructors that accept an [IPackageInstaller]
 * directly - these are internal to the system and accessed via reflection.
 */
private object PackageInstallerCompat {
    private const val INSTALL_REPLACE_EXISTING = 0x00000002

    /**
     * Constructs a [PackageInstaller] wrapping the given [remote] binder.
     * Uses the appropriate constructor signature for the running API level.
     */
    fun createPackageInstaller(
        remote: IPackageInstaller,
        installerPackageName: String,
        installerAttributionTag: String?,
        userId: Int
    ): PackageInstaller {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            newHiddenInstance(
                PackageInstaller::class.java,
                arrayOf(
                    IPackageInstaller::class.java,
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType!!
                ),
                remote, installerPackageName, installerAttributionTag, userId
            )
        } else {
            newHiddenInstance(
                PackageInstaller::class.java,
                arrayOf(
                    IPackageInstaller::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType!!
                ),
                remote, installerPackageName, userId
            )
        }
    }

    /** Constructs a [PackageInstaller.Session] wrapping the given [remote] session binder. */
    fun createSession(remote: IPackageInstallerSession): PackageInstaller.Session =
        newHiddenInstance(
            PackageInstaller.Session::class.java,
            arrayOf(IPackageInstallerSession::class.java),
            remote
        )

    /**
     * Sets [INSTALL_REPLACE_EXISTING] on the internal installFlags field of
     * [PackageInstaller.SessionParams] via reflection. Required for Shizuku installs to correctly
     * replace an existing package - without this flag the system may return
     * [PackageInstaller.STATUS_FAILURE_CONFLICT] even when signatures match.
     */
    fun applyFlags(params: PackageInstaller.SessionParams) {
        // A fresh install works without the flag, so a missing field must not take the whole Shizuku path down
        try {
            setHiddenInstallFlag(params, INSTALL_REPLACE_EXISTING)
        } catch (e: HiddenApiException) {
            Log.w("ShizukuInstaller", "Updating an installed app may fail: ${e.message}")
        }
    }

}

/**
 * Raised when a hidden system member this installer relies on is missing on the running Android
 * build, or refuses to be used. The message names the class, constructor or field involved.
 */
class HiddenApiException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** Calls the hidden constructor [owner]([parameterTypes]) with [args], naming the one that is missing. */
internal fun <T : Any> newHiddenInstance(
    owner: Class<T>,
    parameterTypes: Array<Class<*>>,
    vararg args: Any?
): T {
    val signature = "${owner.name}(${parameterTypes.joinToString { it.name }})"
    val constructor = try {
        owner.getDeclaredConstructor(*parameterTypes).apply { isAccessible = true }
    } catch (error: NoSuchMethodException) {
        throw HiddenApiException("$signature is not available on this Android build", error)
    }
    return try {
        constructor.newInstance(*args)
    } catch (error: ReflectiveOperationException) {
        throw HiddenApiException("Calling $signature failed", error)
    }
}

/** Adds [flag] to the hidden installFlags field of [params], naming the field if it is missing. */
internal fun setHiddenInstallFlag(params: Any, flag: Int) {
    val owner = params.javaClass
    val field = try {
        owner.getDeclaredField("installFlags").apply { isAccessible = true }
    } catch (error: NoSuchFieldException) {
        throw HiddenApiException("${owner.name}.installFlags is not available on this Android build", error)
    }
    field.setInt(params, field.getInt(params) or flag)
}

/**
 * Creates an [IntentSender] backed by a custom [android.content.IIntentSender] stub.
 *
 * Required because Shizuku sessions deliver their result intent through an [IntentSender],
 * but the standard [android.app.PendingIntent]-based approach is unavailable in the Shizuku context.
 */
private object IntentSenderCompat {
    /**
     * Returns an [IntentSender] that invokes [callback] with the delivered [Intent].
     */
    fun create(callback: (Intent) -> Unit): IntentSender {
        val binder = object : android.content.IIntentSender.Stub() {
            override fun send(
                code: Int,
                intent: Intent?,
                resolvedType: String?,
                finishedReceiver: android.content.IIntentReceiver?,
                requiredPermission: String?,
                options: Bundle?
            ): Int {
                intent?.let(callback)
                return 0
            }

            override fun send(
                code: Int,
                intent: Intent?,
                resolvedType: String?,
                whitelistToken: IBinder?,
                finishedReceiver: android.content.IIntentReceiver?,
                requiredPermission: String?,
                options: Bundle?
            ) {
                intent?.let(callback)
            }
        }
        return newHiddenInstance(
            IntentSender::class.java,
            arrayOf(android.content.IIntentSender::class.java),
            binder
        )
    }
}
