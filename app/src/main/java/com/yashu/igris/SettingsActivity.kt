package com.yashu.igris

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.ComponentActivity

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val ipInput = findViewById<EditText>(R.id.ipInput)
        val saveButton = findViewById<Button>(R.id.saveButton)

        val prefs = getSharedPreferences("ARISE", MODE_PRIVATE)

        ipInput.setText(
            prefs.getString(
                "SERVER_IP",
                ""
            )
        )

        saveButton.setOnClickListener {

            prefs.edit()
                .putString(
                    "SERVER_IP",
                    ipInput.text.toString()
                )
                .apply()

            ServerConfig.baseUrl =
                "http://${ipInput.text}:5000/"

            finish()
        }
    }
}