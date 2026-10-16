/*
 * Oru changes to the resource decoding of morphe-patcher.
 */

package app.morphe.patcher.resource.coder

import com.reandroid.apk.ApkModule
import com.reandroid.apk.ApkModuleXmlDecoder
import com.reandroid.apk.ResFile
import com.reandroid.arsc.chunk.PackageBlock
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.arsc.value.Entry
import app.morphe.patcher.util.PhaseProfiler
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Logger
import java.util.zip.ZipFile
import kotlin.concurrent.withLock

/** Experiment switch: `min` (default) or `norm`, the priority of the background raw resource writers. */
internal const val RAW_WRITER_PRIORITY_PROPERTY = "oru.patcher.rawWriterPriority"

internal fun rawWriterPriority(value: String? = System.getProperty(RAW_WRITER_PRIORITY_PROPERTY)): Int =
    when (value ?: "min") {
        "min" -> Thread.MIN_PRIORITY
        "norm" -> Thread.NORM_PRIORITY
        else -> throw IllegalArgumentException("$RAW_WRITER_PRIORITY_PROPERTY must be min or norm, not $value")
    }

/**
 * Raw resource files (images, fonts and other non-XML files under `res/`) that are written to the working
 * directory on background threads while the rest of the decoding and the patching go on.
 *
 * Patches see a complete tree: every access goes through [awaitFor], which writes what is still queued under
 * that path itself and waits for what a worker is writing, so a directory is complete by the time a patch
 * lists it. Files are written under their final names; reads block on [awaitFor] until write completion.
 * A file a patch created first is never overwritten, and a file that fails to write is removed.
 *
 * Holds no reference to the decoded resource table, so it can outlive the module the table was parsed into.
 */
internal class LazyResFiles(
    private val apkFile: File,
    private val workerCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 3),
    private val onWritten: (File) -> Unit = {},
    private val afterCopy: () -> Unit = {},
    /** Called with the lock held just before a caller waits for in-flight writes. Lets tests observe the wait. */
    private val beforeWait: () -> Unit = {},
) : Closeable {
    private class Pending(val archivePath: String, val target: File)

    private val lock = ReentrantLock()
    private val settled = lock.newCondition()
    private val pending = TreeMap<String, Pending>()
    private val inFlight = HashSet<String>()
    /** Writes that failed, by target path, until an await of that path reports them. */
    private val failures = LinkedHashMap<String, Throwable>()
    private var closed = false
    private var sealed = false
    private var released = false
    private val workers = ArrayList<Thread>()
    private val written = AtomicInteger()
    private val copyBuffer = ThreadLocal<ByteArray?>()
    /** Streams from [open] and writes that are reading the input APK. The APK stays open while any is active. */
    private val activeUsers = AtomicInteger(0)

    /** Opened on first use and closed once nothing can read it, so a file that was never read is never opened. */
    private var zipFile: ZipFile? = null

    /** Opens an entry of the input APK. The caller closes the stream, which also releases its inflater. */
    fun open(archivePath: String): java.io.InputStream {
        activeUsers.incrementAndGet()
        try {
            val zip = zip()
            val zipEntry = zip.getEntry(archivePath)
                ?: throw IOException("Resource $archivePath is missing from ${apkFile.name}")
            val stream = zip.getInputStream(zipEntry)
            return object : java.io.FilterInputStream(stream) {
                private var closed = false
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        if (!closed) {
                            closed = true
                            activeUsers.decrementAndGet()
                            lock.withLock { settled.signalAll() }
                            releaseIfDone()
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            activeUsers.decrementAndGet()
            lock.withLock { settled.signalAll() }
            releaseIfDone()
            throw t
        }
    }

    private fun zip(): ZipFile = lock.withLock {
        check(!released) { "The input APK of the resource files is closed" }
        zipFile ?: ZipFile(apkFile).also { zipFile = it }
    }

    /** Files written so far, by workers or on demand. */
    val writtenCount: Int get() = written.get()

    val queued: Int get() = lock.withLock { pending.size }

    fun defer(target: File, archivePath: String) {
        lock.withLock { pending[keyOf(target)] = Pending(archivePath, target) }
    }

    /** Starts the background writers. Call once everything is queued and no more entries will be opened. */
    fun start() {
        val writerPriority = rawWriterPriority()
        lock.withLock { sealed = true }
        releaseIfDone()
        repeat(workerCount) { index ->
            Thread({ work() }, "oru-res-writer-$index").apply {
                isDaemon = true
                priority = writerPriority
                workers += this
                start()
            }
        }
    }

    private fun work() {
        while (true) {
            val next = lock.withLock {
                if (closed) return
                pending.pollFirstEntry()?.value?.also { inFlight += keyOf(it.target) }
            } ?: return
            finish(next)
        }
    }

    private fun finish(entry: Pending) {
        try {
            write(entry)
        } catch (t: Throwable) {
            lock.withLock { failures.putIfAbsent(keyOf(entry.target), t) }
        } finally {
            lock.withLock {
                inFlight -= keyOf(entry.target)
                settled.signalAll()
            }
            releaseIfDone()
        }
    }

    /** Lets go of the input APK once nothing is left to write. */
    private fun releaseIfDone() {
        val zipToClose = lock.withLock {
            if (released || !sealed || pending.isNotEmpty() || inFlight.isNotEmpty() || activeUsers.get() > 0) return
            released = true
            zipFile.also { zipFile = null }
        }
        zipToClose?.let { runCatching { it.close() } }
    }

    /** Returns once [file], or everything below it when it is a directory, is on disk. */
    fun awaitFor(file: File) {
        val key = keyOf(file)
        val prefix = "$key/"
        val under = { path: String -> path == key || path.startsWith(prefix) }
        val mine = lock.withLock {
            if (pending.isEmpty() && inFlight.isEmpty()) {
                throwFailure(under)
                return
            }
            val taken = ArrayList<Pending>()
            pending.remove(key)?.let { taken += it }
            val below = pending.subMap(prefix, prefix + Char.MAX_VALUE)
            taken += below.values
            below.clear()
            taken.forEach { inFlight += keyOf(it.target) }
            taken
        }
        mine.forEach(::finish)
        waitUntil(under)
    }

    fun awaitAll() {
        val mine = lock.withLock {
            val taken = pending.values.toList()
            pending.clear()
            taken.forEach { inFlight += keyOf(it.target) }
            taken
        }
        mine.forEach(::finish)
        waitUntil { true }
    }

    /** Waits until nothing under a path that [matches] is in flight, then reports its failure. */
    private fun waitUntil(matches: (String) -> Boolean) {
        lock.withLock {
            beforeWait()
            while (inFlight.any(matches)) settled.awaitUninterruptibly()
            throwFailure(matches)
        }
    }

    /** Throws, and forgets, the first recorded failure under a path that [matches]. Called with the lock held. */
    private fun throwFailure(matches: (String) -> Boolean) {
        val path = failures.keys.firstOrNull(matches) ?: return
        throw IOException("Writing a resource file failed", failures.remove(path))
    }

    private fun write(entry: Pending) {
        if (entry.target.exists()) return
        val zip = zip()
        val zipEntry = zip.getEntry(entry.archivePath)
            ?: throw IOException("Resource ${entry.archivePath} is missing from ${apkFile.name}")
        entry.target.parentFile?.mkdirs()
        val path = entry.target.toPath()
        val output = try {
            Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        } catch (_: FileAlreadyExistsException) {
            return // a patch created it first
        }
        activeUsers.incrementAndGet()
        try {
            output.use { out ->
                zip.getInputStream(zipEntry).use { input -> copy(input, out) }
                afterCopy()
            }
        } catch (t: Throwable) {
            // A partial file must not be taken for a complete one by a later write
            runCatching { Files.deleteIfExists(path) }
            throw t
        } finally {
            activeUsers.decrementAndGet()
            lock.withLock { settled.signalAll() }
        }
        // Back-dated so a later edit by a patch shows in the snapshot, like for a file decoded up front
        entry.target.setLastModified(System.currentTimeMillis() - BACKDATE_MILLIS)
        written.incrementAndGet()
        onWritten(entry.target)
    }

    /**
     * Copies [input] to [out] through the buffer of this thread. Each worker keeps one buffer, and a caller of
     * [awaitFor] or [awaitAll] gets its own, so no two copies share a buffer. The buffer is taken off the thread
     * while in use, so a copy nested on the same thread (from [afterCopy] or [onWritten]) gets a buffer of its own.
     */
    private fun copy(input: InputStream, out: OutputStream) {
        val buffer = copyBuffer.get() ?: ByteArray(COPY_BUFFER_SIZE)
        copyBuffer.set(null)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
        } finally {
            copyBuffer.set(buffer)
        }
    }

    /**
     * Stops the writers, then waits for the writes in flight and for every stream from [open] to be closed, so a
     * stream that is never closed blocks this call. Production callers close their streams with `use`. The input
     * APK is released once the last of them has ended.
     */
    override fun close() {
        lock.withLock {
            closed = true
            pending.clear()
        }
        workers.forEach { it.interrupt() }
        // A copy cannot be interrupted, so a write that has started runs to its end before the caller may clean up
        lock.withLock {
            while (inFlight.isNotEmpty() || activeUsers.get() > 0) settled.awaitUninterruptibly()
            sealed = true
        }
        releaseIfDone()
    }

    private fun keyOf(file: File) = file.absoluteFile.invariantSeparatorsPath

    private companion object {
        const val BACKDATE_MILLIS = 60_000L
        const val COPY_BUFFER_SIZE = 64 * 1024
    }
}

/**
 * Binary XML files of the input APK, decoded when a patch first asks for one of them under its path, or all of
 * them when the whole working directory is needed. Most of them are never read by a patch, so decoding them up
 * front is most of the decode time.
 *
 * Decoding reads the resource table of [module], so it runs on the thread that asks, never on a background
 * writer, and one decode runs at a time. [module] stays open while a deferred decode can still need its table,
 * and is released once [seal] has run and no decode is pending. Written files are back-dated like raw files and
 * reported to [onWritten], so change detection sees them as unchanged unless a patch edits them.
 */
internal class LazyXmlFiles(
    private val apkFile: File,
    private var module: Closeable?,
    private val onWritten: (File) -> Unit,
) : Closeable {
    private class Pending(val archivePath: String, val target: File, val decode: (InputStream) -> Unit)

    private val lock = ReentrantLock()
    private val pending = TreeMap<String, Pending>()
    private var zipFile: ZipFile? = null
    private var sealed = false
    private var released = false
    private val activeReaders = AtomicInteger(0)

    /** Queues the decode of [archivePath] of the input APK into [target]. [decode] receives the entry's bytes. */
    fun defer(target: File, archivePath: String, decode: (InputStream) -> Unit) {
        lock.withLock {
            check(!sealed) { "The binary XML files of the input APK are already queued" }
            pending[keyOf(target)] = Pending(archivePath, target, decode)
        }
    }

    /**
     * Ends the queueing of decodes, which the decoder does once it has deferred every file. Nothing is queued
     * after this, so [module] is released as soon as no decode is pending.
     */
    fun seal() {
        lock.withLock {
            sealed = true
            releaseIfDone()
        }
    }

    /** Returns once [file], or everything below it when it is a directory, has been decoded. */
    fun awaitFor(file: File) {
        val key = keyOf(file)
        val prefix = "$key/"
        lock.withLock {
            if (pending.isEmpty()) return
            pending.remove(key)?.let(::decode)
            while (true) {
                val next = pending.subMap(prefix, prefix + Char.MAX_VALUE).pollFirstEntry()?.value ?: break
                decode(next)
            }
            releaseIfDone()
        }
    }

    fun awaitAll() {
        lock.withLock {
            while (true) {
                val next = pending.pollFirstEntry()?.value ?: break
                decode(next)
            }
            releaseIfDone()
        }
    }

    /**
     * Lets go of the module and the input APK once no decode is pending. Only a pending decode reads the module,
     * and none can be queued after [seal]. The reference is dropped too, since this object would otherwise keep
     * the parsed table reachable. Called with the lock held.
     */
    private fun releaseIfDone() {
        if (released || !sealed || pending.isNotEmpty() || activeReaders.get() > 0) return
        released = true
        zipFile?.close()
        zipFile = null
        module?.close()
        module = null
    }

    private fun decode(entry: Pending) {
        val zip = zip()
        val zipEntry = zip.getEntry(entry.archivePath)
            ?: throw IOException("Resource ${entry.archivePath} is missing from ${apkFile.name}")
        activeReaders.incrementAndGet()
        try {
            zip.getInputStream(zipEntry).use(entry.decode)
        } finally {
            activeReaders.decrementAndGet()
            releaseIfDone()
        }
        entry.target.setLastModified(System.currentTimeMillis() - BACKDATE_MILLIS)
        onWritten(entry.target)
    }

    private fun zip(): ZipFile = zipFile ?: ZipFile(apkFile).also { zipFile = it }

    override fun close() {
        lock.withLock {
            pending.clear()
            sealed = true
            releaseIfDone()
        }
    }

    private fun keyOf(file: File) = file.absoluteFile.invariantSeparatorsPath

    private companion object {
        const val BACKDATE_MILLIS = 60_000L
    }
}

/**
 * [ApkModuleXmlDecoder] that hands writing raw resource files to [lazyFiles].
 *
 * ARSCLib decodes every resource file in one private loop. This reproduces the order of its
 * public decodeResourceTable and hands over only the raw files, through the same private methods, so the paths it
 * assigns and what it records as decoded stay identical. Binary XML files go to [lazyXml] when it is given, and are
 * decoded at the same point of the loop otherwise. If the private methods are not there, it decodes everything as
 * ARSCLib does.
 */
internal class LazyResourceDecoder(
    module: ApkModule,
    private val lazyFiles: LazyResFiles,
    private val lazyXml: LazyXmlFiles?,
    private val stagesRootEntry: (String) -> Boolean,
    private val logger: Logger,
) : ApkModuleXmlDecoder(module) {

    private class Handles {
        private fun method(name: String, vararg types: Class<*>): Method =
            ApkModuleXmlDecoder::class.java.getDeclaredMethod(name, *types).also { it.isAccessible = true }

        val decodeTableBlock = method("decodeTableBlock", File::class.java, TableBlock::class.java)
        val decodeValues = method("decodeValues", File::class.java, TableBlock::class.java)
        val decodeOverlayable = method("decodeOverlayable", File::class.java, TableBlock::class.java)
        val serializeXml = method("serializeXml", PackageBlock::class.java, ResXmlDocument::class.java, File::class.java)
        val toDecodeResFile = method("toDecodeResFile", File::class.java, ResFile::class.java, PackageBlock::class.java)
        val addDecodedEntry = method("addDecodedEntry", Entry::class.java)
    }

    override fun extractRootFiles(mainDirectory: File) {
        var skipped = 0
        apkModule.inputSources.forEach { source ->
            if (!stagesRootEntry(source.alias)) {
                addDecodedPath(source.alias)
                skipped++
            }
        }
        if (skipped != 0) logger.info("Leaving $skipped native library files in the input APK")
        super.extractRootFiles(mainDirectory)
    }

    override fun decodeResourceTable(mainDirectory: File) {
        val handles = HANDLES
        if (handles == null) {
            super.decodeResourceTable(mainDirectory)
            return
        }

        val table = apkModule.tableBlock
        call { handles.decodeTableBlock.invoke(this, mainDirectory, table) }
        var handedOver = 0
        for (resFile in apkModule.listResFiles()) {
            if (resFile.isBinaryXml) {
                val xmlStart = PhaseProfiler.nanoStart()
                decodeXml(handles, mainDirectory, resFile)
                PhaseProfiler.timeSince("arsc.load.binaryXml", xmlStart)
                continue
            }
            PhaseProfiler.count("arsc.load.rawFile")
            val entry = resFile.pickOne()
            // Assigning the readable path renames the file's alias, so the name in the archive is read first
            val archivePath = resFile.inputSource.alias
            val target = call { handles.toDecodeResFile.invoke(this, mainDirectory, resFile, entry.packageBlock) } as File
            // Decoding creates the directories of the files it writes, and a patch may add a file to one before it is written
            target.parentFile?.mkdirs()
            lazyFiles.defer(target, archivePath)
            if (!keepResPath()) call { handles.addDecodedEntry.invoke(this, entry) }
            addDecodedPath(resFile.inputSource.alias)
            handedOver++
        }
        PhaseProfiler.mark("resource decode: xml loop")
        lazyFiles.start()
        logger.info("Writing $handedOver raw resource files in the background")
        call { handles.decodeValues.invoke(this, mainDirectory, table) }
        PhaseProfiler.mark("resource decode: values")
        call { handles.decodeOverlayable.invoke(this, mainDirectory, table) }
    }

    /**
     * What ARSCLib does for a binary XML file, except that the entry stream is closed. ARSCLib hands
     * the document an inflater stream it never closes, so every XML file leaves native memory behind until
     * the garbage collector gets to it, thousands of files over a decode.
     */
    private fun decodeXml(handles: Handles, mainDirectory: File, resFile: ResFile) {
        val entry = resFile.pickOne()
        val packageBlock = entry.packageBlock
        val archivePath = resFile.inputSource.alias
        val target = call { handles.toDecodeResFile.invoke(this, mainDirectory, resFile, packageBlock) } as File
        val lazy = lazyXml
        if (lazy != null) {
            // The directories exist now, as they do for eagerly decoded files, so a listing sees the same tree
            target.parentFile?.mkdirs()
            lazy.defer(target, archivePath) { input -> serializeXml(handles, input, packageBlock, target) }
        } else {
            lazyFiles.open(archivePath).use { serializeXml(handles, it, packageBlock, target) }
        }
        if (!keepResPath()) call { handles.addDecodedEntry.invoke(this, entry) }
        addDecodedPath(resFile.inputSource.alias)
    }

    private fun serializeXml(handles: Handles, input: InputStream, packageBlock: PackageBlock, target: File) {
        val document = ResXmlDocument()
        document.readBytes(input)
        document.setPackageBlock(packageBlock)
        call { handles.serializeXml.invoke(this, packageBlock, document, target) }
    }

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }

    companion object {
        /** Null when the ARSCLib in use does not have the private methods this relies on. */
        private val HANDLES: Handles? = try {
            Handles()
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: LinkageError) {
            null
        }

        val supported get() = HANDLES != null
    }
}
