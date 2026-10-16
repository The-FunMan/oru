/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.network.service

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.request.url
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpServiceRangeQueueTest {
    private val payload = ByteArray(10 * 1024 * 1024 + 123) { (it * 31 + it / 7).toByte() }
    private val rangeStart = 4L * 1024 * 1024

    private lateinit var server: HttpServer
    private lateinit var fileUrl: String
    private lateinit var tempDir: File

    /** Range GETs received, in total and per first byte offset. */
    private val rangeGets = AtomicInteger()
    private val attemptsByStart = ConcurrentHashMap<Long, AtomicInteger>()

    /** Offsets whose next requests are answered with 503, counted down per request. */
    private val failuresLeft = ConcurrentHashMap<Long, AtomicInteger>()

    /** Offsets that always answer 404. */
    private val notFoundStarts = ConcurrentHashMap.newKeySet<Long>()

    @BeforeTest
    fun startServer() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "range-queue-${System.nanoTime()}")
        tempDir.mkdirs()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/file") { exchange -> serve(exchange) }
            start()
        }
        fileUrl = "http://127.0.0.1:${server.address.port}/file"
    }

    @AfterTest
    fun stopServer() {
        server.stop(0)
        tempDir.deleteRecursively()
    }

    private fun serve(exchange: HttpExchange) {
        try {
            respond(exchange)
        } finally {
            exchange.close()
        }
    }

    private fun respond(ex: HttpExchange) {
        if (ex.requestMethod == "HEAD") {
            ex.responseHeaders.add("Accept-Ranges", "bytes")
            ex.responseHeaders.add("Content-Length", payload.size.toString())
            ex.sendResponseHeaders(200, -1)
            return
        }

        rangeGets.incrementAndGet()
        val (start, end) = parseRange(ex.requestHeaders.getFirst("Range"))
        attemptsByStart.getOrPut(start) { AtomicInteger() }.incrementAndGet()

        val failing = failuresLeft[start]?.getAndUpdate { if (it > 0) it - 1 else 0 }?.let { it > 0 } == true
        when {
            start in notFoundStarts -> ex.sendResponseHeaders(404, -1)
            failing -> ex.sendResponseHeaders(503, -1)
            else -> {
                val length = end - start + 1
                ex.responseHeaders.add("Content-Range", "bytes $start-$end/${payload.size}")
                ex.sendResponseHeaders(206, length)
                ex.responseBody.write(payload, start.toInt(), length.toInt())
            }
        }
    }

    private fun parseRange(header: String?): Pair<Long, Long> {
        val match = requireNotNull(Regex("bytes=(\\d+)-(\\d+)").matchEntire(header.orEmpty())) {
            "Unexpected Range header: $header"
        }
        return match.groupValues[1].toLong() to match.groupValues[2].toLong()
    }

    private fun download(target: File, threads: Int = 5) = runBlocking {
        HttpService(Json, HttpClient()).downloadToFile(
            saveLocation = target,
            threads = threads,
            builder = { url(fileUrl) }
        )
    }

    @Test
    fun parallelDownloadWritesIdenticalBytesWithBoundedRequests() {
        val target = File(tempDir, "out.bin")

        download(target)

        assertContentEquals(payload, target.readBytes())
        // Ranges are at least 1 MB, so the request count stays near the size, not the thread count
        assertTrue(rangeGets.get() <= payload.size / (1024 * 1024) + 1, "range requests: ${rangeGets.get()}")
    }

    @Test
    fun transientRangeFailureIsRetriedAndBytesStayIdentical() {
        val target = File(tempDir, "retried.bin")
        failuresLeft[rangeStart] = AtomicInteger(1)

        download(target)

        assertContentEquals(payload, target.readBytes())
        assertEquals(2, attemptsByStart[rangeStart]?.get(), "the failed range is requested again once")
    }

    @Test
    fun permanentRangeFailureFailsTheDownloadWithoutRetry() {
        val target = File(tempDir, "failed.bin")
        notFoundStarts += rangeStart

        val error = assertFailsWith<HttpService.HttpException> { download(target) }

        assertEquals(404, error.status.value)
        assertEquals(1, attemptsByStart[rangeStart]?.get(), "a 404 is a settled answer and is not retried")
    }
}
