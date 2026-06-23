package com.example.extractor

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.example.BuildConfig

/**
 * Advanced Gemini AI-powered decompilation and source-code reconstruction engine.
 * Restores method bodies, deobfuscates symbols, reconstructs class relationships,
 * and fixes binary XMLs/Manifests.
 */
object GeminiDecompiler {
    private const val TAG = "GeminiDecompiler"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isKeyAvailable(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    /**
     * Performs AI reconstruction of a class's source code based on strings and structural clues.
     */
    suspend fun reconstructClass(
        className: String,
        stringsList: List<String>,
        layoutContext: String? = null
    ): String {
        if (!isKeyAvailable()) {
            return "// AI Decompiler Key Not Found. Set your GEMINI_API_KEY in Secrets.\n"
        }

        val systemPrompt = """
            You are the ultimate Android Jetpack/Dex decompiler and AST source reconstructor.
            Your task is to reconstruct a complete, syntactically correct, beautiful and highly accurate Java/Kotlin class based on DEX string pool elements and class structure.
            Since the class was optimized, obfuscated, or decompiled, you must perform deep deobfuscation:
            1. Restore actual method bodies, lifecycle flow (e.g. onCreate, onResume), and control logic.
            2. Match UI elements (like buttons, clicks, text fields) from the provided layouts or strings context if available.
            3. Infer original variable names, loops, exception handling, and interfaces/inheritance based on constants.
            4. If there is native code, encryption, packing, or native methods, preserve the native signature and append comments clearly explaining the virtualization or native packaging.
            Output ONLY the valid Java/Kotlin source code inside markdown code block. Do NOT write any conversational text.
        """.trimIndent()

        val inputPrompt = """
            Class Name: $className
            Strings extracted from DEX:
            ${stringsList.take(60).joinToString("\n- ")}
            
            Layout context (if any):
            $layoutContext
            
            Synthesize the class $className. Output standard, production-ready, deobfuscated Kotlin/Java code with reconstructed business logic, method implementations, click listeners, and imports.
        """.trimIndent()

        return try {
            callGeminiApi(systemPrompt, inputPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "Failed calling Gemini API", e)
            "// AI Decompilation failed: ${e.localizedMessage}\n"
        }
    }

    /**
     * Performs AI reconstruction of AndroidManifest.xml based on parsed chunks and string pool.
     */
    suspend fun reconstructManifest(
        rawManifest: String,
        appName: String
    ): String {
        if (!isKeyAvailable()) {
            return rawManifest
        }

        val systemPrompt = """
            You are an expert Android XML build tools engineer.
            Your task is to take a raw, partially decompressed, or corrupted binary AndroidManifest.xml string and reconstruct it into a perfectly valid, standard, beautiful, and buildable AndroidManifest.xml for Android Studio.
            Restore standard attributes like android:name, android:label, launcher Intent filters, services, receivers, permissions, and correct target SDK properties based on context and standard templates.
            Output ONLY the raw XML string, wrapped in ```xml.
        """.trimIndent()

        val inputPrompt = """
            App Name: $appName
            Raw manifest source extracted:
            $rawManifest
            
            Reconstruct the valid AndroidManifest.xml.
        """.trimIndent()

        return try {
            callGeminiApi(systemPrompt, inputPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reconstruct manifest", e)
            rawManifest
        }
    }

    private fun callGeminiApi(systemPrompt: String, prompt: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        // Using recommended complex text tasks model `gemini-3.1-pro-preview`
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-pro-preview:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemPrompt)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2) // Low temperature for exact coding structure
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "Empty body"
                Log.e(TAG, "API unsuccessful: Code ${response.code}, message: $err")
                // Let's fallback to `gemini-3.5-flash` if model isn't provisioned or quota exceeded
                if (response.code == 404 || response.code == 400 || response.code == 429) {
                    return callGeminiApiFallback(systemPrompt, prompt)
                }
                throw Exception("HTTP ${response.code}: $err")
            }

            val bodyText = response.body?.string() ?: throw Exception("Body empty")
            val responseJson = JSONObject(bodyText)
            val candidates = responseJson.optJSONArray("candidates") ?: throw Exception("Malformed response: no candidates")
            if (candidates.length() == 0) throw Exception("Empty response candidates")
            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content") ?: throw Exception("No content in candidate")
            val parts = content.optJSONArray("parts") ?: throw Exception("No parts in content")
            if (parts.length() == 0) throw Exception("Empty parts in content")
            val text = parts.getJSONObject(0).optString("text")

            return extractCodeBlock(text)
        }
    }

    private fun callGeminiApiFallback(systemPrompt: String, prompt: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemPrompt)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "Empty body"
                throw Exception("HTTP ${response.code}: $err")
            }

            val bodyText = response.body?.string() ?: throw Exception("Body empty")
            val responseJson = JSONObject(bodyText)
            val candidates = responseJson.optJSONArray("candidates") ?: throw Exception("Malformed response: no candidates")
            if (candidates.length() == 0) throw Exception("Empty response candidates")
            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content") ?: throw Exception("No content in candidate")
            val parts = content.optJSONArray("parts") ?: throw Exception("No parts in content")
            if (parts.length() == 0) throw Exception("Empty parts in content")
            val text = parts.getJSONObject(0).optString("text")

            return extractCodeBlock(text)
        }
    }

    private fun extractCodeBlock(text: String): String {
        val regex = Regex("```(?:kotlin|java|xml|gradle)?\\s*(.*?)\\s*```", RegexOption.DOT_MATCHES_ALL)
        val matchResult = regex.find(text)
        return matchResult?.groups?.get(1)?.value ?: text
    }
}
