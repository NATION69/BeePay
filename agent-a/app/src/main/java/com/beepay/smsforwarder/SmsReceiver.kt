package com.beepay.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) {
            return
        }

        val pending = goAsync()
        thread {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isEmpty()) return@thread

                val sender = messages.firstOrNull()?.originatingAddress ?: "unknown"
                val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
                val receivedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

                if (!FilterRules.shouldForward(context, sender, body)) {
                    return@thread
                }

                ForwarderClient.forwardSms(context, sender, body, receivedAt)
            } catch (t: Throwable) {
                android.util.Log.e("SmsReceiver", "Forward failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
