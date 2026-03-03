package com.beepay.smsforwarder

import android.content.Context

data class SettingsSnapshot(
    val agentBUrl: String,
    val controlChatUrl: String,
    val controlRoutesUrl: String,
    val sharedSecret: String,
    val controlSecret: String,
    val deviceId: String,
    val allowedSenders: Set<String>,
    val blockedSenders: Set<String>,
    val requiredKeywords: Set<String>,
    val maskOtp: Boolean
)

object AppSettings {
    private const val PREFS_NAME = "beepay_sms_forwarder_prefs"
    private const val KEY_AGENT_B_URL = "agent_b_url"
    private const val KEY_CONTROL_CHAT_URL = "control_chat_url"
    private const val KEY_CONTROL_ROUTES_URL = "control_routes_url"
    private const val KEY_SHARED_SECRET = "shared_secret"
    private const val KEY_CONTROL_SECRET = "control_secret"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_ALLOWED_SENDERS = "allowed_senders"
    private const val KEY_BLOCKED_SENDERS = "blocked_senders"
    private const val KEY_REQUIRED_KEYWORDS = "required_keywords"
    private const val KEY_MASK_OTP = "mask_otp"

    fun load(context: Context): SettingsSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return SettingsSnapshot(
            agentBUrl = prefs.getString(KEY_AGENT_B_URL, AgentConfig.AGENT_B_URL).orEmpty().trim(),
            controlChatUrl = prefs.getString(KEY_CONTROL_CHAT_URL, AgentConfig.CONTROL_CHAT_URL).orEmpty().trim(),
            controlRoutesUrl = prefs.getString(KEY_CONTROL_ROUTES_URL, AgentConfig.CONTROL_ROUTES_URL).orEmpty().trim(),
            sharedSecret = prefs.getString(KEY_SHARED_SECRET, AgentConfig.SHARED_SECRET).orEmpty().trim(),
            controlSecret = prefs.getString(KEY_CONTROL_SECRET, AgentConfig.CONTROL_SECRET).orEmpty().trim(),
            deviceId = prefs.getString(KEY_DEVICE_ID, AgentConfig.DEVICE_ID).orEmpty().trim(),
            allowedSenders = parseList(prefs.getString(KEY_ALLOWED_SENDERS, "").orEmpty()),
            blockedSenders = parseList(prefs.getString(KEY_BLOCKED_SENDERS, "").orEmpty()),
            requiredKeywords = parseList(prefs.getString(KEY_REQUIRED_KEYWORDS, "").orEmpty()),
            maskOtp = prefs.getBoolean(KEY_MASK_OTP, true)
        )
    }

    fun save(
        context: Context,
        agentBUrl: String,
        controlChatUrl: String,
        controlRoutesUrl: String,
        sharedSecret: String,
        controlSecret: String,
        deviceId: String,
        allowedSendersRaw: String,
        blockedSendersRaw: String,
        requiredKeywordsRaw: String,
        maskOtp: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_AGENT_B_URL, agentBUrl.trim())
            .putString(KEY_CONTROL_CHAT_URL, controlChatUrl.trim())
            .putString(KEY_CONTROL_ROUTES_URL, controlRoutesUrl.trim())
            .putString(KEY_SHARED_SECRET, sharedSecret.trim())
            .putString(KEY_CONTROL_SECRET, controlSecret.trim())
            .putString(KEY_DEVICE_ID, deviceId.trim())
            .putString(KEY_ALLOWED_SENDERS, normalizeRawList(allowedSendersRaw))
            .putString(KEY_BLOCKED_SENDERS, normalizeRawList(blockedSendersRaw))
            .putString(KEY_REQUIRED_KEYWORDS, normalizeRawList(requiredKeywordsRaw))
            .putBoolean(KEY_MASK_OTP, maskOtp)
            .apply()
    }

    private fun normalizeRawList(raw: String): String {
        return parseList(raw).joinToString(separator = "\n")
    }

    private fun parseList(raw: String): Set<String> {
        return raw.split(',', '\n', '\r', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
