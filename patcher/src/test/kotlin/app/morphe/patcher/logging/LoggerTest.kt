/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patcher
 */

package app.morphe.patcher.logging

import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class LoggerTest {
    private val messages = mutableListOf<Pair<LogLevel, String>>()

    private val logger = object : Logger() {
        override fun log(level: LogLevel, message: String) {
            messages += level to message
        }
    }

    @Test
    fun `error without a throwable logs the message alone`() {
        logger.error("failed")

        assertEquals(listOf(LogLevel.ERROR to "failed"), messages)
    }

    @Test
    fun `error includes the throwable message and stack trace`() {
        logger.error("failed", IllegalStateException("boom"))

        val (level, message) = messages.single()
        assertEquals(LogLevel.ERROR, level)
        assertTrue(message.startsWith("failed\n"))
        assertTrue(message.contains("IllegalStateException: boom"))
    }

    @Test
    fun `severe records keep their throwable`() {
        logger.handler.publish(LogRecord(Level.SEVERE, "failed").apply { thrown = IllegalStateException("boom") })

        val (_, message) = messages.single()
        assertTrue(message.contains("IllegalStateException: boom"))
    }
}
