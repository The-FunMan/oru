/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.network.service

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpServiceRedirectTest {
    private lateinit var server: HttpServer
    private lateinit var base: String
    private val originalFactory = HttpService.newNoRedirectClient
    private val clientBuilds = AtomicInteger()

    @BeforeTest
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            for (status in listOf(301, 302)) {
                createContext("/$status") { exchange ->
                    exchange.responseHeaders.add("Location", "/target-$status")
                    exchange.sendResponseHeaders(status, -1)
                    exchange.close()
                }
            }
            createContext("/abs") { exchange ->
                exchange.responseHeaders.add("Location", "$base/target-abs")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            createContext("/plain") { exchange ->
                val body = "ok".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        base = "http://127.0.0.1:${server.address.port}"

        HttpService.newNoRedirectClient = {
            clientBuilds.incrementAndGet()
            originalFactory()
        }
    }

    @AfterTest
    fun stopServer() {
        HttpService.newNoRedirectClient = originalFactory
        server.stop(0)
    }

    private fun service() = HttpService(Json, HttpClient())

    @Test
    fun resolveRedirectReturnsLocationWithoutFollowing() = runBlocking {
        val service = service()

        assertEquals("/target-301", service.resolveRedirect("$base/301"))
        assertEquals("/target-302", service.resolveRedirect("$base/302"))
        assertNull(service.resolveRedirect("$base/plain"))
    }

    @Test
    fun headRedirectReturnsAbsoluteLocation() = runBlocking {
        val service = service()

        assertEquals("$base/target-abs", service.headRedirect("$base/abs"))
        assertNull(service.headRedirect("$base/plain"))
    }

    @Test
    fun noRedirectClientIsBuiltOncePerService() = runBlocking {
        val service = service()

        repeat(6) {
            service.resolveRedirect("$base/301")
            service.headRedirect("$base/302")
        }

        assertEquals(1, clientBuilds.get())
    }
}
