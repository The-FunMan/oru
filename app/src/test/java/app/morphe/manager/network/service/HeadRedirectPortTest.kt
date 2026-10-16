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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HeadRedirectPortTest {
    private lateinit var server: HttpServer
    private lateinit var base: String

    @BeforeTest
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/rel") { exchange ->
                exchange.responseHeaders.add("Location", "/target-rel")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            start()
        }
        base = "http://127.0.0.1:${server.address.port}"
    }

    @AfterTest
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun headRedirectKeepsPortOnRelativeLocation() = runBlocking {
        val service = HttpService(Json, HttpClient())

        assertEquals("$base/target-rel", service.headRedirect("$base/rel"))
    }
}
