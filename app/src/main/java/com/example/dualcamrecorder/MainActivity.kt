package com.example.dualcamrecorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: Button
    private lateinit var spinnerCamera: Spinner
    private lateinit var spinnerQuality: Spinner

    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null

    private var isRecording = false
    private var backCameraId: String? = null
    private var frontCameraId: String? = null

    private val REQUEST_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRecord = findViewById(R.id.btnRecord)
        spinnerCamera = findViewById(R.id.spinnerCamera)
        spinnerQuality = findViewById(R.id.spinnerQuality)

        val cameras = arrayOf("Задняя камера", "Передняя камера")
        spinnerCamera.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cameras)

        val qualities = arrayOf("HD (720p - Оптимально)", "Full HD (1080p - Высокое)")
        spinnerQuality.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, qualities)

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        detectCameras()

        btnRecord.setOnClickListener {
            if (checkPermissions()) {
                if (!isRecording) {
                    startRecording()
                } else {
                    stopRecording()
                }
            } else {
                requestPermissions()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!checkPermissions()) {
            requestPermissions()
        }
    }

    override fun onPause() {
        super.onPause()
        closeCamera()
    }

    private fun detectCameras() {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameraId = id
                } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = id
                }
            }
            val list = cameraManager.cameraIdList
            if (backCameraId == null && list.isNotEmpty()) backCameraId = list[0]
            if (frontCameraId == null && list.size > 1) frontCameraId = list[1] else frontCameraId = backCameraId
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkPermissions(): Boolean {
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return cam == PackageManager.PERMISSION_GRANTED && mic == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
    }

    private fun startRecording() {
        try {
            val isFront = spinnerCamera.selectedItemPosition == 1
            val targetCameraId = if (isFront) frontCameraId ?: "1" else backCameraId ?: "0"

            val isFullHD = spinnerQuality.selectedItemPosition == 1
            val width = if (isFullHD) 1920 else 1280
            val height = if (isFullHD) 1080 else 720
            val bitrate = if (isFullHD) 15000000 else 8000000

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (!dir.exists()) dir.mkdirs()

            val videoFile = File(dir, "Record_${if (isFront) "Front" else "Back"}_${timeStamp}.mp4")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(videoFile.absolutePath)
                setVideoEncodingBitRate(bitrate)
                setVideoFrameRate(30)
                setVideoSize(width, height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                prepare()
            }

            val recorderSurface = mediaRecorder?.surface ?: throw IllegalStateException("MediaRecorder surface is null")

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            cameraManager.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    try {
                        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                            addTarget(recorderSurface)
                        }

                        camera.createCaptureSession(listOf(recorderSurface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                try {
                                    session.setRepeatingRequest(builder.build(), null, null)
                                    mediaRecorder?.start()
                                    isRecording = true
                                    runOnUiThread {
                                        spinnerCamera.isEnabled = false
                                        spinnerQuality.isEnabled = false
                                        btnRecord.text = "Остановить запись"
                                        Toast.makeText(this@MainActivity, "Запись началась в фоне", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Toast.makeText(this@MainActivity, "Ошибка конфигурации камеры", Toast.LENGTH_SHORT).show()
                            }
                        }, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, null)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка записи: ${e.message}", Toast.LENGTH_LONG).show()
            stopRecording()
        }
    }

    private fun stopRecording() {
        try {
            try { mediaRecorder?.stop() } catch (e: Exception) { e.printStackTrace() }
            try { mediaRecorder?.reset() } catch (e: Exception) { e.printStackTrace() }
            try { mediaRecorder?.release() } catch (e: Exception) { e.printStackTrace() }
            mediaRecorder = null

            closeCamera()
        } finally {
            isRecording = false
            spinnerCamera.isEnabled = true
            spinnerQuality.isEnabled = true
            btnRecord.text = "Начать запись"
            Toast.makeText(this, "Запись сохранена в папку Movies", Toast.LENGTH_LONG).show()
        }
    }

    private fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
