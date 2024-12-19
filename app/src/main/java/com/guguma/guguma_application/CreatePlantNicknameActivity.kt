package com.guguma.guguma_application

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class CreatePlantNicknameActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var nicknameEditText: EditText
    private lateinit var nextButton: Button
    private var plantName: String? = null
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_plant_nickname)

        // View 초기화
        imageView = findViewById(R.id.userPlantPic)
        nicknameEditText = findViewById(R.id.plantNicknameEditText)
        nextButton = findViewById(R.id.nextBtn)

        // 데이터 수신 및 검증
        plantName = intent.getStringExtra("plantName")
        val imageUriString = intent.getStringExtra("imageUri")

        if (plantName.isNullOrEmpty() || imageUriString.isNullOrEmpty()) {
            showErrorAndFinish("필수 데이터가 누락되었습니다.")
            return
        }

        try {
            imageUri = Uri.parse(imageUriString)
        } catch (e: Exception) {
            showErrorAndFinish("이미지 URI를 파싱하는 데 실패했습니다.")
            return
        }
        // 이미지 설정
        Glide.with(this)
            .load(imageUri)
            .apply(RequestOptions.circleCropTransform()) // 이미지 둥글게 표시
            .into(imageView)

        // 다음 버튼 클릭 이벤트
        nextButton.setOnClickListener {
            val nickname = nicknameEditText.text.toString().trim()

            if (nickname.isEmpty()) {
                Toast.makeText(this, "닉네임을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            navigateToNextScreen(nickname)
        }
    }

    private fun navigateToNextScreen(nickname: String) {
        val intent = Intent(this, CreatePlantWaterActivity::class.java).apply {
            putExtra("plantName", plantName)
            putExtra("imageUri", imageUri.toString())
            putExtra("plantNickname", nickname)
        }
        startActivity(intent)
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish() // 현재 Activity 종료
    }
}