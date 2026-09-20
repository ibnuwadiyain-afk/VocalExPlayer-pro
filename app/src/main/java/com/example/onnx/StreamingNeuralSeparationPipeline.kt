package com.example.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.example.audio.DecodedAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-Device Deep Learning Separation Engine with Non-Blocking Streaming Pipe.
 *
 * Implements:
 * 1. 44.1 kHz, 2 channels, 16-bit PCM processing.
 * 2. STFT Windowing: Frame size 4096 / 6144, Hop size 1024, Hann window.
 * 3. Spectrogram magnitude/phase extraction with ONNX session (NNAPI or multi-threaded CPU).
 * 4. Soft mask / ratio mask application and iSTFT reconstruction.
 * 5. Non-Blocking Streaming Pipe:
 *    - Emits each finished 16-bit PCM chunk immediately into:
 *      a) A persistent raw PCM cache file (.pcm) indexed by byte offset (offset = (positionMs * sampleRate * 4) / 1000).
 *      b) A buffered Kotlin Channel<ShortArray>(capacity = 64) consumed concurrently by the video muxer and playback feeder.
 *    - Sequential chunk processing on Dispatchers.Default with CPU micro-yield throttling to prevent UI/audio thread starvation.
 *    - Finalizes a canonical .wav file alongside .pcm for standard players.
 */
class StreamingNeuralSeparationPipeline(
    private val context: Context,
    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()
) {
    companion object {
        private const val TAG = "StreamingNeuralSep"
        const val SAMPLE_RATE = 44100
        const val CHANNELS = 2
        const val BYTES_PER_SAMPLE = 2
        const val BYTES_PER_FRAME = CHANNELS * BYTES_PER_SAMPLE // 4 bytes

        fun getByteOffsetForMs(positionMs: Long): Long {
            return (positionMs * SAMPLE_RATE * BYTES_PER_FRAME) / 1000L
        }

        fun getMsForByteOffset(offsetBytes: Long): Long {
            return (offsetBytes * 1000L) / (SAMPLE_RATE * BYTES_PER_FRAME)
        }
    }

    private val inferenceEngine = UvrMdxInferenceEngine(context)

    /**
     * Executes the non-blocking streaming separation pipeline.
     *
     * @param decodedAudio Audio decoded to 44.1kHz stereo
     * @param outputWavFile Destination WAV file
     * @param persistentPcmCacheFile Persistent raw PCM file indexed by offset
     * @param streamChannel Optional Kotlin Channel(64) receiving chunks for concurrent muxing/playback
     * @param customModelPath Optional custom ONNX model path
     * @param onChunkEmitted Callback fired when each chunk is written to disk/channel
     * @param onProgress Callback with progress from 0.0 to 1.0
     */
    suspend fun executeStreamingPipeline(
        decodedAudio: DecodedAudio,
        outputWavFile: File,
        persistentPcmCacheFile: File,
        streamChannel: Channel<ShortArray>? = null,
        customModelPath: String? = null,
        accelerationMode: AccelerationMode = AccelerationMode.TURBO,
        onChunkEmitted: (chunkIndex: Int, totalChunks: Int, framesProcessed: Long) -> Unit = { _, _, _ -> },
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.Default) {
        var pcmRaf: RandomAccessFile? = null
        var wavFos: FileOutputStream? = null
        var session: OrtSession? = null

        try {
            val totalSamples = decodedAudio.totalFrames
            if (totalSamples <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("Audio has 0 samples"))
            }

            // Ensure parent directories exist
            outputWavFile.parentFile?.mkdirs()
            persistentPcmCacheFile.parentFile?.mkdirs()

            // Initialize persistent raw PCM file
            if (persistentPcmCacheFile.exists()) persistentPcmCacheFile.delete()
            pcmRaf = RandomAccessFile(persistentPcmCacheFile, "rw")

            // Initialize WAV file with placeholder header
            wavFos = FileOutputStream(outputWavFile)
            wavFos.write(ByteArray(44))

            // Initialize ONNX Session with NNAPI or multi-threaded CPU
            val modelFile = inferenceEngine.getOrExtractModelFile(customModelPath)
            val availableCores = Runtime.getRuntime().availableProcessors()
            val numThreads = availableCores.coerceIn(4, 8)

            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(numThreads)
                setInterOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                try {
                    addNnapi()
                    Log.i(TAG, "Hardware acceleration: NNAPI Active")
                } catch (e: Throwable) {
                    Log.i(TAG, "NNAPI unavailable, using Multi-Core CPU ($numThreads threads)")
                }
            }

            session = try {
                ortEnvironment.createSession(modelFile.absolutePath, sessionOptions)
            } catch (e: Throwable) {
                Log.w(TAG, "Falling back to standard CPU session", e)
                val safeOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(numThreads)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                }
                ortEnvironment.createSession(modelFile.absolutePath, safeOptions)
            }

            val inputNames = session.inputNames
            val inputName = inputNames.iterator().next()
            val outputNames = session.outputNames
            val outputName = outputNames.iterator().next()

            val processor = MdxSpectrogramProcessor(
                nFft = UvrMdxInferenceEngine.N_FFT,
                dimF = UvrMdxInferenceEngine.DIM_F,
                dimT = UvrMdxInferenceEngine.DIM_T,
                hopLength = UvrMdxInferenceEngine.HOP_LENGTH
            )

            val genSize = processor.genSize // 254976 samples (~5.78s)
            val chunkSize = processor.chunkSize // 261120
            val trim = processor.trim // 3072

            val remainder = (totalSamples % genSize).toInt()
            val pad = if (remainder == 0) 0L else (genSize - remainder).toLong()
            val totalChunks = ((totalSamples + pad) / genSize).toInt()

            Log.i(TAG, "Starting non-blocking streaming separation: $totalSamples frames, $totalChunks chunks (~5.8s each)")

            val scratchL = processor.createScratchpad()
            val scratchR = processor.createScratchpad()
            val chunkL = FloatArray(chunkSize + processor.nFft)
            val chunkR = FloatArray(chunkSize + processor.nFft)

            val specReL = Array(UvrMdxInferenceEngine.DIM_F) { FloatArray(UvrMdxInferenceEngine.DIM_T) }
            val specImL = Array(UvrMdxInferenceEngine.DIM_F) { FloatArray(UvrMdxInferenceEngine.DIM_T) }
            val specReR = Array(UvrMdxInferenceEngine.DIM_F) { FloatArray(UvrMdxInferenceEngine.DIM_T) }
            val specImR = Array(UvrMdxInferenceEngine.DIM_F) { FloatArray(UvrMdxInferenceEngine.DIM_T) }

            val tensorCapacity = 1 * 4 * UvrMdxInferenceEngine.DIM_F * UvrMdxInferenceEngine.DIM_T
            val inputFloatBuffer = ByteBuffer.allocateDirect(tensorCapacity * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()

            val tensorShape = longArrayOf(1, 4, UvrMdxInferenceEngine.DIM_F.toLong(), UvrMdxInferenceEngine.DIM_T.toLong())
            val istftWaveL = FloatArray(chunkSize + processor.nFft)
            val istftWaveR = FloatArray(chunkSize + processor.nFft)
            val istftWeightL = FloatArray(chunkSize + processor.nFft)
            val istftWeightR = FloatArray(chunkSize + processor.nFft)

            val bytePcmBuffer = ByteArray(genSize * 4)
            val pcmByteBuffer = ByteBuffer.wrap(bytePcmBuffer).order(ByteOrder.LITTLE_ENDIAN)

            var totalPcmBytesWritten = 0L
            var framesEmitted = 0L

            for (c in 0 until totalChunks) {
                val startSample = c.toLong() * genSize - trim
                var readCount = 0

                if (startSample < 0) {
                    val preZero = (-startSample).toInt()
                    val toRead = chunkSize - preZero
                    readCount = decodedAudio.readStereoChunk(0, toRead, chunkL, chunkR, preZero)
                    java.util.Arrays.fill(chunkL, 0, preZero, 0f)
                    java.util.Arrays.fill(chunkR, 0, preZero, 0f)
                    if (toRead - readCount > 0) {
                        java.util.Arrays.fill(chunkL, preZero + readCount, chunkSize, 0f)
                        java.util.Arrays.fill(chunkR, preZero + readCount, chunkSize, 0f)
                    }
                } else if (startSample + chunkSize > totalSamples) {
                    val toRead = (totalSamples - startSample).toInt().coerceAtLeast(0)
                    readCount = decodedAudio.readStereoChunk(startSample, toRead, chunkL, chunkR, 0)
                    if (chunkSize - toRead > 0) {
                        java.util.Arrays.fill(chunkL, toRead, chunkSize, 0f)
                        java.util.Arrays.fill(chunkR, toRead, chunkSize, 0f)
                    }
                } else {
                    readCount = decodedAudio.readStereoChunk(startSample, chunkSize, chunkL, chunkR, 0)
                }

                // 1. Forward STFT
                processor.computeChannelStft(chunkL, specReL, specImL, scratchL)
                processor.computeChannelStft(chunkR, specReR, specImR, scratchR)

                // 2. Prepare 4-channel tensor [Batch=1, Channels=4, DimF=2048, DimT=256]
                processor.packToFloatBuffer(specReL, specImL, specReR, specImR, inputFloatBuffer)

                // 3. Neural ONNX Inference
                val inputTensor = OnnxTensor.createTensor(ortEnvironment, inputFloatBuffer, tensorShape)
                val outputs = session.run(mapOf(inputName to inputTensor))
                inputTensor.close()

                val outTensor = outputs[0] as OnnxTensor
                val outFloatBuffer = outTensor.floatBuffer

                for (f in 0 until UvrMdxInferenceEngine.DIM_F) {
                    outFloatBuffer.get(specReL[f], 0, UvrMdxInferenceEngine.DIM_T)
                }
                for (f in 0 until UvrMdxInferenceEngine.DIM_F) {
                    outFloatBuffer.get(specImL[f], 0, UvrMdxInferenceEngine.DIM_T)
                }
                for (f in 0 until UvrMdxInferenceEngine.DIM_F) {
                    outFloatBuffer.get(specReR[f], 0, UvrMdxInferenceEngine.DIM_T)
                }
                for (f in 0 until UvrMdxInferenceEngine.DIM_F) {
                    outFloatBuffer.get(specImR[f], 0, UvrMdxInferenceEngine.DIM_T)
                }
                outputs.close()

                // 4. Inverse STFT (iSTFT) overlap-add
                java.util.Arrays.fill(istftWaveL, 0f)
                java.util.Arrays.fill(istftWaveR, 0f)
                java.util.Arrays.fill(istftWeightL, 0f)
                java.util.Arrays.fill(istftWeightR, 0f)

                processor.computeChannelIstft(specReL, specImL, istftWaveL, istftWeightL, UvrMdxInferenceEngine.COMPENSATION, scratchL)
                processor.computeChannelIstft(specReR, specImR, istftWaveR, istftWeightR, UvrMdxInferenceEngine.COMPENSATION, scratchR)

                // 5. Trim margins and extract generated audio segment
                val actualOutFrames = if (c == totalChunks - 1) {
                    (totalSamples - c.toLong() * genSize).toInt().coerceIn(0, genSize)
                } else {
                    genSize
                }

                if (actualOutFrames > 0) {
                    val chunkShorts = ShortArray(actualOutFrames * 2)
                    pcmByteBuffer.clear()

                    for (i in 0 until actualOutFrames) {
                        val srcIdx = trim + i
                        val wL = istftWeightL[srcIdx]
                        val wR = istftWeightR[srcIdx]
                        val sampleL = if (wL > 1e-4f) (istftWaveL[srcIdx] / wL).coerceIn(-1.0f, 1.0f) else istftWaveL[srcIdx]
                        val sampleR = if (wR > 1e-4f) (istftWaveR[srcIdx] / wR).coerceIn(-1.0f, 1.0f) else istftWaveR[srcIdx]

                        val sL = (sampleL * 32767.0f).toInt().toShort()
                        val sR = (sampleR * 32767.0f).toInt().toShort()

                        chunkShorts[i * 2] = sL
                        chunkShorts[i * 2 + 1] = sR

                        pcmByteBuffer.putShort(sL)
                        pcmByteBuffer.putShort(sR)
                    }

                    val bytesToWrite = actualOutFrames * 4

                    // Emit to persistent raw PCM cache file
                    pcmRaf.write(bytePcmBuffer, 0, bytesToWrite)

                    // Emit to canonical WAV file
                    wavFos.write(bytePcmBuffer, 0, bytesToWrite)

                    totalPcmBytesWritten += bytesToWrite
                    framesEmitted += actualOutFrames

                    // Non-blocking emission to Channel for concurrent Video Muxer or Live Player
                    streamChannel?.trySend(chunkShorts)

                    onChunkEmitted(c + 1, totalChunks, framesEmitted)
                }

                val prog = (c + 1).toFloat() / totalChunks.toFloat()
                onProgress(prog)

                // CPU Micro-yield throttling: prevents UI thread starving and leaves CPU headroom for audio playback
                delay(5L)
            }

            // Close channel indicating end of streaming
            streamChannel?.close()

            wavFos.flush()
            wavFos.close()
            wavFos = null

            // Finalize WAV Header
            val rafWav = RandomAccessFile(outputWavFile, "rw")
            try {
                rafWav.seek(0)
                writeCanonicalWavHeader(rafWav, SAMPLE_RATE, CHANNELS, 16, totalPcmBytesWritten)
            } finally {
                rafWav.close()
            }

            Log.i(TAG, "Streaming neural separation completed: ${outputWavFile.absolutePath} (${totalPcmBytesWritten} bytes)")
            Result.success(outputWavFile)
        } catch (e: Exception) {
            Log.e(TAG, "Streaming neural separation failed", e)
            streamChannel?.close(e)
            Result.failure(e)
        } finally {
            try { pcmRaf?.close() } catch (_: Exception) {}
            try { wavFos?.close() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
        }
    }

    private fun writeCanonicalWavHeader(
        raf: RandomAccessFile,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        dataBytes: Long
    ) {
        val totalDataLen = dataBytes + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(totalDataLen.toInt())
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1.toShort())
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataBytes.toInt())

        raf.write(header)
    }
}
