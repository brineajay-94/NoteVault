package com.example.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API Key is missing. Please configure GEMINI_API_KEY in the Secrets panel in AI Studio."
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val requestBodyJson = """
            {
              "contents": [{
                "parts": [{
                  "text": "${escapeJson(prompt)}"
                }]
              }],
              "generationConfig": {
                "temperature": 0.4
              }
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestBodyJson.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unsuccessful response from Gemini: Code=${response.code}, Body=$bodyString")
                    return@withContext "Error details: HTTP ${response.code}. Ensure your api key has proper access."
                }
                
                val jsonObject = JSONObject(bodyString)
                val candidates = jsonObject.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).getString("text")
                    }
                }
                "Empty API response."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request failed", e)
            "Error calling Gemini: ${e.localizedMessage ?: "Unknown network exception"}"
        }
    }

    suspend fun summarizeNoteContent(title: String, content: String): String {
        val prompt = "You are a secure, helpful note summarization assistant. Summarize the following note. Do not introduce any preambles or chat. Return a concise, high-level, 1-3 bullet-point summary of this note:\n\nTitle: $title\nContent:\n$content"
        return generateResponse(prompt)
    }

    suspend fun suggestStrongPassword(platform: String): String {
        val prompt = "You are a secure, randomized password generator. Generate a strong, secure, complex, randomized password for the platform '$platform'. " +
                "The password must be around 14-16 characters long, containing uppercase letters, lowercase letters, digits, and special symbols (e.g. !, @, #, $, %, ^, &, *). " +
                "Do not explain anything. Output ONLY the password string so the user can easily copy it."
        val pass = generateResponse(prompt).trim()
        // If Gemini has headers or ticks, clean them up
        return pass.removeSurrounding("`").removeSurrounding("\"").trim()
    }
}
