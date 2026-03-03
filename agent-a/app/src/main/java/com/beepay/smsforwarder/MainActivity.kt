package com.beepay.smsforwarder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var chatLogText: TextView

    private lateinit var agentUrlInput: EditText
    private lateinit var controlChatUrlInput: EditText
    private lateinit var controlRoutesUrlInput: EditText
    private lateinit var sharedSecretInput: EditText
    private lateinit var controlSecretInput: EditText
    private lateinit var deviceIdInput: EditText
    private lateinit var allowedSendersInput: EditText
    private lateinit var blockedSendersInput: EditText
    private lateinit var requiredKeywordsInput: EditText
    private lateinit var chatInput: EditText

    private lateinit var maskOtpSwitch: Switch

    private lateinit var saveButton: Button
    private lateinit var sendChatButton: Button
    private lateinit var showRoutesButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        loadSettings()
        bindActions()

        ensureSmsPermission()
        updatePermissionStatus()
    }

    private fun bindViews() {
        statusText = findViewById(R.id.statusText)
        chatLogText = findViewById(R.id.chatLogText)

        agentUrlInput = findViewById(R.id.agentUrlInput)
        controlChatUrlInput = findViewById(R.id.controlChatUrlInput)
        controlRoutesUrlInput = findViewById(R.id.controlRoutesUrlInput)
        sharedSecretInput = findViewById(R.id.sharedSecretInput)
        controlSecretInput = findViewById(R.id.controlSecretInput)
        deviceIdInput = findViewById(R.id.deviceIdInput)
        allowedSendersInput = findViewById(R.id.allowedSendersInput)
        blockedSendersInput = findViewById(R.id.blockedSendersInput)
        requiredKeywordsInput = findViewById(R.id.requiredKeywordsInput)
        chatInput = findViewById(R.id.chatInput)

        maskOtpSwitch = findViewById(R.id.maskOtpSwitch)

        saveButton = findViewById(R.id.saveButton)
        sendChatButton = findViewById(R.id.sendChatButton)
        showRoutesButton = findViewById(R.id.showRoutesButton)
    }

    private fun loadSettings() {
        val settings = AppSettings.load(this)
        agentUrlInput.setText(settings.agentBUrl)
        controlChatUrlInput.setText(settings.controlChatUrl)
        controlRoutesUrlInput.setText(settings.controlRoutesUrl)
        sharedSecretInput.setText(settings.sharedSecret)
        controlSecretInput.setText(settings.controlSecret)
        deviceIdInput.setText(settings.deviceId)
        allowedSendersInput.setText(settings.allowedSenders.joinToString("\n"))
        blockedSendersInput.setText(settings.blockedSenders.joinToString("\n"))
        requiredKeywordsInput.setText(settings.requiredKeywords.joinToString("\n"))
        maskOtpSwitch.isChecked = settings.maskOtp
    }

    private fun bindActions() {
        saveButton.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        sendChatButton.setOnClickListener {
            saveSettings()
            val message = chatInput.text.toString().trim()
            if (message.isBlank()) {
                Toast.makeText(this, "Type command first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            appendChat("You: $message")
            chatInput.setText("")

            thread {
                val reply = try {
                    ControlClient.sendChatCommand(this, message)
                } catch (t: Throwable) {
                    "Agent error: ${t.message}"
                }
                runOnUiThread {
                    appendChat("Agent: $reply")
                }
            }
        }

        showRoutesButton.setOnClickListener {
            saveSettings()
            appendChat("You: show routes")
            thread {
                val reply = try {
                    ControlClient.fetchRoutes(this)
                } catch (t: Throwable) {
                    "Agent error: ${t.message}"
                }
                runOnUiThread {
                    appendChat("Routes:\n$reply")
                }
            }
        }
    }

    private fun saveSettings() {
        AppSettings.save(
            context = this,
            agentBUrl = agentUrlInput.text.toString(),
            controlChatUrl = controlChatUrlInput.text.toString(),
            controlRoutesUrl = controlRoutesUrlInput.text.toString(),
            sharedSecret = sharedSecretInput.text.toString(),
            controlSecret = controlSecretInput.text.toString(),
            deviceId = deviceIdInput.text.toString(),
            allowedSendersRaw = allowedSendersInput.text.toString(),
            blockedSendersRaw = blockedSendersInput.text.toString(),
            requiredKeywordsRaw = requiredKeywordsInput.text.toString(),
            maskOtp = maskOtpSwitch.isChecked
        )
    }

    private fun appendChat(line: String) {
        val current = chatLogText.text?.toString().orEmpty()
        val next = if (current.isBlank()) line else "$current\n\n$line"
        chatLogText.text = next
    }

    private fun ensureSmsPermission() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS), 1001)
        }
    }

    private fun updatePermissionStatus() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        statusText.text = if (granted) {
            "SMS permission: granted"
        } else {
            "SMS permission: not granted"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            updatePermissionStatus()
        }
    }
}