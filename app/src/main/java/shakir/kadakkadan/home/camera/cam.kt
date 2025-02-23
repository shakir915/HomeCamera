package shakir.kadakkadan.home.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

private var cameraD: CameraDevice? = null

 fun openCamera( context: Context) {
     byteArrayQueue.clear()

    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    try {
        val cameraId = cameraManager.cameraIdList[0] // Get the first available camera

        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        cameraManager.openCamera(
            cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    println("openCamera onOpened")
                    cameraD = camera
                    configureCamera(cameraD!!)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    println("openCamera onDisconnected")
                    camera.close()
                    cameraD = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    println("openCamera onError")
                    camera.close()
                    cameraD = null
                }
            }, null
        )
    } catch (e: CameraAccessException) {
        e.printStackTrace()
        Log.e("Camera2", "Error opening camera", e)
    }
}


var imageReader: ImageReader? = null
var mediaCodec: MediaCodec?=null
var sendOn=false;

private fun configureCamera(camera: CameraDevice) {

    try {
        mediaCodec?.stop()
    } catch (e: Exception) {
       e.printStackTrace()
    }

    mediaCodec = MediaCodec.createEncoderByType("video/avc")

    val format = MediaFormat.createVideoFormat("video/avc", 640, 360).apply { // Lower resolution
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, 500_000) // Lower bitrate (500 kbps)
        setInteger(MediaFormat.KEY_FRAME_RATE, 15) // Lower frame rate (15 FPS)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5) // Increase keyframe interval (5 sec)
    }

    mediaCodec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    val codecSurface = mediaCodec!!.createInputSurface()
    mediaCodec!!.start()

    val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
    captureRequestBuilder.addTarget(codecSurface) // Stream only, no recording

    camera.createCaptureSession(
        listOf(codecSurface),
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                session.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                //startStream(mediaCodec!!) // Stream video
                sendOn=true;
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e("Camera2", "Failed to configure capture session")
            }
        },
        null
    )
    println("configureCamera")

}

private fun startStream(mediaCodec: MediaCodec) {
    println("startStream")
    Thread {
        try {
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                val outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputIndex >= 0) {
                    val outputBuffer = mediaCodec.getOutputBuffer(outputIndex)
                    val bytes = ByteArray(bufferInfo.size)
                    outputBuffer?.get(bytes)
//                    byteArrayQueue.
//                    GlobalScope.launch {
//                        server?.broadcastData(bytes)
//                    }
                    mediaCodec.releaseOutputBuffer(outputIndex, false)
                }
            }
        } catch (e: Exception) {
           e.printStackTrace()
        }
    }.start()
}

fun closeCamera() {
    Throwable().printStackTrace()
    println("closeCamera")
    try {
        mediaCodec?.stop()
    } catch (e: Exception) {
       e.printStackTrace()
    }

    try {
        imageReader?.close()
        imageReader = null
    } catch (e: Exception) {
       e.printStackTrace()
    }

    cameraD?.close()
    cameraD = null
    println("closeCamera $cameraD")
}



fun encodeFrame(data: ByteArray, mediaCodec: MediaCodec) {
    val inputIndex = mediaCodec.dequeueInputBuffer(10000)
    if (inputIndex >= 0) {
        val inputBuffer = mediaCodec.getInputBuffer(inputIndex)
        inputBuffer?.clear()
        inputBuffer?.put(data)
        mediaCodec.queueInputBuffer(inputIndex, 0, data.size, System.nanoTime() / 1000, 0)
    }

    val bufferInfo = MediaCodec.BufferInfo()
    val outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)

    if (outputIndex >= 0) {
        val encodedData = mediaCodec.getOutputBuffer(outputIndex)
        val outBytes = ByteArray(bufferInfo.size)
        encodedData?.get(outBytes)
        byteArrayQueue.write(outBytes)
        mediaCodec.releaseOutputBuffer(outputIndex, false)
    }
}



