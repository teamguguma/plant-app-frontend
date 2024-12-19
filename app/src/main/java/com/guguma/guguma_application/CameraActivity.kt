package com.guguma.guguma_application

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    private val handler = Handler(Looper.getMainLooper())
    private val detectInterval = 4000L
    private var isDetecting = false

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewFinder = findViewById(R.id.viewFinder)
        cameraExecutor = Executors.newSingleThreadExecutor()

        checkCameraPermission()

        findViewById<ImageButton>(R.id.close_button).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.shutter_button).setOnClickListener {
            stopDetectLoop()
            takePicture()
        }

        startDetectLoop()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) startCamera() else {
                    Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("CameraActivity", "카메라 시작 실패", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startDetectLoop() {
        if (!isDetecting) {
            isDetecting = true
            handler.post(object : Runnable {
                override fun run() {
                    if (isDetecting) {
                        takePictureForDetect()
                        handler.postDelayed(this, detectInterval)
                    }
                }
            })
        }
    }

    private fun stopDetectLoop() {
        isDetecting = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun takePicture() {
        if (!::imageCapture.isInitialized) return

        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val bitmap = imageProxy.toBitmap()
                imageProxy.close()

                bitmap?.let {
                    stopDetectLoop()
                    runOnUiThread { showCompletedDialog(it) }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraActivity", "사진 촬영 실패: ${exception.message}")
            }
        })
    }

    private fun takePictureForDetect() {
        if (!::imageCapture.isInitialized) return

        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val bitmap = imageProxy.toBitmap()
                imageProxy.close()

                bitmap?.let { detectImage(it) }
            }
        })
    }

    private fun showCompletedDialog(bitmap: Bitmap) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("촬영 완료")
            .setMessage("촬영이 완료되었습니다.\n확인 버튼을 눌러주세요.")
            .setPositiveButton("확인") { _, _ -> uploadImageAndProceed(bitmap) }
            .setCancelable(false)
            .create()

        dialog.show()
    }

    private fun detectImage(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val byteArray = stream.toByteArray()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "image.jpg", byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url(BuildConfig.API_PLANT_DETECT)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (!responseBody.isNullOrEmpty()) {
                    val jsonResponse = JSONObject(responseBody)
                    val message = jsonResponse.optString("message", "식별 실패")

                    // 메시지에 "다시 촬영해주세요"가 포함된 경우에만 안내 표시
                    if (message.contains("다시 촬영해주세요")) {
                        runOnUiThread {
                            showWarningDialog(message) // 다이얼로그로 안내 표시
                        }
                    }
                    runOnUiThread { handleServerMessage(message, bitmap) }
                } else {
                    runOnUiThread { showWarningDialog("서버 응답이 없습니다.") }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { showWarningDialog("네트워크 오류가 발생했습니다.") }
            }
        })
    }

    private fun uploadImageAndProceed(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val byteArray = stream.toByteArray()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "uploaded_image.jpg", byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url(BuildConfig.API_IMAGE_UPLOAD)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@CameraActivity, "이미지 업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val fileUrl = response.body?.string().orEmpty()
                    runOnUiThread {
                        val intent = Intent(this@CameraActivity, CreatePlantNameActivity::class.java).apply {
                            putExtra("imageUrl", fileUrl)
                        }
                        startActivity(intent)
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@CameraActivity, "서버 오류: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun handleServerMessage(message: String, bitmap: Bitmap) {
        stopDetectLoop()
        when (message) {
            "식물을 등록하시겠습니까?" -> showCompletedDialog(bitmap)
            else -> showWarningDialog(message)
        }
    }

    private fun showWarningDialog(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("확인") { _, _ -> startDetectLoop() }
            .setCancelable(false)
            .show()
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        return try {
            val buffer = planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            val yuvImage = YuvImage(data, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        } catch (e: Exception) {
            Log.e("CameraActivity", "Bitmap 변환 실패", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDetectLoop()
        cameraExecutor.shutdownNow()
    }
}