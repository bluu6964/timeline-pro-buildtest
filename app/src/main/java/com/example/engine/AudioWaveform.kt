package com.example.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Decodes an audio file once and keeps the loudest sample of every [BUCKET_MS] ms
 * (0..1, normalised) so the timeline can draw a waveform. Runs on a background thread.
 * Build 234: decoding + peak extraction live in C++ (AudioAnalysisNative); there is
 * no Kotlin decode path anymore. Null means the file could not be opened / decoded.
 */
object AudioWaveform {

    const val BUCKET_MS = 50

    suspend fun compute(context: Context, uri: String, durationMs: Long): FloatArray? =
        withContext(Dispatchers.Default) {
            ensureActive()
            val peaks = try {
                context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { pfd ->
                    AudioAnalysisNative.nativeComputeWaveformFd(
                        pfd.fd, pfd.statSize, durationMs, BUCKET_MS
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            ensureActive()
            peaks
        }
}
