package com.example.dualcamrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var radioFront: RadioButton

    private val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Необходимо выдать разрешения!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        radioFront = findViewById(R.id.radioFront)

        if (!allPermissionsGranted()) {
            requestPermissionLauncher.launch(permissions)
        }

        btnStart.setOnClickListener {
            if (allPermissionsGranted()) {
                val useFrontCamera = radioFront.isChecked
                val serviceIntent = Intent(this, RecorderService::class.java).apply {
                    action = "ACTION_START"
                    putExtra("USE_FRONT_CAMERA", useFrontCamera)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                val camName = if (useFrontCamera) "передней" else "задней"
                statusTextView.text = "Статус: Запись с $camName камеры..."
                Toast.makeText(this, "Запуск записи", Toast.LENGTH_SHORT).show()
            } else {
                requestPermissionLauncher.launch(permissions)
            }
        }

        btnStop.setOnClickListener {
            val serviceIntent = Intent(this, RecorderService::class.java).apply {
                action = "ACTION_STOP"
            }
            startService(serviceIntent)
            statusTextView.text = "Статус: Запись остановлена"
            Toast.makeText(this, "Запись остановлена, файл сохранен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = permissions.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}
