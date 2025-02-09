package shakir.kadakkadan.home.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.net.wifi.WifiManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import shakir.kadakkadan.home.camera.Pref.lastTypedIP
import shakir.kadakkadan.home.camera.Pref.rotationAngle
import shakir.kadakkadan.home.camera.ui.theme.HomeCameraTheme
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

var port = 6868


val byteArrayQueue = ByteArrayQueue(1000)

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


        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted, request it
            requestCameraPermission();
        } else {
            // Permission is already granted, proceed with camera operations
            // ...
        }

    }

    private fun requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this@MainActivity,
                Manifest.permission.CAMERA
            )
        ) {
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

    var REQUEST_CODE_CAMERA = 73636;


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








fun byteArrayToBitmap(byteArray: ByteArray): Bitmap {
    return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
}



@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {


    var text by remember { mutableStateOf(lastTypedIP) }
    var isChannelOpen by remember { mutableStateOf(false) }
    var isServer by remember { mutableStateOf(true) }
    val bitmapState = remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    var lip = getLocalIpAddress(context)

    val infiniteTransition = rememberInfiniteTransition()
    var rotationAngle by remember { mutableStateOf(rotationAngle) }





    // Continuously update image when new data is received
    LaunchedEffect(isServer) {
        if(!isServer) {
            val imageDataFlow = flow {
                while (true) {
                    val imageData = byteArrayQueue.read() // Read next frame from queue
                    emit(imageData)
                }
            }.flowOn(Dispatchers.IO)
            imageDataFlow.collect { imageData ->
                withContext(Dispatchers.IO) {
                    if (imageData != null) {
                        val bitmap = byteArrayToBitmap(imageData!!,)
                        withContext(Dispatchers.Main) {
                            bitmapState.value = bitmap
                        }
                    }
                }
            }
        }
    }



    Column(
        modifier = Modifier
            .background(Color.Black)
            .fillMaxSize()
    ) {


        Row {
            TextField(
                value = text,
                onValueChange = { text = it; lastTypedIP = it },
                label = { Text("Enter Camera IP") },
                modifier = modifier
                    .weight(1f)
                    .padding(8.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text // Adjust keyboard type as needed
                )
            )
            Text(
                text = "My Ip ${lip}",
                color = Color.White,
                modifier = modifier
                    .padding(8.dp)
                    .weight(1f)
                    .clickable {


                    }
            )
        }


        Row {
            Text(
                text = "open Camera",
                color = Color.White,
                modifier = modifier
                    .padding(8.dp)
                    .clickable {
                        scope.launch {
                            isServer=true
                            delay(1000)
                            openCamera(textureView_CAM, context)
                            withContext(Dispatchers.IO) {
                                val server = PersistentServer(port) // Start server on port 12345
                                server.start()
                            }

                        }

                    }
            )
            Text(
                text = "Receive Data",
                color = Color.White,
                modifier = modifier
                    .padding(8.dp)
                    .clickable {
                        scope.launch {
                            isServer=false
                            withContext(Dispatchers.IO) {
                                val client = PersistentClient(text, port)
                                client.start()
                            }

                        }

                    }
            )
        }





        if(!isServer)
        Box(modifier = Modifier

            .background(color = Color.Green.copy(alpha = .1f))
            .weight(1f), contentAlignment = Alignment.Center) {
            bitmapState.value?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Live Camera Stream",
                    modifier = Modifier
                        .rotate(rotationAngle)
                        .fillMaxSize()
                )


                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Rotating Icon",
                    modifier = modifier
                        .align(alignment = Alignment.TopStart)
                        .clickable {
                            rotationAngle=(rotationAngle+90)%360
                        }
                        .rotate(rotationAngle)
                        .then(Modifier) // You can chain additional modifiers if needed.
                )


            } ?: Text("Waiting for video feed...", color = Color.White)
        }
        if(isServer)
        Box(modifier = Modifier
            .background(color = Color.Red.copy(alpha = .1f))
            .weight(1f)) {
            CameraPreview()
        }


    }
}

fun getLocalIpAddress(context: Context): String {
    val wifiMgr = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val wifiInfo = wifiMgr.connectionInfo
    val ip = wifiInfo.ipAddress

    return String.format(
        "%d.%d.%d.%d",
        (ip and 0xff), (ip shr 8 and 0xff), (ip shr 16 and 0xff), (ip shr 24 and 0xff)
    )
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HomeCameraTheme {
        Greeting("Android")
    }
}


lateinit var textureView_CAM: TextureView

@Composable
fun CameraPreview() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = {
            val textureView = TextureView(context)
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    println("onSurfaceTextureAvailable")
                    textureView_CAM = textureView

                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
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
        cameraManager.openCamera(
            cameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraD = camera
                    configureCamera(textureView, cameraD!!)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraD = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraD = null
                }
            }, null
        )
    } catch (e: CameraAccessException) {
        Log.e("Camera2", "Error opening camera", e)
    }
}
var imageReader: ImageReader?=null
private fun configureCamera(textureView: TextureView, camera: CameraDevice) {
    val surfaceTexture = textureView.surfaceTexture ?: return

    // Create an ImageReader for capturing camera frames

    imageReader = ImageReader.newInstance(500, 500, ImageFormat.JPEG,1)
    imageReader?.setOnImageAvailableListener({ reader ->
        // Handle captured image data here
        val image = reader.acquireLatestImage()

        if(image!=null) {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            byteArrayQueue.write(bytes)

//        val planes = image.planes
//        val buffer = planes.firstOrNull()?.buffer
//
//        // Convert image data to desired format (e.g., byte array)
//        val imageData = buffer?.remaining()?.let { ByteArray(it) }
//        buffer?.get(imageData)
//        println("imageData ${imageData?.size}")
//        byteArrayQueue.write(imageData!!)
            image.close()
        }
    }, Handler(Looper.getMainLooper()))


    // Create a Surface from the TextureView's SurfaceTexture
    val surface = Surface(surfaceTexture)

    // Try to create a capture session for camera preview
    try {
        val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)
        captureRequestBuilder.addTarget(imageReader!!.surface)


        camera.createCaptureSession(
            listOf(surface, imageReader!!.surface),
            object : CameraCaptureSession.StateCallback() {
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
            },
            null
        )
    } catch (e: CameraAccessException) {
        Log.e("Camera2", "Failed to create capture request", e)
    }
}

private fun closeCamera() {
    cameraD?.close()
    cameraD = null
}




fun bitmapToByteArray(
    bitmap: Bitmap,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    quality: Int = 100
): ByteArray {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(format, quality, outputStream)
    return outputStream.toByteArray()
}





class ByteArrayQueue(capacity: Int) {
    private val queue = LinkedBlockingQueue<ByteArray>(capacity)

    fun write(data: ByteArray) {
        queue.put(data) // Blocks if the queue is full
    }

    fun read(): ByteArray? {
        return queue.poll() // Returns null if queue is empty (non-blocking)
    }

    fun readAll(): List<ByteArray> {
        val dataList = mutableListOf<ByteArray>()
        queue.drainTo(dataList) // Moves all elements from queue to list
        return dataList
    }

    fun isEmpty(): Boolean = queue.isEmpty()
}





class PersistentServer(private val port: Int) {
    private val serverSocket = ServerSocket(port)
    private val executorService = Executors.newCachedThreadPool() // Thread pool for clients
    private val clientList = mutableListOf<ClientHandler>()

    fun start() {
        println("Server started on port $port")

        while (true) {
            val clientSocket = serverSocket.accept()
            println("New client connected: ${clientSocket.inetAddress.hostAddress}")

            val clientHandler = ClientHandler(clientSocket)
            synchronized(clientList) {
                clientList.add(clientHandler)
            }

            executorService.execute { clientHandler.handleClient() }
            executorService.execute { clientHandler.sendData() } // Start sending data separately
        }
    }

    inner class ClientHandler(private val socket: Socket) {
        private val input = DataInputStream(socket.getInputStream())
        private val output = DataOutputStream(socket.getOutputStream())
        private var running = true

        fun handleClient() {
            try {
                while (running) {
                    val message = input.readUTF() // Read commands without blocking writing
                    if (message == "START_CAMERA") {
                        println("SERVER WRITE LOG START_CAMERA")
                    } else if (message == "STOP_CAMERA") {
                        println("SERVER WRITE LOG STOP_CAMERA")
                    }
                }
            } catch (e: Exception) {
                println("SERVER READ LOG Client disconnected: ${socket.inetAddress.hostAddress}")
            } finally {
                println("SERVER READ LOG Client finally:")
                stop()
            }
        }

        fun sendData() {
            try {
                while (running) {
                    val dataList = byteArrayQueue.readAll()

                    if (dataList.isNotEmpty()) {
                        output.writeInt(dataList.size) // Send number of byte arrays
                        for (data in dataList) {
                            output.writeInt(data.size) // Send size of byte array
                            output.write(data) // Send byte array
                        }
                        output.flush()
                    } else {
                        output.writeInt(0) // No data available
                        output.flush()
                    }

                    println("SERVER WRITE LOG Sent ${dataList.size} byte arrays")
                }
            } catch (e: Exception) {
                println("SERVER WRITE LOG Error sending data: ${e.message}")
            } finally {
                println("SERVER WRITE LOG finally")
                stop()
            }
        }

        fun stop() {
            running = false
            synchronized(clientList) {
                clientList.remove(this)
            }
            socket.close()
        }
    }
}








class PersistentClient(
    private val serverAddress: String,
    private val port: Int,

) {

    private var isRunning = true

    fun start() {
        while (isRunning) {
            try {
                println("Connecting to server at $serverAddress:$port...")
                val socket = Socket(serverAddress, port)
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())

                println("Connected to server!")

                // Start camera (or any initial command)
                output.writeUTF("START_CAMERA")
                output.flush()

                while (isRunning) {


                    val count = input.readInt()
                    if (count > 0) {
                        println("Receiving $count byte arrays")

                        repeat(count) {
                            val size = input.readInt()
                            val data = ByteArray(size)
                            input.readFully(data) // Read entire byte array
                            byteArrayQueue.write(data)

                                //viewModel.processAndDisplayImage(data)
                        }
                    } else {
                        println("No new data available")
                    }

//                    Thread.sleep(1) // Adjust polling interval as needed
                }

                // Close connection properly when exiting
                //output.writeUTF("STOP_CAMERA")
//                output.flush()
//                socket.close()

            } catch (e: Exception) {
                e.printStackTrace()
                println("Connection lost, retrying in 5 seconds... (${e.message})")
                TimeUnit.SECONDS.sleep(1) // Wait before reconnecting
            }
        }
    }

    fun stop() {
        isRunning = false
    }
}













fun imageToBitmap(image: Image): Bitmap {
    val nv21 = yuv420888ToNv21(image)
    return nv21ToBitmap(nv21, image.width, image.height)
}



fun yuv420888ToNv21(image: Image): ByteArray {
    val width = image.width
    val height = image.height
    val ySize = width * height
    val uvSize = width * height / 2  // NV21 stores UV interleaved in half the size of Y
    val nv21 = ByteArray(ySize + uvSize)

    // Copy Y plane.
    image.planes[0].buffer.get(nv21, 0, ySize)

    // Retrieve U and V planes.
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val uRowStride = image.planes[1].rowStride
    val vRowStride = image.planes[2].rowStride
    val uPixelStride = image.planes[1].pixelStride
    val vPixelStride = image.planes[2].pixelStride

    var offset = ySize
    val uvWidth = width / 2
    val uvHeight = height / 2

    // Iterate over the UV planes and interleave V and U bytes.
    for (i in 0 until uvHeight) {
        for (j in 0 until uvWidth) {
            // Compute the index in the U and V buffers.
            val uIndex = i * uRowStride + j * uPixelStride
            val vIndex = i * vRowStride + j * vPixelStride

            // In NV21, V comes first then U.
            nv21[offset++] = vBuffer.get(vIndex)
            nv21[offset++] = uBuffer.get(uIndex)
        }
    }
    return nv21
}


fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap {
    val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    // Compress the YuvImage to JPEG. Quality can be adjusted as needed.
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val jpegByteArray = out.toByteArray()
    return BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.size)
}

