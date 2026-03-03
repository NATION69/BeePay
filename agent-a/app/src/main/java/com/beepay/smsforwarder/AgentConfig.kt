package com.beepay.smsforwarder

object AgentConfig {
    // TODO: Replace with your Agent B URL reachable from the Android device.
    const val AGENT_B_URL = "http://192.168.1.100:8080/ingest"
    const val CONTROL_CHAT_URL = "http://192.168.1.100:8080/control/chat"
    const val CONTROL_ROUTES_URL = "http://192.168.1.100:8080/control/routes"

    // TODO: Use the same exact secret configured in Agent B (.env).
    const val SHARED_SECRET = "replace_with_long_random_secret"
    const val CONTROL_SECRET = ""

    // Device label shown in Telegram messages.
    const val DEVICE_ID = "android-device-01"
}
