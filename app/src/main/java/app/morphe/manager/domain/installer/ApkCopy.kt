/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.domain.installer

import java.io.InputStream
import java.io.OutputStream

// The default copy buffer is 8 KB, which turns a 200 MB APK into about 27k read and write pairs
internal const val APK_COPY_BUFFER_SIZE = 1 shl 20

internal fun InputStream.copyApkTo(out: OutputStream): Long = copyTo(out, APK_COPY_BUFFER_SIZE)
