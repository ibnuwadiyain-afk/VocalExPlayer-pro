package com.example.dubbing

import kotlin.math.PI
import kotlin.math.cos

/**
 * High-quality audio time-stretching, compression, and smoothing utility.
 * Uses Synchronized Overlap-Add (SOLA) to adaptively speed up synthesized speech
 * cues so they fit into their allotted video time windows without pitch distortion,
 * and applies envelope smoothing to eliminate voice overlap and audio clicks.
 */
object AudioTimeFitter {

    private const val DEFAULT_WINDOW_MS = 20
    private const val DEFAULT_HOP_MS = 10
    private const val MAX_LAG_MS = 8

    /**
     * Compresses the duration of audio samples using SOLA (Pitch-Preserved Time-Scale Modification)
     * so that the spoken cue fits into [targetFrames].
     *
     * @param samplesLeft 44.1kHz PCM float samples (Left channel)
     * @param samplesRight 44.1kHz PCM float samples (Right channel)
     * @param targetFrames Desired output length in frames
     * @param sampleRate Sampling rate in Hz (default 44100)
     * @param maxSpeedUp Maximum speedup factor (e.g. 1.45f = up to 45% faster)
     * @return Pair of Left and Right audio sample arrays compressed to fit targetFrames
     */
    fun compressAudio(
        samplesLeft: FloatArray,
        samplesRight: FloatArray,
        targetFrames: Int,
        sampleRate: Int = 44100,
        maxSpeedUp: Float = 1.45f
    ): Pair<FloatArray, FloatArray> {
        val sourceFrames = samplesLeft.size
        if (sourceFrames == 0 || targetFrames >= sourceFrames || targetFrames <= 0) {
            return Pair(samplesLeft, samplesRight)
        }

        // Target compression ratio alpha (0.0 < alpha < 1.0)
        val rawAlpha = targetFrames.toDouble() / sourceFrames.toDouble()
        val minAlpha = 1.0 / maxSpeedUp.toDouble()
        val alpha = rawAlpha.coerceIn(minAlpha, 1.0)

        // If compression needed is negligible (< 2%), keep original
        if (alpha >= 0.98) {
            return Pair(samplesLeft, samplesRight)
        }

        val outputFrames = (sourceFrames * alpha).toInt().coerceAtLeast(1)

        val windowSize = ((DEFAULT_WINDOW_MS * sampleRate) / 1000).coerceAtLeast(64)
        val synthHop = ((DEFAULT_HOP_MS * sampleRate) / 1000).coerceAtLeast(32)
        val maxLag = ((MAX_LAG_MS * sampleRate) / 1000).coerceAtLeast(16)

        // For very short clips, use high-speed linear resampling
        if (sourceFrames < windowSize * 2 || outputFrames < windowSize) {
            return resampleLinear(samplesLeft, samplesRight, outputFrames)
        }

        // Hanning analysis/synthesis window
        val win = FloatArray(windowSize) { i ->
            0.5f * (1.0f - cos(2.0 * PI * i / (windowSize - 1)).toFloat())
        }

        val outL = FloatArray(outputFrames)
        val outR = FloatArray(outputFrames)
        val weight = FloatArray(outputFrames)

        // Copy initial window
        val initLen = minOf(windowSize, sourceFrames, outputFrames)
        for (i in 0 until initLen) {
            val w = win[i]
            outL[i] = samplesLeft[i] * w
            outR[i] = samplesRight[i] * w
            weight[i] = w
        }

        var posSynth = synthHop
        while (posSynth + windowSize <= outputFrames) {
            val nominalAnalPos = (posSynth / alpha).toInt()
            if (nominalAnalPos + windowSize >= sourceFrames) break

            // Search for best correlation lag around nominalAnalPos to align waveform pitch peaks
            var bestLag = 0
            var maxCorr = -Float.MAX_VALUE

            val searchStart = -maxLag
            val searchEnd = maxLag

            for (lag in searchStart..searchEnd) {
                val candPos = nominalAnalPos + lag
                if (candPos < 0 || candPos + synthHop > sourceFrames) continue

                var corr = 0f
                var step = 0
                while (step < synthHop) {
                    val targetSample = outL[posSynth + step]
                    val candSample = samplesLeft[candPos + step]
                    corr += targetSample * candSample
                    step += 2 // Step by 2 for fast mobile CPU correlation
                }

                if (corr > maxCorr) {
                    maxCorr = corr
                    bestLag = lag
                }
            }

            val finalAnalPos = (nominalAnalPos + bestLag).coerceIn(0, sourceFrames - windowSize)

            // Overlap-add window
            for (j in 0 until windowSize) {
                val outIdx = posSynth + j
                val inIdx = finalAnalPos + j
                if (outIdx < outputFrames && inIdx < sourceFrames) {
                    val w = win[j]
                    outL[outIdx] += samplesLeft[inIdx] * w
                    outR[outIdx] += samplesRight[inIdx] * w
                    weight[outIdx] += w
                }
            }

            posSynth += synthHop
        }

        // Normalize accumulated window weights
        for (i in 0 until outputFrames) {
            val w = weight[i]
            if (w > 1e-4f) {
                outL[i] = (outL[i] / w).coerceIn(-1.0f, 1.0f)
                outR[i] = (outR[i] / w).coerceIn(-1.0f, 1.0f)
            }
        }

        // Apply gentle boundary fade
        applyGentleFade(outL, outR, fadeInFrames = synthHop, fadeOutFrames = synthHop)

        return Pair(outL, outR)
    }

    /**
     * Linear resampling fallback for very short audio bursts.
     */
    private fun resampleLinear(
        srcLeft: FloatArray,
        srcRight: FloatArray,
        targetFrames: Int
    ): Pair<FloatArray, FloatArray> {
        val outL = FloatArray(targetFrames)
        val outR = FloatArray(targetFrames)
        val srcFrames = srcLeft.size
        val ratio = srcFrames.toDouble() / targetFrames.toDouble()

        for (i in 0 until targetFrames) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val idx1 = (idx0 + 1).coerceIn(0, srcFrames - 1)
            val frac = (srcPos - idx0).toFloat()

            outL[i] = (1f - frac) * srcLeft[idx0] + frac * srcLeft[idx1]
            outR[i] = (1f - frac) * srcRight[idx0] + frac * srcRight[idx1]
        }
        return Pair(outL, outR)
    }

    /**
     * Applies smooth cosine fade-in and fade-out envelopes to eliminate start/stop click transients.
     */
    fun applyGentleFade(
        samplesLeft: FloatArray,
        samplesRight: FloatArray,
        fadeInFrames: Int = 441,
        fadeOutFrames: Int = 441
    ) {
        val total = samplesLeft.size
        if (total == 0) return

        val inLen = minOf(fadeInFrames, total / 2)
        if (inLen > 1) {
            for (i in 0 until inLen) {
                val gain = 0.5f * (1.0f - cos(PI * i / inLen).toFloat())
                samplesLeft[i] *= gain
                samplesRight[i] *= gain
            }
        }

        val outLen = minOf(fadeOutFrames, total / 2)
        if (outLen > 1) {
            for (i in 0 until outLen) {
                val idx = total - 1 - i
                val gain = 0.5f * (1.0f - cos(PI * i / outLen).toFloat())
                samplesLeft[idx] *= gain
                samplesRight[idx] *= gain
            }
        }
    }

    /**
     * Trims audio to [targetFrames] and applies a smooth cosine fade-out at the end
     * so that cutting an utterance is completely click-free and natural.
     */
    fun trimWithFade(
        samplesLeft: FloatArray,
        samplesRight: FloatArray,
        targetFrames: Int,
        fadeOutFrames: Int = 882
    ): Pair<FloatArray, FloatArray> {
        val count = minOf(samplesLeft.size, targetFrames)
        if (count <= 0) return Pair(FloatArray(0), FloatArray(0))

        val trimmedL = samplesLeft.copyOf(count)
        val trimmedR = samplesRight.copyOf(count)

        val fadeLen = minOf(fadeOutFrames, count / 2)
        if (fadeLen > 1) {
            for (i in 0 until fadeLen) {
                val idx = count - 1 - i
                val gain = 0.5f * (1.0f - cos(PI * i / fadeLen).toFloat())
                trimmedL[idx] *= gain
                trimmedR[idx] *= gain
            }
        }

        return Pair(trimmedL, trimmedR)
    }
}
