package shakir.kadakkadan.home.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.net.wifi.WifiManager
import android.opengl.GLES20
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import shakir.kadakkadan.home.camera.ui.theme.HomeCameraTheme
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.IntBuffer
import kotlin.concurrent.thread


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeCameraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            requestCameraPermission();
        } else {
            // Permission is already granted, proceed with camera operations
            // ...
        }

    }

    private fun requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity, Manifest.permission.CAMERA)) {
            // Show an explanation to the user *asynchronously* -
            // but *only* if the user has previously denied the permission
            Toast.makeText(
                this,
                "Camera permission is required for this app to function.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(
                this,
                arrayOf<String>(Manifest.permission.CAMERA),
                REQUEST_CODE_CAMERA
            )
        }
    }

var REQUEST_CODE_CAMERA=73636;


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        if (requestCode == REQUEST_CODE_CAMERA) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with camera operations
                // ...
            } else {
                // Permission denied, explain to the user why you need this permission
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
            }
        }



    }




}

var socket: Socket?=null  // Replace with the server's IP address and port


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }

    val context = LocalContext.current
    var lip=getLocalIpAddress(context)
    Column(modifier = Modifier
        .background(Color.Black)
        .fillMaxSize()) {


        Row {
            Text(
                text = "open Camera",
                color = Color.White,
                modifier = modifier
                    .padding(8.dp)
                    .clickable {

                        thread {
                            socket = Socket(text, 6767)
                            println("socket ${socket?.isBound}")
                            println("socket ${socket?.isClosed}")
                            println("socket ${socket?.isConnected}")
                        }


                        openCamera(textureView_CAM, context)
                    }
            )

            Text(
                text = "ip ${lip}",
                color = Color.White,
                modifier = modifier
                    .padding(8.dp)
                    .clickable {


                    }
            )
        }

        Row {
            Text(
                text = "Receive Data",
                color = Color.White,
                modifier = modifier
                    .padding(8.dp)
                    .clickable {
                        starServer()
                    }
            )
            TextField(
                value = text,
                onValueChange = { text=it },
                label = { Text("IP") },
                modifier = modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text // Adjust keyboard type as needed
                )
            )

        }

        Box(modifier = Modifier.weight(1f)) {
            NetPreview()
        }
        Box(modifier = Modifier.weight(1f)) {
            CameraPreview()
        }


    }
}

fun getLocalIpAddress(context: Context): String {
    val wifiMgr = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val wifiInfo = wifiMgr.connectionInfo
    val ip = wifiInfo.ipAddress

    return String.format("%d.%d.%d.%d",
        (ip and 0xff), (ip shr 8 and 0xff), (ip shr 16 and 0xff), (ip shr 24 and 0xff))
}



@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HomeCameraTheme {
        Greeting("Android")
    }
}



lateinit var textureView_CAM:TextureView
@Composable
fun CameraPreview() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = {
            val textureView = TextureView(context)
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    println("onSurfaceTextureAvailable")
                    textureView_CAM=textureView

                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    println("onSurfaceTextureSizeChanged")
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    println("onSurfaceTextureDestroyed")
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    println("onSurfaceTextureUpdated")
                }
            }
            textureView
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                closeCamera()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

var  net_surface:SurfaceTexture?=null
var  net_textureView:TextureView?=null
@Composable
fun NetPreview() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = {
            val textureView = TextureView(context)
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    net_surface=surface
                    net_textureView=textureView
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    println("onSurfaceTextureSizeChanged")
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    println("onSurfaceTextureDestroyed")
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    println("onSurfaceTextureUpdated")
                }
            }
            textureView
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                closeCamera()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

private var cameraD: CameraDevice? = null

private fun openCamera(textureView: TextureView, context: Context) {

    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    try {
        val cameraId = cameraManager.cameraIdList[0] // Get the first available camera

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        cameraManager.openCamera(cameraId,
        object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraD = camera
                configureCamera(textureView,cameraD!!)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraD= null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                cameraD = null
            }
        }, null)
    } catch (e: CameraAccessException) {
        Log.e("Camera2", "Error opening camera", e)
    }
}

private fun configureCamera(textureView: TextureView, camera: CameraDevice) {
    val surfaceTexture = textureView.surfaceTexture ?: return

    // Create an ImageReader for capturing camera frames
    val imageReader = ImageReader.newInstance(500, 500, ImageFormat.YUV_420_888, /*maxImages*/ 2)
    imageReader.setOnImageAvailableListener({ reader ->
        // Handle captured image data here
        val image = reader.acquireLatestImage()
        val planes = image.planes
        val buffer = planes.firstOrNull()?.buffer

        // Convert image data to desired format (e.g., byte array)
        val imageData = buffer?.remaining()?.let { ByteArray(it) }
        buffer?.get(imageData)
        println("imageData ${imageData?.size}")

        val outputStream = socket?.getOutputStream()
        outputStream?.write(imageData)
        outputStream?.flush()


        image.close()
    }, null)


    // Create a Surface from the TextureView's SurfaceTexture
    val surface = Surface(surfaceTexture)

    // Try to create a capture session for camera preview
    try {
        val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)
        captureRequestBuilder.addTarget(imageReader.surface)


        camera.createCaptureSession(listOf(surface,imageReader.surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                try {
                    session.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                } catch (e: CameraAccessException) {
                    Log.e("Camera2", "Failed to set repeating request", e)
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e("Camera2", "Failed to configure capture session")
            }
        }, null)
    } catch (e: CameraAccessException) {
        Log.e("Camera2", "Failed to create capture request", e)
    }
}

private fun closeCamera() {
    cameraD?.close()
    cameraD = null
}


private var textureId: Int = 0
private fun processAndDisplayImage(imageData: ByteArray) {
    // 1. Create an OpenGL ES context (if not already created)
    if (textureId == 0) {
        val textures = IntBuffer.allocate(1)
        GLES20.glGenTextures(1, textures)
        textureId = textures.get(0)
    }

    // 2. Bind the texture
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

    // 3. Load the image data into OpenGL textures
    //    (This may involve YUV to RGB conversion, depending on the image format)
    //    Here's a simplified example assuming you have a function to convert YUV to RGB
    //    (You'll need to implement this conversion function)
    val rgbData = convertYUVtoRGB(imageData, 400, 400)
    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, 400, 400,
        0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, ByteBuffer.wrap(rgbData))

    // 4. Render the image to the SurfaceTexture
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    GLES20.glEnableVertexAttribArray(0);

    // ... (Your OpenGL drawing commands to render a textured quad) ...

    // 5. Update the SurfaceTexture
    net_surface?.updateTexImage()

    // 6. Signal the TextureView to redraw
    //net_textureView?.requestRender()

    // 7. Unbind the texture
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
}

private fun convertYUVtoRGB(yuv: ByteArray, width: Int, height: Int): ByteArray {
    val rgb = ByteArray(width * height * 3) // RGB
    // ... (Implement YUV to RGB conversion logic here) ...
    return rgb
}


fun starServer(){
    thread {
        val serverSocket = ServerSocket(6767) // Replace with your desired port
        println("Server started on port 6767")
        while (true) {
            val clientSocket = serverSocket.accept()
            println("New client connected")
            handleClient(clientSocket)
            clientSocket.close()
        }
    }
}

fun handleClient(clientSocket: Socket) {
    try {
        val inputStream = clientSocket.getInputStream()


        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(1024) // Adjust chunk size as needed
        var bytesRead: Int

        while ((inputStream.read(chunk).also { bytesRead = it }) != -1) {
            buffer.write(chunk, 0, bytesRead)
        }
        val toByteArray = buffer.toByteArray()
        println("toByteArray ${toByteArray.size}")


    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        clientSocket.close()
    }
}



















