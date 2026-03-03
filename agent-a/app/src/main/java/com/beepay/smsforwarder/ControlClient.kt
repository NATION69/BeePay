package com.beepay.smsforwarder

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object ControlClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient()

    fun sendChatCommand(context: Context, message: String): String {
        val settings = AppSettings.load(context)
        if (settings.controlChatUrl.isBlank()) {
            return "Control chat URL is empty."
        }

        val body = JSONObject().put("message", message).toString()
        val requestBuilder = Request.Builder()
            .url(settings.controlChatUrl)
            .post(body.toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")

        if (settings.controlSecret.isNotBlank()) {
            requestBuilder.addHeader("x-control-secret", settings.controlSecret)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return "Control error ${response.code}: $raw"
            }
            val json = JSONObject(raw)
            return json.optString("reply", raw)
        }
    }

    fun fetchRoutes(context: Context): String {
        val settings = AppSettings.load(context)
        if (settings.controlRoutesUrl.isBlank()) {
            return "Control routes URL is empty."
        }

        val requestBuilder = Request.Builder()
            .url(settings.controlRoutesUrl)
            .get()

        if (settings.controlSecret.isNotBlank()) {
            requestBuilder.addHeader("x-control-secret", settings.controlSecret)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return "Control error ${response.code}: $raw"
            }
            val json = JSONObject(raw)
            val routes = json.optJSONArray("routes") ?: JSONArray()
            if (routes.length() == 0) {
                return "No routes configured."
            }
            val lines = mutableListOf<String>()
            for (i in 0 until routes.length()) {
                val r = routes.getJSONObject(i)
                lines += "#${r.optInt("id")} sender~\"${r.optString("senderContains")}\" => ${r.optString("targetType")}(${r.optString("targetId")})"
            }
            return lines.joinToString(separator = "\n")
        }
    }
}
