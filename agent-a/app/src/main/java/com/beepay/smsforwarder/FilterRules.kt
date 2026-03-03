package com.beepay.smsforwarder

import android.content.Context

object FilterRules {
    fun shouldForward(context: Context, sender: String, body: String): Boolean {
        val settings = AppSettings.load(context)
        val normalizedSender = sender.trim().lowercase()
        if (normalizedSender.isBlank()) return false

        if (settings.blockedSenders.any { normalizedSender.contains(it.lowercase()) }) {
            return false
        }

        if (settings.allowedSenders.isNotEmpty() && settings.allowedSenders.none { normalizedSender.contains(it.lowercase()) }) {
            return false
        }

        if (settings.requiredKeywords.isNotEmpty()) {
            val lower = body.lowercase()
            if (settings.requiredKeywords.none { lower.contains(it.lowercase()) }) {
                return false
            }
        }

        return true
    }
}
