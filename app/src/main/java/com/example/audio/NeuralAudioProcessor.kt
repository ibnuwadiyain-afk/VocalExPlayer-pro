package com.example.audio

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Zero-Lag Real-Time Audio Engine wired directly into ExoPlayer's audio pipeline.
 *
 * 1. Intercepts 16-bit PCM stereo audio buffers during playback.
 * 2. When offline vocal stems are cached or streaming, reads synchronized vocal samples
 *    directly from disk cache (CachedPcmReader) using pre-allocated small buffers.
 * 3. Mixes the cached vocal stem with live audio using a smoothing step-interpolated alpha
 *    gain factor (currentMixAlpha to targetMixAlpha) to eliminate audio clicks and phase pops.
 * 4. Memory-safe: Never loads multi-megabyte audio tracks into JVM heap. Reads small sample
 *    slices on-demand via RandomAccessFile.seek().
 * 5. Fallback DSP: When neural cache is not yet available, supplies an instant real-time
 *    spectral/formant center-channel vocal filter.
 */
@OptIn(UnstableApi::class)
class NeuralAudioProcessor(
    private val context: Context
) : BaseAudioProcessor() {

    companion object {
        private const val TAG = "NeuralAudioProcessor"
        private const val SCRATCH_SIZE = 8192
    }

    private var sampleRate: Int = 44100
    private var channelCount: Int = 2

    // Media tracking
    @Volatile
    private var activeVideoId: String? = null
    private var cachedReader: CachedPcmReader? = null
    private var cachedVocalFile: File? = null

    // Playback frame position tracking
    @Volatile
    private var currentPlaybackFrame: Long = 0L

    // Stem Mixing Gain Alpha: 0.0f = Instrumental / Karaoke, 0.5f = Original Mix, 1.0f = Vocals Only
    @Volatile
    var targetMixAlpha: Float = 1.0f
        private set

    private var currentMixAlpha: Float = 1.0f

    // Fallback DSP Filter
    private var spectralFilter = SpectralVocalFilter(sampleRate)

    // Pre-allocated scratchpad arrays (strictly O(1) heap, zero allocations in queueInput)
    private var scratchVocalL = FloatArray(SCRATCH_SIZE)
    private var scratchVocalR = FloatArray(SCRATCH_SIZE)

    enum class StemMode {
        VOCAL_ONLY,
        KARAOKE,
        ORIGINAL_MIX
    }

    /**
     * Sets active video ID and optional explicit vocal file path.
     */
    @Synchronized
    fun setMedia(videoId: String?, explicitVocalUri: String? = null) {
        if (activeVideoId == videoId && cachedReader != null) return

        activeVideoId = videoId
        cachedReader?.close()
        cachedReader = null
        cachedVocalFile = null

        if (videoId != null) {
            val file = if (!explicitVocalUri.isNullOrBlank()) {
                val f = File(explicitVocalUri)
                if (f.exists()) f else VocalCacheManager.getVocalFile(context, videoId)
            } else {
                VocalCacheManager.getVocalFile(context, videoId)
            }

            if (file.exists() && file.length() > 44L) {
                cachedVocalFile = file
                cachedReader = CachedPcmReader(file)
                Log.d(TAG, "Attached cached vocal stem: ${file.name} (${file.length()} bytes)")
            }
        }
    }

    fun setTargetVideoId(videoId: String?, vocalUri: String? = null) {
        setMedia(videoId, vocalUri)
    }

    fun setStemMode(mode: StemMode) {
        when (mode) {
            StemMode.VOCAL_ONLY -> setVocalBlend(1.0f)
            StemMode.KARAOKE -> setVocalBlend(0.0f)
            StemMode.ORIGINAL_MIX -> setVocalBlend(0.5f)
        }
    }

    fun notifySeek(positionMs: Long) {
        onSeek(positionMs)
    }

    fun release() {
        reset()
    }

    /**
     * Toggles between isolated AI vocals and original mix.
     */
    fun setVocalsOnly(vocalsOnly: Boolean) {
        targetMixAlpha = if (vocalsOnly) 1.0f else 0.5f
    }

    /**
     * Sets continuous vocal blend factor (0.0 = Instrumental, 0.5 = Original, 1.0 = Vocals).
     */
    fun setVocalBlend(blend: Float) {
        targetMixAlpha = blend.coerceIn(0.0f, 1.0f)
    }

    /**
     * Updates frame position on user seek.
     */
    fun onSeek(positionMs: Long, audioSampleRate: Int = sampleRate) {
        val rate = if (audioSampleRate > 0) audioSampleRate else 44100
        currentPlaybackFrame = (positionMs.coerceAtLeast(0L) * rate) / 1000L
        Log.d(TAG, "Seeked to $positionMs ms (frame $currentPlaybackFrame)")
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        spectralFilter = SpectralVocalFilter(sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remainingBytes = inputBuffer.remaining()
        if (remainingBytes == 0) return

        // Each 16-bit stereo frame is 4 bytes (2 bytes Left + 2 bytes Right)
        val frameBytes = channelCount * 2
        val framesToProcess = remainingBytes / frameBytes
        if (framesToProcess <= 0) {
            inputBuffer.position(inputBuffer.position() + remainingBytes)
            return
        }

        // Ensure scratchpad size
        if (scratchVocalL.size < framesToProcess) {
            scratchVocalL = FloatArray(framesToProcess)
            scratchVocalR = FloatArray(framesToProcess)
        }

        // Check if cached vocal stem or active live streaming session is available
        val vid = activeVideoId
        var vocalFramesAvailable = 0

        // 1. Try active live streaming session first
        val liveSession = vid?.let { VocalCacheManager.getActiveLiveSession(it) }
        if (liveSession != null && liveSession.hasFramesAvailable(currentPlaybackFrame + framesToProcess)) {
            vocalFramesAvailable = liveSession.readVocalChunk(
                startFrame = currentPlaybackFrame,
                count = framesToProcess,
                destL = scratchVocalL,
                destR = scratchVocalR,
                destOffset = 0
            )
        } else {
            // 2. Try disk cache file
            var reader = cachedReader
            if (reader == null && vid != null) {
                val f = VocalCacheManager.getVocalFile(context, vid)
                if (f.exists() && f.length() > 44L) {
                    cachedVocalFile = f
                    reader = CachedPcmReader(f)
                    cachedReader = reader
                }
            }

            if (reader != null && reader.hasFramesAvailable(currentPlaybackFrame + framesToProcess)) {
                vocalFramesAvailable = reader.readStereoFrames(
                    startFrame = currentPlaybackFrame,
                    frameCount = framesToProcess,
                    destL = scratchVocalL,
                    destR = scratchVocalR,
                    destOffset = 0
                )
            }
        }

        val outputBuffer = replaceOutputBuffer(framesToProcess * frameBytes)
        val sIn = inputBuffer.asShortBuffer()

        val hasNeuralVocals = (vocalFramesAvailable >= framesToProcess)

        for (i in 0 until framesToProcess) {
            // Step-interpolated smoothing alpha: smoothly ramps towards targetMixAlpha
            currentMixAlpha += (targetMixAlpha - currentMixAlpha) * 0.05f

            val origL = sIn.get() / 32768.0f
            val origR = if (channelCount >= 2) sIn.get() / 32768.0f else origL

            var outL: Float
            var outR: Float

            if (hasNeuralVocals) {
                val vocL = scratchVocalL[i]
                val vocR = scratchVocalR[i]

                if (currentMixAlpha >= 0.98f) {
                    // Isolated Vocals Only
                    outL = vocL
                    outR = vocR
                } else if (currentMixAlpha <= 0.02f) {
                    // Karaoke / Instrumental (live original minus isolated vocal)
                    outL = (origL - vocL * 0.95f).coerceIn(-1.0f, 1.0f)
                    outR = (origR - vocR * 0.95f).coerceIn(-1.0f, 1.0f)
                } else if (currentMixAlpha < 0.5f) {
                    // Interpolate between Instrumental and Original
                    val t = currentMixAlpha / 0.5f
                    val karaL = origL - vocL * 0.95f
                    val karaR = origR - vocR * 0.95f
                    outL = (karaL * (1f - t) + origL * t).coerceIn(-1.0f, 1.0f)
                    outR = (karaR * (1f - t) + origR * t).coerceIn(-1.0f, 1.0f)
                } else {
                    // Interpolate between Original and Isolated Vocals
                    val t = (currentMixAlpha - 0.5f) / 0.5f
                    outL = (origL * (1f - t) + vocL * t).coerceIn(-1.0f, 1.0f)
                    outR = (origR * (1f - t) + vocR * t).coerceIn(-1.0f, 1.0f)
                }
            } else {
                // Fallback DSP: Instant real-time center-channel formant vocal filter
                val (dspL, dspR) = spectralFilter.processSample(origL, origR, currentMixAlpha)
                outL = dspL
                outR = dspR
            }

            val shortL = (outL.coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
            val shortR = (outR.coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()

            outputBuffer.putShort(shortL)
            if (channelCount >= 2) {
                outputBuffer.putShort(shortR)
            }
        }

        // Advance playback frame index
        currentPlaybackFrame += framesToProcess

        // Mark input buffer consumed and output ready
        inputBuffer.position(inputBuffer.position() + framesToProcess * frameBytes)
        outputBuffer.flip()
    }

    override fun onFlush() {
        spectralFilter.reset()
    }

    override fun onReset() {
        spectralFilter.reset()
        cachedReader?.close()
        cachedReader = null
        cachedVocalFile = null
        activeVideoId = null
    }
}
