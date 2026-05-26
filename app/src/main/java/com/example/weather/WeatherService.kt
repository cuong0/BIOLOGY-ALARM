package com.example.weather

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class WeatherInfo(
    val temperature: Int,
    val description: String,
    val type: String, // sunny, rainy, cloudy, windy, thunderstorm
    val source: String // "Google Weather AI" or "Dự báo Sinh học"
)

object WeatherService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun getEstimatedWeather(
        context: Context,
        lat: Double?,
        lng: Double?,
        alarmTimeMs: Long
    ): WeatherInfo = fetchWeatherFromGemini(
        lat,
        lng,
        "Suggest a realistic weather for Vietnam at coordinate (lat=${lat ?: 21.0285}, lng=${lng ?: 105.8542}) tomorrow morning at ${SimpleDateFormat("EEEE, 'ngày' dd/MM 'lúc' HH:mm", Locale("vi", "VN")).format(Date(alarmTimeMs))}. Respond ONLY in standard raw JSON with no Markdown wrappers, formatting exactly: { \"temp\": 26, \"desc\": \"Thời tiết ấm áp mang khí xuân mát mẻ\", \"type\": \"cloudy\" }. Options for 'type' are only: sunny, rainy, cloudy, windy, thunderstorm."
    ) ?: fallbackWeather(lat ?: 21.0285, lng ?: 105.8542, Calendar.getInstance().apply { timeInMillis = alarmTimeMs }.get(Calendar.HOUR_OF_DAY))

    suspend fun getCurrentWeather(
        lat: Double?,
        lng: Double?
    ): WeatherInfo = fetchWeatherFromGemini(
        lat,
        lng,
        "Get the current weather for Vietnam at coordinate (lat=${lat ?: 21.0285}, lng=${lng ?: 105.8542}). Respond ONLY in standard raw JSON with no Markdown wrappers, formatting exactly: { \"temp\": 26, \"desc\": \"Thời tiết hiện tại\", \"type\": \"cloudy\" }. Options for 'type' are only: sunny, rainy, cloudy, windy, thunderstorm."
    ) ?: fallbackWeather(lat ?: 21.0285, lng ?: 105.8542, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

    private suspend fun fetchWeatherFromGemini(lat: Double?, lng: Double?, prompt: String): WeatherInfo? = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return@withContext null

        try {
            val jsonRequest = JSONObject().apply {
                put("contents", org.json.JSONArray().put(JSONObject().apply {
                    put("parts", org.json.JSONArray().put(JSONObject().apply {
                        put("text", prompt)
                    }))
                }))
            }

            val requestBody = jsonRequest.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val root = JSONObject(bodyString)
                    val candidate = root.getJSONArray("candidates").getJSONObject(0)
                    val text = candidate.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                    val cleanText = text.replace("```json", "").replace("```", "").trim()
                    val resultJson = JSONObject(cleanText)
                    
                    return@withContext WeatherInfo(
                        temperature = resultJson.getInt("temp"),
                        description = resultJson.getString("desc"),
                        type = resultJson.getString("type").lowercase(),
                        source = "Google AI Weather"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun fallbackWeather(latitude: Double, longitude: Double, hour: Int): WeatherInfo {
        val month = Calendar.getInstance().get(Calendar.MONTH)
        val isSouth = latitude < 16.0
        val temp: Int
        val desc: String
        val type: String

        if (isSouth) {
            val isRainySeason = month in 4..10
            if (isRainySeason && hour !in 5..9) {
                temp = 28 + (0..4).random(); desc = "Nóng ẩm, có khả năng mưa rào nhiệt đới"; type = "rainy"
            } else {
                temp = 25 + (0..3).random(); desc = "Mát mẻ"; type = "cloudy"
            }
        } else {
            val isSummer = month in 4..8
            temp = if (isSummer) 26 + (0..3).random() else 22 + (0..3).random()
            desc = "Thời tiết hôm nay"
            type = if (isSummer) "sunny" else "cloudy"
        }

        return WeatherInfo(temp, desc, type, "Google Weather (Ước tính sinh học)")
    }
}
