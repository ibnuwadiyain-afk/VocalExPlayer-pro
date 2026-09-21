package com.example.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.example.audio.DecodedAudio
import com.example.audio.WavWriter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sqrt

class DeezerSpleeterInferenceEngine(private val context: Context) : SeparationEngine {

    companion object {
        private const val TAG = "DeezerSpleeter"
        const val VOCALS_MODEL_NAME = "spleeter_vocals.onnx"
        const val ACCOMPANIMENT_MODEL_NAME = "spleeter_accompaniment.onnx"

        // Deezer Spleeter 2stems STFT parameters
        const val N_FFT = 4096
        const val HOP_LENGTH = 1024
        const val DIM_F = 1024
        const val DIM_T = 512
        const val SAMPLE_RATE = 44100
    }

    override val engineType: SeparationEngineType = SeparationEngineType.DEEZER_SPLEETER

    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val processor = SpleeterSpectrogramProcessor(
        nFft = N_FFT,
        hopLength = HOP_LENGTH,
        dimF = DIM_F,
        dimT = DIM_T
    )

    override var activeHardwareBackend: String = "Multi-Core CPU"
        private set

    /**
     * Extracts model file from assets to app internal files directory if not already extracted.
     */
    fun getOrExtractModel(modelFileName: String): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val destinationFile = File(modelsDir, modelFileName)
        if (!destinationFile.exists() || destinationFile.length() < 1_000_000L) {
            Log.i(TAG, "Extracting Deezer Spleeter model $modelFileName to ${destinationFile.absolutePath}...")
            context.assets.open("models/$modelFileName").use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Extracted $modelFileName successfully: ${destinationFile.length()} bytes")
        }
        return destinationFile
    }

    private fun createSession(modelFile: File, numThreads: Int): OrtSession {
        return try {
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(numThreads)
                setInterOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                try {
                    addNnapi()
                    activeHardwareBackend = "NNAPI (NPU/GPU Accelerated)"
                    Log.i(TAG, "Hardware Acceleration enabled: NNAPI NPU/GPU")
                } catch (e: Throwable) {
                    activeHardwareBackend = "Multi-Core CPU ($numThreads threads)"
                    Log.i(TAG, "NNAPI not active, fallback to Multi-Core CPU: ${e.message}")
                }
            }
            ortEnvironment.createSession(modelFile.absolutePath, options)
        } catch (e: Throwable) {
            Log.w(TAG, "Accelerated session initialization failed (${e.message}), falling back to safe CPU mode")
            activeHardwareBackend = "Multi-Core CPU ($numThreads threads)"
            val safeOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(numThreads)
                setInterOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }
            ortEnvironment.createSession(modelFile.absolutePath, safeOptions)
        }
    }

    override fun separate(
        decodedAudio: DecodedAudio,
        outputVocalFile: File,
        outputInstrumentalFile: File?,
        customModelPath: String?,
        accelerationMode: AccelerationMode,
        onProgress: (Float) -> Unit
    ): SeparationResult {
        val vocalModelFile = if (!customModelPath.isNullOrBlank() && File(customModelPath).exists()) {
            File(customModelPath)
        } else {
            getOrExtractModel(VOCALS_MODEL_NAME)
        }

        val accModelFile = getOrExtractModel(ACCOMPANIMENT_MODEL_NAME)

        val availableProcessors = Runtime.getRuntime().availableProcessors()
        val numThreads = availableProcessors.coerceIn(4, 8)

        Log.i(TAG, "Initializing Spleeter sessions with $numThreads threads...")
        val vocalSession = createSession(vocalModelFile, numThreads)
        var accSession: OrtSession? = null
        try {
            if (accModelFile.exists()) {
                accSession = createSession(accModelFile, numThreads)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Accompaniment model session failed to load: ${e.message}, will use ratio complement")
        }

        try {
            val vocalInputName = vocalSession.inputNames.first()
            val accInputName = accSession?.inputNames?.first()

            val totalSamples = decodedAudio.totalFrames
            if (totalSamples <= 0L) {
                throw IllegalArgumentException("Audio track has 0 samples")
            }

            onProgress(0.05f)

            // Streaming Chunk Architecture:
            // Spleeter operates on 512 frames per chunk (dimT = 512, hopLength = 1024 => genSize = 524,288 samples ≈ 11.89 seconds).
            // Each chunk requires:
            // chunkInputSamples = (dimT - 1) * hopLength + nFft = 511 * 1024 + 4096 = 527,360 samples.
            // By processing audio in 512-frame chunks with streaming disk writers, memory usage is O(1) regardless of duration.
            val genSize = DIM_T * HOP_LENGTH // 524,288 samples
            val nFft = N_FFT // 4096
            val hopLength = HOP_LENGTH // 1024
            val dimT = DIM_T // 512
            val dimF = DIM_F // 1024
            val numBins = processor.numBins // 2049
            val chunkInputSamples = (dimT - 1) * hopLength + nFft // 527,360 samples

            val totalChunks = ((totalSamples + genSize - 1) / genSize).toInt().coerceAtLeast(1)
            Log.i(TAG, "Streaming Deezer Spleeter separation: $totalSamples samples, $totalChunks chunks of $genSize samples (O(1) memory)")

            // Reusable buffers for chunk processing (never reallocated during loop)
            val audioChunkL = FloatArray(chunkInputSamples)
            val audioChunkR = FloatArray(chunkInputSamples)

            val leftReal = FloatArray(dimT * numBins)
            val leftImag = FloatArray(dimT * numBins)
            val leftMag = FloatArray(dimT * dimF)

            val rightReal = FloatArray(dimT * numBins)
            val rightImag = FloatArray(dimT * numBins)
            val rightMag = FloatArray(dimT * dimF)

            val vocalRealL = FloatArray(dimT * numBins)
            val vocalImagL = FloatArray(dimT * numBins)
            val vocalRealR = FloatArray(dimT * numBins)
            val vocalImagR = FloatArray(dimT * numBins)

            val accRealL = FloatArray(dimT * numBins)
            val accImagL = FloatArray(dimT * numBins)
            val accRealR = FloatArray(dimT * numBins)
            val accImagR = FloatArray(dimT * numBins)

            // FFT scratchpads
            val reScratch = FloatArray(nFft)
            val imScratch = FloatArray(nFft)

            // iSTFT output buffers
            val istftOutWaveL = FloatArray(genSize + nFft + hopLength)
            val istftOutWaveR = FloatArray(genSize + nFft + hopLength)
            val istftWeights = FloatArray(genSize + nFft + hopLength)

            val vocalOutChunkL = FloatArray(genSize)
            val vocalOutChunkR = FloatArray(genSize)
            val accOutChunkL = FloatArray(genSize)
            val accOutChunkR = FloatArray(genSize)

            // Direct buffer for ONNX tensor [2, 1, 512, 1024]
            val splitDimElements = dimT * dimF
            val tensorCapacity = 2 * 1 * splitDimElements
            val byteBuffer = ByteBuffer.allocateDirect(tensorCapacity * 4).order(ByteOrder.nativeOrder())
            val floatBuffer = byteBuffer.asFloatBuffer()
            val inputShape = longArrayOf(2L, 1L, dimT.toLong(), dimF.toLong())

            outputVocalFile.parentFile?.mkdirs()
            outputInstrumentalFile?.parentFile?.mkdirs()

            val vocalWavWriter = WavWriter.createStreamingWriter(outputVocalFile, SAMPLE_RATE, 2)
            val instWavWriter = if (outputInstrumentalFile != null) {
                WavWriter.createStreamingWriter(outputInstrumentalFile, SAMPLE_RATE, 2)
            } else null

            try {
                var chunkIdx = 0
                var samplePos = 0L

                while (samplePos < totalSamples) {
                    val progress = 0.08f + 0.88f * (chunkIdx.toFloat() / totalChunks)
                    onProgress(progress)

                    val validOutputFrames = minOf(genSize.toLong(), totalSamples - samplePos).toInt()

                    // 1. Read input audio chunk from DecodedAudio (streaming from disk or memory)
                    java.util.Arrays.fill(audioChunkL, 0f)
                    java.util.Arrays.fill(audioChunkR, 0f)

                    val samplesToRead = minOf(chunkInputSamples.toLong(), totalSamples - samplePos).toInt()
                    if (samplesToRead > 0) {
                        decodedAudio.readStereoChunk(
                            startFrame = samplePos,
                            count = samplesToRead,
                            destL = audioChunkL,
                            destR = audioChunkR,
                            destOffset = 0
                        )
                    }

                    // 2. Compute 512-frame STFT for Left channel
                    for (t in 0 until dimT) {
                        val startSample = t * hopLength
                        for (i in 0 until nFft) {
                            val sIdx = startSample + i
                            val s = if (sIdx < chunkInputSamples) audioChunkL[sIdx] else 0f
                            // Hann window
                            val w = (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / nFft))).toFloat()
                            reScratch[i] = s * w
                            imScratch[i] = 0f
                        }
                        processor.fft(reScratch, imScratch, inverse = false)

                        val frameOffset = t * numBins
                        val magOffset = t * dimF
                        for (k in 0 until numBins) {
                            val r = reScratch[k]
                            val im = imScratch[k]
                            leftReal[frameOffset + k] = r
                            leftImag[frameOffset + k] = im
                            if (k < dimF) {
                                leftMag[magOffset + k] = sqrt(r * r + im * im)
                            }
                        }
                    }

                    // 3. Compute 512-frame STFT for Right channel
                    for (t in 0 until dimT) {
                        val startSample = t * hopLength
                        for (i in 0 until nFft) {
                            val sIdx = startSample + i
                            val s = if (sIdx < chunkInputSamples) audioChunkR[sIdx] else 0f
                            val w = (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / nFft))).toFloat()
                            reScratch[i] = s * w
                            imScratch[i] = 0f
                        }
                        processor.fft(reScratch, imScratch, inverse = false)

                        val frameOffset = t * numBins
                        val magOffset = t * dimF
                        for (k in 0 until numBins) {
                            val r = reScratch[k]
                            val im = imScratch[k]
                            rightReal[frameOffset + k] = r
                            rightImag[frameOffset + k] = im
                            if (k < dimF) {
                                rightMag[magOffset + k] = sqrt(r * r + im * im)
                            }
                        }
                    }

                    // 4. Fill direct float buffer: Left mag [1, 512, 1024] then Right mag [1, 512, 1024]
                    floatBuffer.clear()
                    floatBuffer.put(leftMag, 0, splitDimElements)
                    floatBuffer.put(rightMag, 0, splitDimElements)
                    floatBuffer.flip()

                    // 5. Run ONNX neural inference
                    val inputTensor = OnnxTensor.createTensor(ortEnvironment, floatBuffer, inputShape)

                    val vocalResult = vocalSession.run(mapOf(vocalInputName to inputTensor))
                    val vocalOutTensor = vocalResult.get(0) as OnnxTensor
                    val vocalOutBuffer = vocalOutTensor.floatBuffer

                    val accResult = if (accSession != null && accInputName != null) {
                        accSession.run(mapOf(accInputName to inputTensor))
                    } else null
                    val accOutTensor = accResult?.get(0) as? OnnxTensor
                    val accOutBuffer = accOutTensor?.floatBuffer

                    // 6. Apply Deezer Spleeter soft ratio mask
                    for (channel in 0 until 2) {
                        val channelTensorOffset = channel * splitDimElements
                        for (t in 0 until dimT) {
                            val frameBinOffset = t * numBins
                            val magFrameOffset = channelTensorOffset + t * dimF

                            for (k in 0 until dimF) {
                                val vVal = vocalOutBuffer.get(magFrameOffset + k)
                                val aVal = accOutBuffer?.get(magFrameOffset + k) ?: (1f - vVal).coerceAtLeast(0f)

                                val v2 = vVal * vVal
                                val a2 = aVal * aVal
                                val sum = v2 + a2 + 1e-10f
                                val maskV = (v2 + 5e-11f) / sum
                                val maskA = (a2 + 5e-11f) / sum

                                if (channel == 0) {
                                    val r = leftReal[frameBinOffset + k]
                                    val im = leftImag[frameBinOffset + k]
                                    vocalRealL[frameBinOffset + k] = maskV * r
                                    vocalImagL[frameBinOffset + k] = maskV * im
                                    accRealL[frameBinOffset + k] = maskA * r
                                    accImagL[frameBinOffset + k] = maskA * im
                                } else {
                                    val r = rightReal[frameBinOffset + k]
                                    val im = rightImag[frameBinOffset + k]
                                    vocalRealR[frameBinOffset + k] = maskV * r
                                    vocalImagR[frameBinOffset + k] = maskV * im
                                    accRealR[frameBinOffset + k] = maskA * r
                                    accImagR[frameBinOffset + k] = maskA * im
                                }
                            }

                            // Frequencies above 11kHz (bins 1024..2048): Vocals = 0, Instrumental = original mix
                            for (k in dimF until numBins) {
                                if (channel == 0) {
                                    vocalRealL[frameBinOffset + k] = 0f
                                    vocalImagL[frameBinOffset + k] = 0f
                                    accRealL[frameBinOffset + k] = leftReal[frameBinOffset + k]
                                    accImagL[frameBinOffset + k] = leftImag[frameBinOffset + k]
                                } else {
                                    vocalRealR[frameBinOffset + k] = 0f
                                    vocalImagR[frameBinOffset + k] = 0f
                                    accRealR[frameBinOffset + k] = rightReal[frameBinOffset + k]
                                    accImagR[frameBinOffset + k] = rightImag[frameBinOffset + k]
                                }
                            }
                        }
                    }

                    inputTensor.close()
                    vocalResult.close()
                    accResult?.close()

                    // 7. iSTFT reconstruction for Vocals
                    computeIstftInto(
                        real = vocalRealL,
                        imag = vocalImagL,
                        dimT = dimT,
                        numBins = numBins,
                        hopLength = hopLength,
                        nFft = nFft,
                        targetLength = genSize,
                        outWave = vocalOutChunkL,
                        istftOutWave = istftOutWaveL,
                        istftWeights = istftWeights,
                        reScratch = reScratch,
                        imScratch = imScratch
                    )
                    computeIstftInto(
                        real = vocalRealR,
                        imag = vocalImagR,
                        dimT = dimT,
                        numBins = numBins,
                        hopLength = hopLength,
                        nFft = nFft,
                        targetLength = genSize,
                        outWave = vocalOutChunkR,
                        istftOutWave = istftOutWaveR,
                        istftWeights = istftWeights,
                        reScratch = reScratch,
                        imScratch = imScratch
                    )

                    // Write isolated vocal chunk directly to disk
                    vocalWavWriter.writeStereoChunk(vocalOutChunkL, vocalOutChunkR, 0, validOutputFrames)

                    // 8. iSTFT reconstruction for Instrumental if requested
                    if (instWavWriter != null) {
                        computeIstftInto(
                            real = accRealL,
                            imag = accImagL,
                            dimT = dimT,
                            numBins = numBins,
                            hopLength = hopLength,
                            nFft = nFft,
                            targetLength = genSize,
                            outWave = accOutChunkL,
                            istftOutWave = istftOutWaveL,
                            istftWeights = istftWeights,
                            reScratch = reScratch,
                            imScratch = imScratch
                        )
                        computeIstftInto(
                            real = accRealR,
                            imag = accImagR,
                            dimT = dimT,
                            numBins = numBins,
                            hopLength = hopLength,
                            nFft = nFft,
                            targetLength = genSize,
                            outWave = accOutChunkR,
                            istftOutWave = istftOutWaveR,
                            istftWeights = istftWeights,
                            reScratch = reScratch,
                            imScratch = imScratch
                        )
                        instWavWriter.writeStereoChunk(accOutChunkL, accOutChunkR, 0, validOutputFrames)
                    }

                    samplePos += genSize
                    chunkIdx++
                }
            } finally {
                try { vocalWavWriter.close() } catch (_: Exception) {}
                try { instWavWriter?.close() } catch (_: Exception) {}
            }

            onProgress(1.0f)
            Log.i(TAG, "Deezer Spleeter 2-stem separation completed successfully! Vocals: ${outputVocalFile.length()} bytes, Instrumental: ${outputInstrumentalFile?.length() ?: 0} bytes")
            return SeparationResult(
                vocalWavFile = outputVocalFile,
                instrumentalWavFile = outputInstrumentalFile
            )
        } finally {
            try { vocalSession.close() } catch (_: Exception) {}
            try { accSession?.close() } catch (_: Exception) {}
        }
    }

    private fun computeIstftInto(
        real: FloatArray,
        imag: FloatArray,
        dimT: Int,
        numBins: Int,
        hopLength: Int,
        nFft: Int,
        targetLength: Int,
        outWave: FloatArray,
        istftOutWave: FloatArray,
        istftWeights: FloatArray,
        reScratch: FloatArray,
        imScratch: FloatArray
    ) {
        val totalSize = targetLength + nFft + hopLength
        java.util.Arrays.fill(istftOutWave, 0, totalSize, 0f)
        java.util.Arrays.fill(istftWeights, 0, totalSize, 0f)

        for (frame in 0 until dimT) {
            val frameOffset = frame * numBins

            reScratch[0] = real[frameOffset]
            imScratch[0] = 0f

            for (k in 1 until numBins - 1) {
                val r = real[frameOffset + k]
                val im = imag[frameOffset + k]
                reScratch[k] = r
                imScratch[k] = im
                reScratch[nFft - k] = r
                imScratch[nFft - k] = -im
            }

            reScratch[numBins - 1] = real[frameOffset + numBins - 1]
            imScratch[numBins - 1] = 0f

            processor.fft(reScratch, imScratch, inverse = true)

            val startSample = frame * hopLength
            for (i in 0 until nFft) {
                val idx = startSample + i
                if (idx < totalSize) {
                    val w = (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / nFft))).toFloat()
                    istftOutWave[idx] += reScratch[i] * w
                    istftWeights[idx] += w * w
                }
            }
        }

        for (i in 0 until targetLength) {
            val weight = istftWeights[i]
            outWave[i] = if (weight > 1e-4f) {
                istftOutWave[i] / weight
            } else {
                istftOutWave[i]
            }
        }
    }
}
