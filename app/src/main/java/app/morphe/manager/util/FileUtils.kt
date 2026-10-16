/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Purges the contents of this directory without blocking the calling thread.
 *
 * If the directory exists:
 * 1. Atomically renames the directory to a temporary trash name in the same parent
 *    directory (an O(1) filesystem metadata operation on the calling thread).
 * 2. Immediately recreates the clean, empty directory at the original path.
 * 3. Launches a background coroutine on [dispatcher] to recursively delete the renamed
 *    trash directory (and any stale trash directories left behind by previous crashes or kills).
 *
 * If the rename fails, the directory is deleted on the calling thread instead.
 * If the directory does not exist, simply ensures it exists via [File.mkdirs].
 */
fun File.purgeDirectoryAsync(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): Job {
    val trashDir = if (exists()) {
        val parent = parentFile
        val candidate = if (parent != null) {
            File(parent, "${name}_trash_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}")
        } else {
            File("${name}_trash_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}")
        }
        if (renameTo(candidate)) {
            candidate
        } else {
            // Without the rename the previous session's files would outlive the purge, so they
            // are wiped in place instead
            deleteRecursively()
            null
        }
    } else {
        null
    }

    // Ensure the original directory exists and is immediately usable by downstream components
    mkdirs()

    return scope.launch(dispatcher) {
        if (trashDir != null) {
            runCatching { trashDir.deleteRecursively() }
        }
        // Also sweep any stale trash directories matching the pattern in the parent directory
        val parent = parentFile
        if (parent != null) {
            val prefix = "${name}_trash_"
            runCatching {
                parent.listFiles { f -> f.isDirectory && f.name.startsWith(prefix) }
                    ?.forEach { it.deleteRecursively() }
            }
        }
    }
}

/**
 * Moves [source] over [target] in one step where the filesystem allows it, so a reader sees either
 * the old file or the new one. Returns false when the move fails and leaves [source] in place.
 *
 * Unlike a bare [File.renameTo], this also replaces a read-only [target]: installed bundles are
 * read-only, and Windows refuses to replace such a file, so it is made writable first.
 */
fun moveReplacing(source: File, target: File): Boolean {
    if (target.exists()) runCatching { target.setWritable(true, true) }
    val from = source.toPath()
    val to = target.toPath()
    return runCatching {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }.isSuccess
}

/**
 * Passes through at most [limit] bytes of [input] and fails past that. An archive entry declares
 * its own size, which a hostile archive can set to a small value while the data expands far beyond it.
 */
class BoundedInputStream(private val input: InputStream, private val limit: Long) : InputStream() {
    private var consumed = 0L

    override fun read(): Int {
        val byte = input.read()
        if (byte != -1) countConsumed(1)
        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = input.read(b, off, len)
        if (count > 0) countConsumed(count)
        return count
    }

    override fun close() = input.close()

    private fun countConsumed(count: Int) {
        consumed += count
        if (consumed > limit) throw IOException("Stream is larger than $limit bytes")
    }
}
