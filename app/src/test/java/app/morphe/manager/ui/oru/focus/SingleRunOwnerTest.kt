/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import app.morphe.manager.ui.oru.install.FakeExecutor
import app.morphe.manager.ui.oru.install.FakeInstallerReadings
import app.morphe.manager.ui.oru.install.FakePackages
import app.morphe.manager.ui.oru.install.FakeSignatureSource
import app.morphe.manager.ui.oru.install.OruExecution
import app.morphe.manager.ui.oru.install.OruInstallCoordinator
import app.morphe.manager.ui.oru.install.OruInstallUi
import app.morphe.manager.ui.oru.install.testRequest
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.ui.model.navigation.Patcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A patcher for the owner tests. Progress and the success screen are snapshot state, so the owner's snapshot
 * flows see every change (call Snapshot.sendApplyNotifications after a change made outside a snapshot).
 */
class FakeSingleRunPatcher(
    progress: Float = 0f,
    patchesProgress: Pair<Int, Int> = 0 to 0,
    val succeededFlow: MutableStateFlow<Boolean?> = MutableStateFlow(null),
    val cancelledFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
) : SingleRunPatcher {
    override var progress: Float by mutableStateOf(progress)
    override var patchesProgress: Pair<Int, Int> by mutableStateOf(patchesProgress)
    override var showSuccessScreen: Boolean by mutableStateOf(false)
    override val patcherSucceeded: StateFlow<Boolean?> = succeededFlow
    override val patcherCancelled: StateFlow<Boolean> = cancelledFlow
    var cancelCalled = false
    var destroyCalled = false

    override fun cancelPatching() {
        cancelCalled = true
        cancelledFlow.value = true
    }

    override fun destroy() {
        destroyCalled = true
    }
}

class SingleRunOwnerTest {

    private lateinit var tempDir: File
    private lateinit var activeRunTracker: ActiveRunTracker

    @Before
    fun setUp() {
        tempDir = File.createTempFile("single_run_test", "").apply {
            delete()
            mkdirs()
        }
        activeRunTracker = ActiveRunTracker()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun testParams(pkg: String) = Patcher.ViewModelParams(
        selectedApp = SelectedApp.Installed(packageName = pkg, version = "1.0.0"),
        selectedPatches = emptyMap(),
        options = emptyMap()
    )

    private fun testItem(pkg: String) = HomeAppItem(
        id = pkg,
        packageName = pkg,
        displayName = "App $pkg",
        gradientColors = emptyList(),
        installedApp = null,
        packageInfo = null,
        version = "1.0.0",
        isPinnedByDefault = false,
        isInstalledOnDevice = false,
        isDeleted = false,
        isInstallStateNotPatched = false,
        isInstallStateUnknown = false,
        isInstallStatePending = false,
        savedApkFile = null,
        hasUpdate = false,
        versionStatus = null,
        patchCount = 0,
        isClone = false,
    )

    private fun newOwner(
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
        factory: (Patcher.ViewModelParams) -> SingleRunPatcher = { throw UnsupportedOperationException() },
    ) = SingleRunOwner(
        runs = activeRunTracker,
        filesDir = tempDir,
        patcherFactory = factory,
        scope = scope,
    )

    /** A coordinator over the shared install fakes, with the executor the test controls. */
    private fun coordinator(executor: FakeExecutor = FakeExecutor()) = OruInstallCoordinator(
        request = testRequest(),
        installers = FakeInstallerReadings(),
        packages = FakePackages(),
        signatures = FakeSignatureSource(),
        executor = executor,
        onRootModeChosen = {},
    )

    /** Marks the run finished and successful, with its success screen shown (the Done outcome). */
    private fun finishSuccessfully(fake: FakeSingleRunPatcher) {
        fake.succeededFlow.value = true
        Snapshot.withMutableSnapshot {
            fake.showSuccessScreen = true
        }
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `initial state has no active run and can start`() {
        val owner = newOwner()

        assertFalse(owner.isActive)
        assertFalse(owner.isBusy)
        assertNull(owner.activePackageKey)
        assertNull(owner.currentRun.value)
        assertFalse(owner.consumeLastRunStopped())
        assertTrue(owner.canStart())
    }

    @Test
    fun `process death flag records the Focus key and is consumed once`() {
        val stopFlag = File(tempDir, "oru_active_run_flag")
        stopFlag.writeText("com.example.crashed")

        val owner = newOwner()

        assertFalse(stopFlag.exists())
        assertEquals("com.example.crashed", owner.stoppedPackageKey.value)
        assertTrue(owner.consumeLastRunStopped())
        assertFalse(owner.consumeLastRunStopped())
    }

    @Test
    fun `normal launch without crash flag has no stopped run`() {
        val owner = newOwner()

        assertNull(owner.stoppedPackageKey.value)
        assertFalse(owner.consumeLastRunStopped())
    }

    @Test
    fun `stopped run is forgotten only when its own Focus starts again`() {
        File(tempDir, "oru_active_run_flag").writeText("com.example.lost")
        val owner = newOwner()

        owner.clearStoppedRun("com.example.other")
        assertEquals("com.example.lost", owner.stoppedPackageKey.value)

        owner.clearStoppedRun("com.example.lost")
        assertNull(owner.stoppedPackageKey.value)
    }

    @Test
    fun `tracker shows a run only from the owner that claimed it`() {
        val tracker = ActiveRunTracker()
        val run = ActiveRun(packageKey = "key1", packageName = "com.test", label = "Test", progress = 0.5f, done = 5, total = 10)
        val other = run.copy(packageKey = "key2")
        val owner1 = Any()
        val owner2 = Any()

        tracker.publish(owner1, run)
        assertNull(tracker.run.value)

        tracker.claim(owner1)
        tracker.publish(owner1, run)
        assertEquals(run, tracker.run.value)

        tracker.publish(owner2, other)
        assertEquals(run, tracker.run.value)

        tracker.clear(owner2)
        assertEquals(run, tracker.run.value)

        tracker.clear(owner1)
        assertNull(tracker.run.value)
    }

    @Test
    fun `tracker drops an update that arrives after its owner cleared`() {
        val tracker = ActiveRunTracker()
        val run = ActiveRun(packageKey = "key1", packageName = "com.test", label = "Test", progress = 0.5f, done = 5, total = 10)
        val owner = Any()

        tracker.claim(owner)
        tracker.publish(owner, run)
        tracker.clear(owner)
        tracker.publish(owner, run.copy(progress = 0.9f))

        assertNull(tracker.run.value)
    }

    private companion object {
        const val PROGRESS_TOLERANCE = 1e-5f
    }

    @Test
    fun `run is tracked from start through progress to done`() = runBlocking {
        val fakePatcher = FakeSingleRunPatcher(progress = 0.25f, patchesProgress = 2 to 8)
        val owner = newOwner { fakePatcher }

        val pkg = "com.example.app"
        owner.getOrStartRun(pkg, testItem(pkg), testParams(pkg))

        assertTrue(owner.isActive)
        assertTrue(owner.isBusy)
        assertEquals(pkg, owner.activePackageKey)
        assertEquals(OruRunStatus.Working, owner.runStatus.value)

        val stopFlag = File(tempDir, "oru_active_run_flag")
        assertTrue(stopFlag.exists())
        assertEquals(pkg, stopFlag.readText())

        // The accessory shows the run's fill, the same as the run screen (see oruRunFill): a patch run's 0.25 is
        // 0.225 of a run that still has its install ahead
        val tracked = activeRunTracker.run.value
        assertNotNull(tracked)
        assertEquals(pkg, tracked.packageKey)
        assertEquals(0.225f, tracked.progress, PROGRESS_TOLERANCE)
        assertEquals(2, tracked.done)
        assertEquals(8, tracked.total)

        // A change of progress reaches the accessory, because the owner observes the patcher's snapshot state
        fakePatcher.progress = 0.6f
        fakePatcher.patchesProgress = 5 to 8
        Snapshot.sendApplyNotifications()

        val updated = activeRunTracker.run.value
        assertNotNull(updated)
        assertEquals(0.54f, updated.progress, PROGRESS_TOLERANCE)
        assertEquals(5, updated.done)

        // The patch run is signed, but the install is still ahead: the accessory stops at the install share, not full
        fakePatcher.progress = 1f
        Snapshot.sendApplyNotifications()

        val signed = activeRunTracker.run.value
        assertNotNull(signed)
        assertEquals(0.9f, signed.progress, PROGRESS_TOLERANCE)
        assertEquals(OruRunStatus.Working, owner.runStatus.value)

        // Done releases the accessory, so the tracker holds nothing after the run's last value
        finishSuccessfully(fakePatcher)

        assertEquals(OruRunStatus.Done, owner.runStatus.value)
        assertNull(activeRunTracker.run.value)
        assertFalse(stopFlag.exists())
        // No install has been begun, so the finished run does not hold the one-run rule
        assertFalse(owner.isBusy)
    }

    @Test
    fun `the run carries its Focus key into the params its patcher is built from`() {
        var received: Patcher.ViewModelParams? = null
        val owner = newOwner { params ->
            received = params
            FakeSingleRunPatcher()
        }

        val pkg = "com.example.clone"
        owner.getOrStartRun(pkg, testItem(pkg), testParams("com.example.source"))

        assertEquals(pkg, received?.focusKey)
    }

    @Test
    fun `a second app is refused while the first run is busy`() = runBlocking {
        val fakePatcher = FakeSingleRunPatcher()
        var created = 0
        val owner = newOwner {
            created++
            fakePatcher
        }

        val pkg1 = "com.example.app1"
        val pkg2 = "com.example.app2"

        val first = owner.getOrStartRun(pkg1, testItem(pkg1), testParams(pkg1))
        assertNotNull(first)
        assertFalse(owner.canStart())
        assertNull(owner.getOrStartRun(pkg2, testItem(pkg2), testParams(pkg2)))
        assertEquals(pkg1, owner.activePackageKey)
        assertEquals(1, created)
    }

    @Test
    fun `a busy run of the same Focus is attached to, not replaced`() = runBlocking {
        val fakePatcher = FakeSingleRunPatcher()
        var created = 0
        val owner = newOwner {
            created++
            fakePatcher
        }

        val pkg = "com.example.attach"
        val first = owner.getOrStartRun(pkg, testItem(pkg), testParams(pkg))
        val again = owner.getOrStartRun(pkg, testItem(pkg), testParams(pkg))

        assertSame(first, again)
        assertSame(first, owner.getRun(pkg))
        assertEquals(1, created)
    }

    @Test
    fun `a failed run is released when its Focus starts again, so the start gets a new patcher`() = runBlocking {
        val fake1 = FakeSingleRunPatcher()
        val fake2 = FakeSingleRunPatcher()
        var created = 0
        val owner = newOwner {
            created++
            if (created == 1) fake1 else fake2
        }

        val pkg = "com.example.retry"
        owner.getOrStartRun(pkg, testItem(pkg), testParams(pkg))

        fake1.succeededFlow.value = false
        assertEquals(OruRunStatus.Failed, owner.runStatus.value)
        assertFalse(owner.isBusy)
        assertTrue(owner.canStart())

        val second = owner.getOrStartRun(pkg, testItem(pkg), testParams(pkg))

        assertSame(fake2, second)
        assertEquals(2, created)
        assertTrue(fake1.destroyCalled)
    }

    @Test
    fun `cancel then start allows second run`() = runBlocking {
        val fake1 = FakeSingleRunPatcher()
        var created = 0
        val fake2 = FakeSingleRunPatcher()
        val owner = newOwner {
            created++
            if (created == 1) fake1 else fake2
        }

        val pkg1 = "com.example.app1"
        val pkg2 = "com.example.app2"

        owner.getOrStartRun(pkg1, testItem(pkg1), testParams(pkg1))
        assertTrue(owner.isActive)

        owner.cancelRun()

        assertTrue(fake1.cancelCalled)
        assertEquals(OruRunStatus.Cancelled, owner.runStatus.value)
        assertFalse(owner.isActive)
        assertNull(activeRunTracker.run.value)

        assertTrue(owner.canStart())
        owner.getOrStartRun(pkg2, testItem(pkg2), testParams(pkg2))

        assertTrue(owner.isActive)
        assertEquals(pkg2, owner.activePackageKey)
    }

    @Test
    fun `finishRun destroys active run and cleans tracker`() = runBlocking {
        val fakePatcher = FakeSingleRunPatcher()
        val owner = newOwner { fakePatcher }

        val pkg = "com.example.finish"
        owner.getOrStartRun(pkg, testItem(pkg), testParams(pkg))
        assertTrue(owner.isActive)

        owner.finishRun()

        assertFalse(owner.isActive)
        assertFalse(owner.isBusy)
        assertNull(owner.currentRun.value)
        assertNull(owner.activePackageKey)
        assertNull(owner.runStatus.value)
        assertNull(activeRunTracker.run.value)
        assertTrue(fakePatcher.destroyCalled)
    }

    @Test
    fun `finished run whose install is under way stays busy and is attached to on return`() = runBlocking {
        val fakePatcher = FakeSingleRunPatcher()
        var created = 0
        val owner = newOwner {
            created++
            fakePatcher
        }

        val pkg = "com.example.done"
        val patcher = owner.getOrStartRun(pkg, testItem(pkg), testParams(pkg))
        finishSuccessfully(fakePatcher)
        assertEquals(OruRunStatus.Done, owner.runStatus.value)

        val executor = FakeExecutor()
        val install = coordinator(executor)
        assertTrue(owner.beginInstall(picked = null) { install })
        assertFalse(owner.beginInstall(picked = null) { install }, "an install is begun once")

        assertTrue(owner.isBusy)
        assertFalse(owner.canStart())
        assertNull(owner.getOrStartRun("com.example.other", testItem("com.example.other"), testParams("com.example.other")))
        assertEquals(pkg, owner.activePackageKey)
        assertFalse(fakePatcher.destroyCalled, "the install's run is not released while the install is under way")

        // The Focus comes back: it attaches to the same patcher, and no second run is made
        assertSame(patcher, owner.getOrStartRun(pkg, testItem(pkg), testParams(pkg)))
        assertSame(patcher, owner.getRun(pkg))
        assertEquals(1, created)
        assertSame(install, owner.install.value)
    }

    @Test
    fun `the run is released once its install has an outcome`() = runBlocking {
        val fakePatcher = FakeSingleRunPatcher()
        val owner = newOwner { fakePatcher }

        val pkg = "com.example.outcome"
        owner.getOrStartRun(pkg, testItem(pkg), testParams(pkg))
        finishSuccessfully(fakePatcher)

        val executor = FakeExecutor()
        val install = coordinator(executor)
        owner.beginInstall(picked = null) { install }
        assertTrue(owner.isBusy)

        executor.execution = OruExecution.Installed("com.example.outcome")
        install.refresh()

        assertNull(owner.currentRun.value)
        assertFalse(owner.isBusy)
        assertTrue(fakePatcher.destroyCalled)
    }

    @Test
    fun `isRunBusy holds the one-run rule only while work or an unfinished install remains`() {
        // No run: never busy
        for (status in listOf(null, OruRunStatus.Working, OruRunStatus.Done, OruRunStatus.Failed, OruRunStatus.Cancelled)) {
            for (phase in InstallPhase.entries) {
                assertFalse(isRunBusy(hasRun = false, status = status, install = phase))
            }
        }
        // Working is busy whatever the install says
        for (phase in InstallPhase.entries) {
            assertTrue(isRunBusy(hasRun = true, status = OruRunStatus.Working, install = phase))
        }
        // Done is busy only while its install is pending or running
        assertTrue(isRunBusy(hasRun = true, status = OruRunStatus.Done, install = InstallPhase.PENDING))
        assertTrue(isRunBusy(hasRun = true, status = OruRunStatus.Done, install = InstallPhase.RUNNING))
        assertFalse(isRunBusy(hasRun = true, status = OruRunStatus.Done, install = InstallPhase.NONE))
        assertFalse(isRunBusy(hasRun = true, status = OruRunStatus.Done, install = InstallPhase.SETTLED))
        // Failed and cancelled never are
        for (phase in InstallPhase.entries) {
            assertFalse(isRunBusy(hasRun = true, status = OruRunStatus.Failed, install = phase))
            assertFalse(isRunBusy(hasRun = true, status = OruRunStatus.Cancelled, install = phase))
        }
        // A run whose status is not yet known is working
        assertTrue(isRunBusy(hasRun = true, status = null, install = InstallPhase.NONE))
    }

    @Test
    fun `an install is settled only by installed or failed`() {
        assertTrue(isInstallSettled(OruInstallUi.Installed(installerLabel = null)))
        assertTrue(isInstallSettled(OruInstallUi.Failed(message = "failed")))
        assertFalse(isInstallSettled(OruInstallUi.Checking))
    }
}
