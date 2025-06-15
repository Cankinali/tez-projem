package com.yusuf.deney2.util

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.FloatBuffer

object ModelLoader {
    lateinit var ortSession: OrtSession

    fun initModel(context: Context) {
        try {
            Log.d("ONNX_DEBUG", "initModel çağrıldı")
            val env = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open("model.onnx").readBytes()
            Log.d("ONNX_DEBUG", "model.onnx yüklendi, boyut: ${modelBytes.size}")
            val sessionOptions = OrtSession.SessionOptions()
            ortSession = env.createSession(modelBytes, sessionOptions)
            Log.d("ONNX_DEBUG", "Session başarıyla oluşturuldu")
        } catch (e: Exception) {
            Log.e("ONNX_ERROR", "Model yükleme hatası: ${e.message}")
        }
    }

    fun testOnnxModel(onResult: (Float) -> Unit, inputArray: FloatArray) {
        try {
            Log.d("ONNX_DEBUG", "testOnnxModel() çağrıldı")
            Log.d("ONNX_DEBUG", "Kullanıcıdan gelen input uzunluğu: ${inputArray.size}")
            Log.d("ONNX_DEBUG", "Kullanıcı input verisi: ${inputArray.joinToString()}")

            val fixedLength = 172
            val paddedInput = when {
                inputArray.size >= fixedLength -> {
                    inputArray.copyOfRange(0, fixedLength)
                }

                else -> {
                    FloatArray(fixedLength) { i -> if (i < inputArray.size) inputArray[i] else 0.0f }
                }
            }

            val env = OrtEnvironment.getEnvironment()
            val session = ortSession

            val inputShape = longArrayOf(1, 172)
            val buffer = FloatBuffer.wrap(paddedInput)
            val tensor = OnnxTensor.createTensor(env, buffer, inputShape)
            Log.d("ONNX_DEBUG", "Tensor oluşturuldu: shape = ${inputShape.joinToString()}")

            val inputs = mapOf("float_input" to tensor)
            Log.d("ONNX_DEBUG", "Model çalıştırılıyor...")
            val results = session.run(inputs)
            Log.d("ONNX_DEBUG", "Model çıktısı alındı")

            val result = results[0].value
            Log.d("ONNX_RESULT", "Raw output type: ${result!!::class.java}")
            Log.d("ONNX_RESULT", "Raw output toString(): ${result.toString()}")

            if (result is LongArray) {
                val prediction = result[0].toFloat()
                Log.d("ONNX_RESULT", "Prediction sonucu (LongArray): $prediction")
                Log.d("ONNX_RESULT", "Gerçek model output değeri: $prediction")
                onResult(prediction)
            } else if (result is Array<*> && result.firstOrNull() is Long) {
                val prediction = (result[0] as Long).toFloat()
                Log.d("ONNX_RESULT", "Prediction sonucu (Array<Long>): $prediction")
                Log.d("ONNX_RESULT", "Gerçek model output değeri: $prediction")
                onResult(prediction)
            } else if (result is FloatArray) {
                val prediction = result[0]
                Log.d("ONNX_RESULT", "Prediction sonucu (FloatArray): $prediction")
                Log.d("ONNX_RESULT", "Gerçek model output değeri: $prediction")
                onResult(prediction)
            } else {
                Log.e("ONNX_RESULT", "Model çıktısı beklenen formatta değil: ${result!!::class.java}")
            }
        } catch (e: Exception) {
            Log.e("ONNX_RESULT", "Model çalıştırılamadı: ${e.message}")
        }
    }
}