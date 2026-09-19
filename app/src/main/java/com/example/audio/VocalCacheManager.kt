package com.example.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages disk-backed incremental vocal caches and live streaming separation sessions.
 * Persists separated vocal stems under filesDir/vocals (and cacheDir/vocals), keyed by video ID or URI hash.
 */
object VocalCacheManager {

    private const val TAG = "VocalCacheManager"

    // Active live streaming sessions keyed by videoId
    private val activeLiveSessions = ConcurrentHashMap<String, LiveVocalSession>()

    /**
     * Represents an in-flight live streaming session where separated chunks are being appended sequentially
     * to disk while ExoPlayer's NeuralAudioProcessor can read and play them concurrently.
     */
    class LiveVocalSession(
        val videoId: String,
        val vocalWavFile: File,
        val sampleRate: Int = 44100
    ) : AutoCloseable {

        private val writer: StreamingWavWriter = WavWriter.createStreamingWriter(vocalWavFile, sampleRate, 2)

        @Volatile
        var isStreaming: Boolean = true
            private set

        @Volatile
        var streamedSamplesCount: Long = 0L
            private set

        private val reader = CachedPcmReader(vocalWavFile)

        @Synchronized
        fun appendChunk(left: FloatArray, right: FloatArray, offset: Int, count: Int) {
            if (count <= 0) return
            writer.writeStereoChunk(left, right, offset, count)
            writer.flush()
            streamedSamplesCount = writer.framesWritten
        }

        @Synchronized
        fun appendSilence(count: Int) {
            if (count <= 0) return
            writer.writeSilence(count)
            writer.flush()
            streamedSamplesCount = writer.framesWritten
        }

        /**
         * Reads stereo frames directly from the streaming cache file.
         * Only allows reading up to streamedSamplesCount.
         */
        fun readVocalChunk(
            startFrame: Long,
            count: Int,
            destL: FloatArray,
            destR: FloatArray,
            destOffset: Int = 0
        ): Int {
            val available = (streamedSamplesCount - startFrame).coerceAtLeast(0L).toInt()
            val validFrames = minOf(count, available)
            if (validFrames <= 0) return 0
            return reader.readStereoFrames(startFrame, validFrames, destL, destR, destOffset)
        }

        fun hasFramesAvailable(endFrame: Long): Boolean {
            return streamedSamplesCount >= endFrame
        }

        @Synchronized
        fun finalizeSession(): File {
            isStreaming = false
            writer.close()
            reader.close()
            return vocalWavFile
        }

        override fun close() {
            finalizeSession()
        }
    }

    /**
     * Generates a stable cache key hash for a given media URI string.
     */
    fun getCacheKeyForUri(uriString: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(uriString.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            uriString.hashCode().toString().replace("-", "n")
        }
    }

    /**
     * Returns the primary vocals directory on internal storage.
     */
    fun getVocalsDir(context: Context): File {
        val dir = File(context.filesDir, "vocals")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Resolves the persistent vocal cache file for a video.
     */
    fun getVocalFile(context: Context, videoId: String): File {
        val dir = getVocalsDir(context)
        return File(dir, "vocal_$videoId.wav")
    }

    /**
     * Resolves cache file from either videoId or raw URI.
     */
    fun getVocalFileForUri(context: Context, uri: Uri): File {
        val key = getCacheKeyForUri(uri.toString())
        val dir = getVocalsDir(context)
        return File(dir, "vocal_$key.wav")
    }

    /**
     * Checks if a valid, finalized vocal cache file exists on disk.
     */
    fun hasValidCachedVocals(context: Context, videoId: String): Boolean {
        val file = getVocalFile(context, videoId)
        return file.exists() && file.length() > 44L
    }

    /**
     * Starts a live streaming vocal session for progressive inference and instant playback.
     */
    fun startLiveSession(context: Context, videoId: String, sampleRate: Int = 44100): LiveVocalSession {
        // Close any prior session for this video
        activeLiveSessions[videoId]?.close()

        val vocalFile = getVocalFile(context, videoId)
        val session = LiveVocalSession(videoId, vocalFile, sampleRate)
        activeLiveSessions[videoId] = session
        Log.i(TAG, "Started live streaming vocal session for video $videoId (file: ${vocalFile.name})")
        return session
    }

    /**
     * Retrieves an active in-flight live streaming session, if one exists.
     */
    fun getActiveLiveSession(videoId: String): LiveVocalSession? {
        val session = activeLiveSessions[videoId]
        if (session != null && session.isStreaming) {
            return session
        }
        return null
    }

    /**
     * Completes and finalizes an active live session.
     */
    fun finalizeLiveSession(videoId: String): File? {
        val session = activeLiveSessions.remove(videoId)
        return session?.finalizeSession()
    }

    /**
     * Calculates total disk storage consumed by all cached vocal files in bytes.
     */
    fun calculateCacheSizeBytes(context: Context): Long {
        val dir = getVocalsDir(context)
        var total = 0L
        dir.listFiles()?.forEach { file ->
            if (file.isFile) total += file.length()
        }
        val cacheDir = File(context.cacheDir, "vocals")
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile) total += file.length()
            }
        }
        return total
    }

    /**
     * Clears all cached vocal audio files from disk and terminates all active sessions.
     */
    fun clearCache(context: Context): Long {
        activeLiveSessions.forEach { (_, session) ->
            try { session.close() } catch (_: Exception) {}
        }
        activeLiveSessions.clear()

        var bytesDeleted = 0L
        val dir = getVocalsDir(context)
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                bytesDeleted += file.length()
                file.delete()
            }
        }
        val cacheDir = File(context.cacheDir, "vocals")
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    bytesDeleted += file.length()
                    file.delete()
                }
            }
        }
        Log.i(TAG, "Cleared vocal cache: freed $bytesDeleted bytes")
        return bytesDeleted
    }
}
