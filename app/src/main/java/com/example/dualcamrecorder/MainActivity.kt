package com.example.dualcamrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
    private val REQUIRED_PERMISSIONS = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

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
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
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
                Toast.makeText(this, "Camera and Mic permissions required!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleRecording() {
        val serviceIntent = Intent(this, RecorderService::class.java)
        
        isRecording = !isRecording
        if (isRecording) {
            btnRecord.text = "Stop Background Recording"
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Starting background service...", Toast.LENGTH_SHORT).show()
        } else {
            btnRecord.text = "Start Background Recording"
            stopService(serviceIntent)
            Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
        }
    }
}
