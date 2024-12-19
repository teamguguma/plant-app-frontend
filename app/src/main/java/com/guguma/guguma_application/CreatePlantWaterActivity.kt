package com.guguma.guguma_application

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class CreatePlantWaterActivity : AppCompatActivity() {

    private lateinit var waterIntervalEditText: EditText
    private lateinit var saveButton: Button

    private val okHttpClient = OkHttpClient()
    private val registerUrl = BuildConfig.API_PLANT_CREATE // 백엔드 API URL

    private var plantName: String? = null
    private var imageUri: String? = null
    private var plantNickname: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_plant_water)

        // View 초기화
        waterIntervalEditText = findViewById(R.id.waterIntervalEditText)
        saveButton = findViewById(R.id.saveBtn)

        // 데이터 수신 및 검증
        receiveIntentData()

        saveButton.setOnClickListener {
            handleSaveButtonClick()
        }
    }

    private fun receiveIntentData() {
        plantName = intent.getStringExtra("plantName")
        imageUri = intent.getStringExtra("imageUri")
        plantNickname = intent.getStringExtra("plantNickname")

        if (plantName.isNullOrEmpty() || imageUri.isNullOrEmpty() || plantNickname.isNullOrEmpty()) {
            Toast.makeText(this, "필수 데이터가 누락되었습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun handleSaveButtonClick() {
        val waterInterval = waterIntervalEditText.text.toString().trim()

        if (waterInterval.isEmpty()) {
            Toast.makeText(this, "물 주기 간격을 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val interval = waterInterval.toInt()
            if (interval <= 0) {
                Toast.makeText(this, "물 주기 간격은 양수로 입력해주세요.", Toast.LENGTH_SHORT).show()
                return
            }
            sendPlantDataToServer(interval)
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "물 주기 간격은 숫자로 입력해주세요.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendPlantDataToServer(waterInterval: Int) {
        val userUuid = getSharedPreferences("user_prefs", MODE_PRIVATE).getString("userUuid", null)
        Log.d("SharedPreferences", "userUuid: $userUuid")
        if (userUuid.isNullOrEmpty()) {
            Toast.makeText(this, "사용자 UUID를 찾을 수 없습니다. 다시 로그인하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val jsonBody = """
        {
            "name": "$plantName",
            "nickname": "$plantNickname",
            "waterInterval": $waterInterval,
            "imageUri": "$imageUri",
            "userUuid": "$userUuid"
        }
        """.trimIndent()

        Log.d("RequestBody", "Sending JSON: $jsonBody")

        val requestBody = jsonBody.toRequestBody(contentType = "application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(registerUrl)
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handleServerFailure(e)
            }

            override fun onResponse(call: Call, response: Response) {
                handleServerResponse(response)
            }
        })
    }

    private fun handleServerFailure(e: IOException) {
        runOnUiThread {
            Toast.makeText(this, "등록 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleServerResponse(response: Response) {
        runOnUiThread {
            if (response.isSuccessful) {
                Toast.makeText(this, "식물 등록 성공", Toast.LENGTH_SHORT).show()
                navigateToHome()
            } else {
                val errorMessage = extractErrorMessage(response)
                Toast.makeText(this, "등록 실패: $errorMessage", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun extractErrorMessage(response: Response): String {
        return try {
            val jsonResponse = JSONObject(response.body?.string() ?: "{}")
            jsonResponse.optString("message", "알 수 없는 오류")
        } catch (e: Exception) {
            "알 수 없는 오류"
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
}