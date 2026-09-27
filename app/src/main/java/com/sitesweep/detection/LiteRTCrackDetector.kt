package com.sitesweep.detection

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Hardware-accelerated on-device inference engine using LiteRT / TFLite.
 * Strictly adheres to AGENTS.md requirements:
 * 1. Loads crack_model.tflite (V2 fine-tuned with hard negatives) from assets.
 * 2. Input: 160x160x3, uint8 (0-255), no normalization.
 * 3. Output: single uint8 value, dequantize with scale=0.00390625, zero_point=0
 *    to get a 0.0-1.0 crack probability (prob = raw_value * 0.00390625).
 *    Threshold calibrated via SeverityClassifier.
 * 4. Tries NNAPI delegate first, falls back to GPU delegate, then CPU multi-threading.
 * 5. Logs which delegate won at init.
 * 6. Strictly on-device: zero network dependencies or cloud vision calls.
 */
class LiteRTCrackDetector(
    private val context: Context,
    private val modelAssetPath: String = "crack_model.tflite"
) : CrackDetector {

    companion object {
        private const val TAG = "CrackDetector"
        const val DEFAULT_INPUT_SIZE = 160
        const val NUM_CHANNELS = 3
        const val DEFAULT_QUANT_SCALE = 0.00390625f // 1/256
        const val DEFAULT_ZERO_POINT = 0
    }

    override val activeDelegate: DelegateType
    private val interpreter: Interpreter
    private var nnApiDelegate: NnApiDelegate? = null
    private var gpuDelegate: GpuDelegate? = null

    private val inputWidth: Int
    private val inputHeight: Int
    private val isInputQuantized: Boolean
    private val isSingleProbabilityOutput: Boolean
    private val numClasses: Int
    private val isOutputQuantized: Boolean

    private val inputByteBuffer: ByteBuffer
    private val intPixelValues: IntArray
    private val outputScale: Float
    private val outputZeroPoint: Int

    init {
        val modelBuffer = loadModelFile(context, modelAssetPath)

        // Attempt hierarchical delegate initialization: NNAPI -> GPU -> CPU
        var delegateWon = DelegateType.CPU
        var createdInterpreter: Interpreter? = null

        // 1. Try NNAPI Delegate (NPU / hardware accelerator)
        try {
            Log.d(TAG, "Attempting to initialize NNAPI delegate...")
            val nnOptions = NnApiDelegate.Options()
            val delegate = NnApiDelegate(nnOptions)
            val options = Interpreter.Options().apply {
                addDelegate(delegate)
            }
            val testInterpreter = Interpreter(modelBuffer, options)
            testInterpreter.allocateTensors()
            createdInterpreter = testInterpreter
            nnApiDelegate = delegate
            delegateWon = DelegateType.NNAPI
            Log.d(TAG, "NNAPI delegate initialized successfully.")
        } catch (e: Throwable) {
            Log.w(TAG, "NNAPI delegate initialization failed: ${e.message}. Trying GPU fallback...")
            nnApiDelegate?.close()
            nnApiDelegate = null
        }

        // 2. Fallback to GPU Delegate
        if (createdInterpreter == null) {
            try {
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                    val gpuOptions = compatList.bestOptionsForThisDevice
                    val delegate = GpuDelegate(gpuOptions)
                    val options = Interpreter.Options().apply {
                        addDelegate(delegate)
                    }
                    val testInterpreter = Interpreter(modelBuffer, options)
                    testInterpreter.allocateTensors()
                    createdInterpreter = testInterpreter
                    gpuDelegate = delegate
                    delegateWon = DelegateType.GPU
                    Log.d(TAG, "GPU delegate initialized successfully.")
                } else {
                    Log.d(TAG, "GPU delegate not supported on this device.")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "GPU delegate initialization failed: ${e.message}. Falling back to CPU...")
                gpuDelegate?.close()
                gpuDelegate = null
            }
        }

        // 3. Fallback to multi-threaded CPU
        if (createdInterpreter == null) {
            Log.d(TAG, "Initializing CPU interpreter with 4 threads...")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false)
            }
            val cpuInterpreter = Interpreter(modelBuffer, options)
            cpuInterpreter.allocateTensors()
            createdInterpreter = cpuInterpreter
            delegateWon = DelegateType.CPU
        }

        interpreter = createdInterpreter
            ?: throw IllegalStateException("All delegate initializations (NNAPI, GPU, CPU) failed. Cannot create interpreter.")
        activeDelegate = delegateWon

        // MANDATORY REQUIREMENT: Log which delegate wins at init
        Log.i(TAG, "CrackDetector initialized with delegate: $activeDelegate")

        // Inspect model tensor parameters
        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape() // [1, height, width, 3]
        inputHeight = if (inputShape.size >= 3 && inputShape[1] > 0) inputShape[1] else DEFAULT_INPUT_SIZE
        inputWidth = if (inputShape.size >= 3 && inputShape[2] > 0) inputShape[2] else DEFAULT_INPUT_SIZE
        isInputQuantized = inputTensor.dataType() == DataType.UINT8

        val outputTensor = interpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape()
        val totalOutputElements = outputShape.fold(1) { acc, i -> acc * i }
        isSingleProbabilityOutput = totalOutputElements == 1
        numClasses = if (isSingleProbabilityOutput) 1 else if (outputShape.size >= 2) outputShape[1] else 2
        isOutputQuantized = outputTensor.dataType() == DataType.UINT8

        val quantParams = outputTensor.quantizationParams()
        outputScale = if (quantParams != null && quantParams.scale > 0f) quantParams.scale else DEFAULT_QUANT_SCALE
        outputZeroPoint = quantParams?.zeroPoint ?: DEFAULT_ZERO_POINT
        Log.i(TAG, "Cached output quant params: scale=$outputScale, zeroPoint=$outputZeroPoint")

        val bytesPerChannel = if (isInputQuantized) 1 else 4
        inputByteBuffer = ByteBuffer.allocateDirect(1 * inputWidth * inputHeight * NUM_CHANNELS * bytesPerChannel).apply {
            order(ByteOrder.nativeOrder())
        }
        intPixelValues = IntArray(inputWidth * inputHeight)

        val inputQuant = inputTensor.quantizationParams()
        Log.i(
            TAG,
            "Input Tensor: name=${inputTensor.name()}, dataType=${inputTensor.dataType()}, " +
                    "shape=${inputShape.contentToString()}, scale=${inputQuant?.scale}, zeroPoint=${inputQuant?.zeroPoint}"
        )
        Log.i(
            TAG,
            "Output Tensor: name=${outputTensor.name()}, dataType=${outputTensor.dataType()}, " +
                    "shape=${outputShape.contentToString()}, scale=$outputScale, zeroPoint=$outputZeroPoint"
        )
    }

    override fun detect(bitmap: Bitmap): CrackDetectionResult {
        val startTime = SystemClock.elapsedRealtime()

        val scaledBitmap = if (bitmap.width == inputWidth && bitmap.height == inputHeight) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        }

        val crackClass: CrackClass
        val confidence: Float
        val severity: Severity
        val crackProbability: Float

        synchronized(this) {
            inputByteBuffer.rewind()
            loadBitmapIntoByteBuffer(scaledBitmap, inputByteBuffer, intPixelValues)
            inputByteBuffer.rewind()

            val scale = outputScale
            val zeroPoint = outputZeroPoint

            if (isSingleProbabilityOutput) {
                val crackProb = if (isOutputQuantized) {
                    val outputArray = Array(1) { ByteArray(1) }
                    interpreter.run(inputByteBuffer, outputArray)
                    val rawVal = outputArray[0][0].toInt() and 0xFF
                    // Dequantize formula: prob = (raw_value - zero_point) * scale
                    ((rawVal - zeroPoint) * scale).coerceIn(0.0f, 1.0f)
                } else {
                    val outputArray = Array(1) { FloatArray(1) }
                    interpreter.run(inputByteBuffer, outputArray)
                    outputArray[0][0].coerceIn(0.0f, 1.0f)
                }

                val (cls, sev) = SeverityClassifier.classifyProbability(crackProb)
                val conf = crackProb // Unified: confidence represents calibrated crack probability
                crackClass = cls
                confidence = conf
                severity = sev
                crackProbability = crackProb
                Log.d(TAG, "INFERENCE: crackProb=$crackProb, cls=$cls, sev=$sev")
            } else {
                // Multi-class classification fallback
                val (rawClassIndex, maxConfidence) = if (isOutputQuantized) {
                    val outputArray = Array(1) { ByteArray(numClasses) }
                    interpreter.run(inputByteBuffer, outputArray)

                    var maxIndex = 0
                    var maxProb = -1.0f
                    for (i in 0 until numClasses) {
                        val byteVal = outputArray[0][i].toInt() and 0xFF
                        val prob = (byteVal - zeroPoint) * scale
                        if (prob > maxProb) {
                            maxProb = prob
                            maxIndex = i
                        }
                    }
                    Pair(maxIndex, maxProb.coerceIn(0.0f, 1.0f))
                } else {
                    val outputArray = Array(1) { FloatArray(numClasses) }
                    interpreter.run(inputByteBuffer, outputArray)

                    var maxIndex = 0
                    var maxProb = -1.0f
                    for (i in 0 until numClasses) {
                        val prob = outputArray[0][i]
                        if (prob > maxProb) {
                            maxProb = prob
                            maxIndex = i
                        }
                    }
                    Pair(maxIndex, maxProb.coerceIn(0.0f, 1.0f))
                }

                val cls = when (numClasses) {
                    2 -> if (rawClassIndex == 1) CrackClass.STRUCTURAL else CrackClass.HAIRLINE
                    else -> when (rawClassIndex) {
                        2 -> CrackClass.STRUCTURAL
                        1 -> CrackClass.HAIRLINE
                        else -> CrackClass.NONE
                    }
                }
                val sev = SeverityClassifier.classify(cls, maxConfidence)
                val crackProb = if (cls != CrackClass.NONE) maxConfidence else (1.0f - maxConfidence)
                crackClass = cls
                confidence = maxConfidence
                severity = sev
                crackProbability = crackProb
            }
        }

        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }

        val latency = SystemClock.elapsedRealtime() - startTime

        return CrackDetectionResult(
            crackClass = crackClass,
            confidence = confidence,
            severity = severity,
            latencyMs = latency,
            delegateType = activeDelegate,
            timestamp = System.currentTimeMillis(),
            crackProbability = crackProbability
        )
    }

    private fun loadBitmapIntoByteBuffer(bitmap: Bitmap, byteBuffer: ByteBuffer, intValues: IntArray) {
        bitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        var pixel = 0
        for (i in 0 until inputHeight) {
            for (j in 0 until inputWidth) {
                val `val` = intValues[pixel++]
                val r = (`val` shr 16) and 0xFF
                val g = (`val` shr 8) and 0xFF
                val b = `val` and 0xFF

                if (isInputQuantized) {
                    byteBuffer.put(r.toByte())
                    byteBuffer.put(g.toByte())
                    byteBuffer.put(b.toByte())
                } else {
                    byteBuffer.putFloat(r / 255.0f)
                    byteBuffer.putFloat(g / 255.0f)
                    byteBuffer.putFloat(b / 255.0f)
                }
            }
        }
    }

    private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
        val candidates = listOf(
            assetPath,
            "crack_model.tflite"
        ).distinct()

        var lastException: Exception? = null
        for (candidate in candidates) {
            try {
                val fileDescriptor: AssetFileDescriptor = context.assets.openFd(candidate)
                val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
                val fileChannel: FileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
                Log.i(TAG, "Successfully loaded TFLite model from asset path: $candidate ($declaredLength bytes)")
                return buffer
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException("Failed to load model from assets: $assetPath")
    }

    override fun close() {
        interpreter.close()
        nnApiDelegate?.close()
        gpuDelegate?.close()
        Log.d(TAG, "LiteRTCrackDetector closed and delegate resources released.")
    }

    private data class DetectionTuple(
        val crackClass: CrackClass,
        val confidence: Float,
        val severity: Severity,
        val crackProbability: Float
    )
}
