package com.beepay.smsforwarder

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object ForwarderClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient()

    fun forwardSms(context: Context, sender: String, body: String, receivedAt: String) {
        val settings = AppSettings.load(context)
        if (settings.agentBUrl.isBlank() || settings.sharedSecret.isBlank()) {
            android.util.Log.e("ForwarderClient", "Missing Agent B URL or shared secret")
            return
        }
        val outgoingBody = if (settings.maskOtp) maskOtp(body) else body

        val payload = JSONObject()
            .put("sender", sender)
            .put("body", outgoingBody)
            .put("received_at", receivedAt)
            .put("device_id", settings.deviceId.ifBlank { AgentConfig.DEVICE_ID })
            .put("msg_hash", buildMsgHash(sender, outgoingBody, receivedAt))
            .toString()

        val signature = "sha256=" + hmacSha256Hex(payload, settings.sharedSecret)

        val request = Request.Builder()
            .url(settings.agentBUrl)
            .post(payload.toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("x-agent-signature", signature)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                android.util.Log.e("ForwarderClient", "Failed: ${response.code} ${response.body?.string()}")
            } else {
                android.util.Log.i("ForwarderClient", "SMS forwarded successfully")
            }
        }
    }

    private fun buildMsgHash(sender: String, body: String, receivedAt: String): String {
        val raw = "$sender|$body|$receivedAt"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256Hex(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(key)
        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun maskOtp(text: String): String {
        return text.replace(Regex("\\b\\d{4,8}\\b"), "****")
    }
}
