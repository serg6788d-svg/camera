package com.example.dualcamrecorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: Button
    private var isRecording = false

    private val PERMISSIONS_REQUEST_CODE = 100
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRecord = findViewById(R.id.btnRecord)

        btnRecord.setOnClickListener {
            if (checkPermissions()) {
                toggleRecording()
            } else {
                requestPermissions()
            }
        }
    }

    private fun checkPermissions(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (checkPermissions()) {
                toggleRecording()
            } else {
                Toast.makeText(this, "Требуются разрешения для камеры и микрофона!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        if (isRecording) {
            btnRecord.text = "Остановить запись"
            Toast.makeText(this, "Запуск камер и звука...", Toast.LENGTH_SHORT).show()
        } else {
            btnRecord.text = "Начать запись"
            Toast.makeText(this, "Запись сохранена", Toast.LENGTH_SHORT).show()
        }
    }
}
