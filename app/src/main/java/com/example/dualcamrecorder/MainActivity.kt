package com.example.dualcamrecorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.AdapterView
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

    private lateinit var textureView1: TextureView
    private lateinit var textureView2: TextureView
    private lateinit var btnRecord: Button
    private lateinit var spinnerMode: Spinner
    private lateinit var spinnerQuality: Spinner

    private lateinit var cameraManager: CameraManager
    private var cameraDevice1: CameraDevice? = null
    private var cameraDevice2: CameraDevice? = null

    private var captureSession1: CameraCaptureSession? = null
    private var captureSession2: CameraCaptureSession? = null

    private var mediaRecorder1: MediaRecorder? = null
    private var mediaRecorder2: MediaRecorder? = null

    private var isRecording = false
    private var backCameraId: String? = null
    private var frontCameraId: String? = null

    private val REQUEST_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView1 = findViewById(R.id.textureView1)
        textureView2 = findViewById(R.id.textureView2)
        btnRecord = findViewById(R.id.btnRecord)
        spinnerMode = findViewById(R.id.spinnerMode)
        spinnerQuality = findViewById(R.id.spinnerQuality)

        val modes = arrayOf("Только задняя камера", "Только передняя камера", "Обе камеры (Одновременно)")
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)
        spinnerMode.adapter = modeAdapter

        val qualities = arrayOf("HD (720p - Оптимально)", "Full HD (1080p - Высокое)")
        val qualityAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, qualities)
        spinnerQuality.adapter = qualityAdapter

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        detectCameras()

        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (!isRecording) {
                    updatePreviewLayout()
                    openCameras()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

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
        if (checkPermissions()) {
            updatePreviewLayout()
            openCameras()
        } else {
            requestPermissions()
        }
    }

    override fun onPause() {
        super.onPause()
        closeCameras()
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

    private fun updatePreviewLayout() {
        val selectedMode = spinnerMode.selectedItemPosition
        if (selectedMode == 2) {
            textureView1.visibility = View.VISIBLE
            textureView2.visibility = View.VISIBLE
        } else {
            textureView1.visibility = View.VISIBLE
            textureView2.visibility = View.GONE
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

    private fun openCameras() {
        closeCameras()
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            val selectedMode = spinnerMode.selectedItemPosition

            if (selectedMode == 0 || selectedMode == 2) {
                backCameraId?.let { id ->
                    cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            cameraDevice1 = camera
                            startPreview(cameraDevice1, textureView1, 1)
                        }
                        override fun onDisconnected(camera: CameraDevice) { camera.close() }
                        override fun onError(camera: CameraDevice, error: Int) { camera.close() }
                    }, null)
                }
            }

            if (selectedMode == 1 || selectedMode == 2) {
                val targetId = if (selectedMode == 1) frontCameraId ?: backCameraId else frontCameraId
                targetId?.let { id ->
                    cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            if (selectedMode == 1) {
                                cameraDevice1 = camera
                                startPreview(cameraDevice1, textureView1, 1)
                            } else {
                                cameraDevice2 = camera
                                startPreview(cameraDevice2, textureView2, 2)
                            }
                        }
                        override fun onDisconnected(camera: CameraDevice) { camera.close() }
                        override fun onError(camera: CameraDevice, error: Int) { camera.close() }
                    }, null)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPreview(cameraDevice: CameraDevice?, textureView: TextureView, id: Int) {
        if (cameraDevice == null || !textureView.isAvailable) return
        try {
            val texture = textureView.surfaceTexture ?: return
            texture.setDefaultBufferSize(1280, 720)
            val surface = Surface(texture)

            val builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)

            cameraDevice.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {}
                override fun onConfigured(session: CameraCaptureSession) {
                    if (id == 1) captureSession1 = session else captureSession2 = session
                    try {
                        session.setRepeatingRequest(builder.build(), null, null)
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startRecording() {
        try {
            val selectedMode = spinnerMode.selectedItemPosition
            val isFullHD = spinnerQuality.selectedItemPosition == 1
            val width = if (isFullHD) 1920 else 1280
            val height = if (isFullHD) 1080 else 720
            val bitrate = if (isFullHD) 15000000 else 8000000

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (!dir.exists()) dir.mkdirs()

            val file1 = File(dir, "Cam1_${timeStamp}.mp4")

            if (selectedMode == 2) {
                val file2 = File(dir, "Cam2_${timeStamp}.mp4")

                mediaRecorder1 = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(file1.absolutePath)
                    setVideoEncodingBitRate(bitrate)
                    setVideoFrameRate(30)
                    setVideoSize(width, height)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    prepare()
                }

                mediaRecorder2 = MediaRecorder().apply {
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(file2.absolutePath)
                    setVideoEncodingBitRate(bitrate)
                    setVideoFrameRate(30)
                    setVideoSize(width, height)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    prepare()
                }

                mediaRecorder1?.start()
                mediaRecorder2?.start()

                startRecordingSession(cameraDevice1, captureSession1, textureView1, mediaRecorder1?.surface, width, height, 1)
                startRecordingSession(cameraDevice2, captureSession2, textureView2, mediaRecorder2?.surface, width, height, 2)

            } else {
                mediaRecorder1 = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(file1.absolutePath)
                    setVideoEncodingBitRate(bitrate)
                    setVideoFrameRate(30)
                    setVideoSize(width, height)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    prepare()
                }

                mediaRecorder1?.start()
                startRecordingSession(cameraDevice1, captureSession1, textureView1, mediaRecorder1?.surface, width, height, 1)
            }

            isRecording = true
            spinnerMode.isEnabled = false
            spinnerQuality.isEnabled = false
            btnRecord.text = "Остановить запись"
            Toast.makeText(this, "Запись началась! Файлы в Movies", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка записи: ${e.message}", Toast.LENGTH_LONG).show()
            stopRecording()
        }
    }

    private fun startRecordingSession(cameraDevice: CameraDevice?, session: CameraCaptureSession?, textureView: TextureView, recorderSurface: Surface?, width: Int, height: Int, id: Int) {
        if (cameraDevice == null || recorderSurface == null) return
        try {
            session?.stopRepeating()
            session?.close()

            val texture = textureView.surfaceTexture ?: return
            texture.setDefaultBufferSize(width, height)
            val previewSurface = Surface(texture)

            val builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(previewSurface)
                addTarget(recorderSurface)
            }

            cameraDevice.createCaptureSession(listOf(previewSurface, recorderSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(camSession: CameraCaptureSession) {
                    if (id == 1) captureSession1 = camSession else captureSession2 = camSession
                    try {
                        camSession.setRepeatingRequest(builder.build(), null, null)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                override fun onConfigureFailed(camSession: CameraCaptureSession) {}
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        try {
            try { mediaRecorder1?.stop() } catch (e: Exception) { e.printStackTrace() }
            try { mediaRecorder1?.reset() } catch (e: Exception) { e.printStackTrace() }
            try { mediaRecorder1?.release() } catch (e: Exception) { e.printStackTrace() }
            mediaRecorder1 = null

            try { mediaRecorder2?.stop() } catch (e: Exception) { e.printStackTrace() }
            try { mediaRecorder2?.reset() } catch (e: Exception) { e.printStackTrace() }
            try { mediaRecorder2?.release() } catch (e: Exception) { e.printStackTrace() }
            mediaRecorder2 = null

        } finally {
            isRecording = false
            spinnerMode.isEnabled = true
            spinnerQuality.isEnabled = true
            btnRecord.text = "Начать запись"
            Toast.makeText(this, "Запись сохранена в папку Movies", Toast.LENGTH_LONG).show()

            openCameras()
        }
    }

    private fun closeCameras() {
        try {
            captureSession1?.close()
            captureSession1 = null
            cameraDevice1?.close()
            cameraDevice1 = null

            captureSession2?.close()
            captureSession2 = null
            cameraDevice2?.close()
            cameraDevice2 = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
