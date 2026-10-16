/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.util

import java.io.File
import java.lang.management.ManagementFactory
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Opt-in phase timing, heap, counters and a per-patch trace for measuring a patch run.
 *
 * Enabled with the system property `oru.profile=true`. When disabled every call is a no-op and
 * no profiler state is allocated per call. Heap figures are live heap after a forced GC at each [mark];
 * the peak is sampled every 20 ms and includes garbage not yet collected. The forced GC runs after the
 * wall time of a mark is taken, so GC time does not count toward the phase that ends at that mark.
 * On Android [mark] does not force a GC ([forcedGcOnMark]), so its live heap is the used heap at that moment
 * and the run JSON carries `"forcedGc":false`.
 *
 * [report] writes one JSON line per run, prefixed `oru.profile.json`, to the log. When the system
 * property `oru.profile.out` names a file, the same line is appended to that file. On Android the
 * line also goes to logcat under [LOGCAT_TAG], split into chunks of at most [LOGCAT_CHUNK] characters
 * because logcat truncates long messages. Each chunk is prefixed `index/total `; concatenate them in order.
 * GC and thread figures come from `java.lang.management` on desktop. On Android they come from
 * `Debug.getRuntimeStat` and `Thread.getAllStackTraces`, read by reflection so this module keeps no
 * Android types.
 */
object PhaseProfiler {
    val enabled = System.getProperty("oru.profile") == "true"

    private val logger = Logger.getLogger("oru.profile")
    private val runtime = Runtime.getRuntime()
    private val start = System.nanoTime()
    private var lastMark = start
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val timers = ConcurrentHashMap<String, AtomicLong>()
    private val scans = ConcurrentHashMap<String, ScanStats>()
    private val phases = ArrayList<Phase>()
    private val patches = ArrayList<PatchTrace>()
    private val logLines = AtomicLong()

    @Volatile
    private var peakUsedBytes = 0L

    private val forcesGc = forcedGcOnMark(Android.present)

    private var forcedGcCount = 0L
    private var forcedGcMillis = 0L

    internal class ScanStats {
        val calls = AtomicLong()
        val visits = AtomicLong()
        val nanos = AtomicLong()
    }

    /** A running full-scan measurement, or null when profiling is off. */
    class Scan internal constructor(private val stats: ScanStats, private val startNanos: Long) {
        private var visitCount = 0L

        /** Counts one class visited by the scan. */
        fun visit() {
            visitCount++
        }

        /** Ends the measurement. Call from a finally block so early returns are counted. */
        fun end() {
            stats.calls.incrementAndGet()
            stats.visits.addAndGet(visitCount)
            stats.nanos.addAndGet(System.nanoTime() - startNanos)
        }
    }

    /**
     * One mark. The GC figures are cumulative since start. [blockingGcCount] and [blockingGcMillis] count
     * the collections that blocked the app threads, as reported by ART; both are 0 on desktop.
     */
    internal class Phase(
        val name: String,
        val wallNanos: Long,
        val phaseNanos: Long,
        val liveBytes: Long,
        val gcCount: Long,
        val gcMillis: Long,
        val blockingGcCount: Long,
        val blockingGcMillis: Long,
        val threads: Int,
    )

    internal class PatchTrace(val index: Int, val name: String, val wallNanos: Long, val classesMutable: Long, val classesAdded: Long)

    /** One fingerprint key with the totals of its full-class scans. */
    internal class ScanRecord(val key: String, val calls: Long, val visits: Long, val nanos: Long)

    init {
        if (enabled) {
            Logger.getLogger("").addHandler(object : Handler() {
                override fun publish(record: LogRecord) {
                    logLines.incrementAndGet()
                }

                override fun flush() = Unit

                override fun close() = Unit
            })
            Thread({
                while (true) {
                    peakUsedBytes = maxOf(peakUsedBytes, usedBytes())
                    Thread.sleep(20)
                }
            }, "oru-profile-heap").apply { isDaemon = true }.start()
        }
    }

    /**
     * Logs the end of a phase: wall time since start, time spent in the phase, live heap, GC and threads.
     */
    fun mark(phase: String) {
        if (!enabled) return

        val wall = System.nanoTime()
        val (gcCountBefore, gcMillisBefore) = Management.gcTotals()
        if (forcesGc) System.gc()
        val (gcCountAfter, gcMillisAfter) = Management.gcTotals()
        if (forcesGc) {
            forcedGcCount += gcCountAfter - gcCountBefore
            forcedGcMillis += gcMillisAfter - gcMillisBefore
        }
        val live = usedBytes()
        val phaseNanos = wall - lastMark
        lastMark = System.nanoTime()

        // Collections that the run itself caused: the total minus the forced ones.
        val gcCount = gcCountAfter - forcedGcCount
        val gcMillis = gcMillisAfter - forcedGcMillis
        val (blockingCount, blockingMillis) = Management.blockingGcTotals()
        val threads = Management.threadCount()
        synchronized(phases) {
            phases += Phase(phase, wall - start, phaseNanos, live, gcCount, gcMillis, blockingCount, blockingMillis, threads)
        }

        logger.log(
            Level.INFO,
            String.format(
                Locale.ROOT,
                "oru.profile phase=%s wall=%.2fs phase=%.2fs live=%dMB gc=%d gcMs=%d blockingGc=%d blockingGcMs=%d threads=%d",
                phase,
                (wall - start) / 1e9,
                phaseNanos / 1e9,
                live shr 20,
                gcCount,
                gcMillis,
                blockingCount,
                blockingMillis,
                threads,
            ),
        )
    }

    /** Increments the named counter. */
    fun count(name: String) {
        if (!enabled) return
        counters.getOrPut(name) { AtomicLong() }.incrementAndGet()
    }

    /** Current value of the named counter, or 0. Used to attribute counter deltas to a patch. */
    fun counter(name: String): Long = counters[name]?.get() ?: 0L

    /** Start time for [timeSince], or 0 when profiling is off. */
    fun nanoStart(): Long = if (enabled) System.nanoTime() else 0L

    /** Counts one occurrence of [name] and adds the time since [startNanos] to its timer. */
    fun timeSince(name: String, startNanos: Long) {
        if (!enabled) return
        counters.getOrPut(name) { AtomicLong() }.incrementAndGet()
        timers.getOrPut("$name.ns") { AtomicLong() }.addAndGet(System.nanoTime() - startNanos)
    }

    /**
     * Starts a full-class scan by the fingerprint [name] (or its defining class) of the given [kind].
     * Returns null when profiling is off.
     */
    fun beginScan(kind: String, name: String?): Scan? {
        if (!enabled) return null
        val key = "$kind ${name ?: "<unnamed>"}"
        return Scan(scans.getOrPut(key) { ScanStats() }, System.nanoTime())
    }

    /** Time and class counters at the start of one patch execution. */
    class PatchStart internal constructor(val nanos: Long, val classesMutable: Long, val classesAdded: Long)

    /** Starts the trace of one patch execution. Returns null when profiling is off. */
    fun beginPatch(): PatchStart? {
        if (!enabled) return null
        return PatchStart(System.nanoTime(), counter("classes.madeMutable"), counter("classes.added"))
    }

    /** Ends the trace of the patch [name] started by [beginPatch]. The index is the execution order. */
    fun endPatch(name: String, start: PatchStart?) {
        if (start == null) return
        val wallNanos = System.nanoTime() - start.nanos
        val classesMutable = counter("classes.madeMutable") - start.classesMutable
        val classesAdded = counter("classes.added") - start.classesAdded
        synchronized(patches) {
            patches += PatchTrace(patches.size, name, wallNanos, classesMutable, classesAdded)
        }
    }

    /** Logs every counter, the log line total, the peak heap, and the JSON line of the run. */
    fun report() {
        if (!enabled) return

        counters.toSortedMap().forEach { (name, value) ->
            logger.info("oru.profile counter $name=${value.get()}")
        }
        logger.info("oru.profile peakHeap=${peakUsedBytes shr 20}MB")
        logger.info("oru.profile logLines=${logLines.get()}")

        val json = toJson()
        logger.info("oru.profile.json $json")
        Android.logChunked(LOGCAT_TAG, json)
        System.getProperty("oru.profile.out")?.let { path ->
            try {
                File(path).appendText(json + "\n")
            } catch (e: java.io.IOException) {
                logger.log(Level.WARNING, "oru.profile could not append to $path", e)
            }
        }
    }

    private fun toJson(): String = encodeRun(
        peakHeapBytes = peakUsedBytes,
        forcedGc = forcesGc,
        phases = synchronized(phases) { phases.toList() },
        patches = synchronized(patches) { patches.toList() },
        scans = scans.entries
            .sortedByDescending { it.value.nanos.get() }
            .take(FINGERPRINT_TOP)
            .map { (key, stats) -> ScanRecord(key, stats.calls.get(), stats.visits.get(), stats.nanos.get()) },
        counters = counters.mapValues { it.value.get() },
        timers = timers.mapValues { it.value.get() },
    )

    /** The JSON line of one run. Maps are written in key order so that lines of two runs compare directly. */
    internal fun encodeRun(
        peakHeapBytes: Long,
        forcedGc: Boolean,
        phases: List<Phase>,
        patches: List<PatchTrace>,
        scans: List<ScanRecord>,
        counters: Map<String, Long>,
        timers: Map<String, Long>,
    ): String = buildString {
        append("{\"peakHeapMb\":").append(peakHeapBytes shr 20)
        append(",\"forcedGc\":").append(forcedGc)
        append(",\"phases\":[")
        phases.joinTo(this, ",") { phase ->
            "{\"name\":${quote(phase.name)},\"wallNs\":${phase.wallNanos},\"phaseNs\":${phase.phaseNanos}," +
                    "\"liveMb\":${phase.liveBytes shr 20},\"gcCount\":${phase.gcCount}," +
                    "\"gcMs\":${phase.gcMillis},\"blockingGcCount\":${phase.blockingGcCount}," +
                    "\"blockingGcMs\":${phase.blockingGcMillis},\"threads\":${phase.threads}}"
        }
        append("],\"patches\":[")
        patches.joinTo(this, ",") { patch ->
            "{\"index\":${patch.index},\"name\":${quote(patch.name)},\"wallNs\":${patch.wallNanos}," +
                    "\"classesMutable\":${patch.classesMutable},\"classesAdded\":${patch.classesAdded}}"
        }
        append("],\"fingerprintScans\":[")
        scans.joinTo(this, ",") { scan ->
            "{\"key\":${quote(scan.key)},\"calls\":${scan.calls},\"visits\":${scan.visits}," +
                    "\"ns\":${scan.nanos}}"
        }
        append("],\"counters\":{")
        counters.toSortedMap().entries.joinTo(this, ",") { (name, value) -> "${quote(name)}:$value" }
        append("},\"timers\":{")
        timers.toSortedMap().entries.joinTo(this, ",") { (name, value) -> "${quote(name)}:$value" }
        append("}}")
    }

    private fun quote(text: String): String = buildString {
        append('"')
        for (char in text) {
            when {
                char == '"' || char == '\\' -> append('\\').append(char)
                char < ' ' -> append(String.format(Locale.ROOT, "\\u%04x", char.code))
                else -> append(char)
            }
        }
        append('"')
    }

    private fun usedBytes() = runtime.totalMemory() - runtime.freeMemory()

    private const val FINGERPRINT_TOP = 20

    /** Logcat tag of the per-run JSON line on Android. */
    const val LOGCAT_TAG = "OruProfile"
    private const val LOGCAT_CHUNK = 3000

    /**
     * Whether [mark] forces a collection. Not on Android: a forced collection there is slow and blocks the app,
     * and it would distort the run it measures.
     */
    internal fun forcedGcOnMark(android: Boolean): Boolean = !android

    /** Kept apart so that the profiler itself never references the desktop-only management API. */
    private object Management {
        fun gcTotals(): Pair<Long, Long> {
            if (Android.present) {
                return Android.runtimeStat("art.gc.gc-count") to Android.runtimeStat("art.gc.gc-time")
            }
            var count = 0L
            var millis = 0L
            for (collector in ManagementFactory.getGarbageCollectorMXBeans()) {
                count += maxOf(0L, collector.collectionCount)
                millis += maxOf(0L, collector.collectionTime)
            }
            return count to millis
        }

        /**
         * Collections that blocked the app threads, from ART. The time unit is assumed to be milliseconds,
         * as for `art.gc.gc-time`; this is not verified against an ART source or a device run.
         */
        fun blockingGcTotals(): Pair<Long, Long> {
            if (!Android.present) return 0L to 0L
            return Android.runtimeStat("art.gc.blocking-gc-count") to Android.runtimeStat("art.gc.blocking-gc-time")
        }

        fun threadCount(): Int =
            if (Android.present) Thread.getAllStackTraces().size
            else ManagementFactory.getThreadMXBean().threadCount
    }

    /**
     * Android framework calls, looked up by name so that desktop runs never load an Android class.
     * Every lookup is null off Android, and the callers then use the JVM figures.
     */
    private object Android {
        private val runtimeStatMethod: Method? = lookup("android.os.Debug", "getRuntimeStat", String::class.java)
        private val logInfoMethod: Method? = lookup("android.util.Log", "i", String::class.java, String::class.java)

        val present: Boolean get() = runtimeStatMethod != null

        /** The ART runtime stat [key] as a number, or 0 when it is missing or not numeric. */
        fun runtimeStat(key: String): Long {
            val method = runtimeStatMethod ?: return 0L
            return runCatching { (method.invoke(null, key) as? String)?.toLongOrNull() }.getOrNull() ?: 0L
        }

        /** Writes [text] to logcat under [tag] in chunks, each prefixed with its position. */
        fun logChunked(tag: String, text: String) {
            val method = logInfoMethod ?: return
            val chunks = text.chunked(LOGCAT_CHUNK)
            chunks.forEachIndexed { index, chunk ->
                runCatching { method.invoke(null, tag, "${index + 1}/${chunks.size} $chunk") }
            }
        }

        private fun lookup(className: String, name: String, vararg parameters: Class<*>): Method? = try {
            Class.forName(className).getMethod(name, *parameters)
        } catch (e: ReflectiveOperationException) {
            null
        }
    }
}
