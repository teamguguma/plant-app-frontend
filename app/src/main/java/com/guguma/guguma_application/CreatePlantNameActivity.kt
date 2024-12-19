package com.guguma.guguma_application

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.IOException
import java.io.File

class CreatePlantNameActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var plantNameEditText: EditText
    private lateinit var retryPhotoButton: Button
    private lateinit var registerPlantButton: Button // 닉네임으로 이동(다음버튼)
    private lateinit var loadingTextView: TextView // 로딩 상태를 표시할 TextView

    private val okHttpClient: OkHttpClient = OkHttpClient()
    private var imageUri: Uri? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                imageUri = it
                loadImage(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_plant_name)
        // EditText 초기화
        plantNameEditText = findViewById(R.id.plantNameEditText)
        imageView = findViewById(R.id.plantImageView)
        val imageUrl = intent.getStringExtra("imageUrl")

        if (!imageUrl.isNullOrEmpty()) {
            // Glide로 이미지 로드
            Glide.with(this)
                .load(imageUrl)
                .into(findViewById(R.id.plantImageView))
            val uri = Uri.parse(imageUrl)
            recognizePlant(uri)
        } else {
            Toast.makeText(this, "이미지 URL을 받지 못했습니다.", Toast.LENGTH_SHORT).show()
        }
        // 버튼 클릭 리스너 추가
        registerPlantButton = findViewById(R.id.gotoNicknamePlantBtn) // 버튼 ID가 맞는지 확인
        registerPlantButton.setOnClickListener {
            goToNicknameActivity()
        }
    }

    // Glide를 사용하여 이미지 표시
    private fun loadImage(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .apply(RequestOptions.centerCropTransform())
            .into(imageView)
    }

    private fun goToNicknameActivity() {
        val plantName = plantNameEditText.text.toString()
        if (plantName.isEmpty()) {
            Toast.makeText(this, "식물 이름을 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, CreatePlantNicknameActivity::class.java).apply {
            val imageUrl = intent.getStringExtra("imageUrl")
            putExtra("plantName", plantName)
            putExtra("imageUri", imageUrl) // 이미지 파일 경로 전달
        }
        startActivity(intent)
    }
    private fun recognizePlant(uri: Uri) {
        // 로딩 상태 표시
        loadingTextView.text = "식물 이름을 인식 중입니다..."
        loadingTextView.visibility = TextView.VISIBLE

        val file = getFileFromUri(uri)
        if (file == null || !file.exists()) {
            Toast.makeText(this, "이미지 파일을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            loadingTextView.visibility = TextView.GONE
            return
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image", file.name,
                RequestBody.create("image/jpeg".toMediaTypeOrNull(), file)
            )
            .build()

        val request = Request.Builder()
            .url(BuildConfig.API_PLANT_RECOGNIZE)
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loadingTextView.visibility = TextView.GONE
                    Toast.makeText(this@CreatePlantNameActivity, "인식 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val plantName = response.body?.string()?.trim()
                Log.d("ServerResponse", "Response: $plantName")
                runOnUiThread {
                    loadingTextView.visibility = TextView.GONE
                    if (response.isSuccessful && !plantName.isNullOrEmpty()) {
                        plantNameEditText.setText(plantName)
                        Toast.makeText(this@CreatePlantNameActivity, "식물 이름: $plantName", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@CreatePlantNameActivity, "식물 이름을 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)

            // 압축된 파일을 임시로 저장할 경로
            val tempFile = File.createTempFile("compressed_image", ".jpg", cacheDir)

            // 1MB 미만이 될 때까지 압축
            var quality = 100
            do {
                val outputStream = tempFile.outputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                outputStream.close()
                quality -= 5
            } while (tempFile.length() >= 1024 * 1024 && quality > 0)

            tempFile
        } catch (e: Exception) {
            Log.e("FileError", "Error compressing file: ${e.message}")
            null
        }
    }
}