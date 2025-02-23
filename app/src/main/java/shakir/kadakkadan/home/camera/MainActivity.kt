package shakir.kadakkadan.home.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.media.Image
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import shakir.kadakkadan.home.camera.Pref.lastTypedIP
import shakir.kadakkadan.home.camera.ui.theme.HomeCameraTheme
import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue

var client: PersistentClient?=null

var port = 6868

// var textureView_CAM: TextureView?=null
 var isScreenOnResumeState: Boolean=false;



val byteArrayQueue = ByteArrayQueue(10000)
var  lastSentTimeStamp= 0L
var  lastDataSize= 0

class MainActivity : ComponentActivity() {

    private fun startForegroundService() {
//        // Check for notification permission on Android 13 and above
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
//                PackageManager.PERMISSION_GRANTED
//            ) {
//                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
//                return
//            }
//        }

        val serviceIntent = Intent(this, ExampleForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        enableEdgeToEdge()
        setContent {
            HomeCameraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainView(
                        name = "Android", modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }









    }



    var REQUEST_CODE_CAMERA = 73636;


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray, deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        recreate()

    }

    @Composable
    fun MainView(name: String, modifier: Modifier = Modifier) {


        var text by remember { mutableStateOf(lastTypedIP) }
        var isChannelOpen by remember { mutableStateOf(false) }
        var isServer by remember { mutableStateOf(Pref.isServer) }
        var isServerClientSelected by remember { mutableStateOf(Pref.isServerClientSelected) }
        val bitmapState = remember { mutableStateOf<Bitmap?>(null) }

        val scope = rememberCoroutineScope()

        val context = LocalContext.current
        val textureView = remember { TextureView(context) }
        val lifecycleOwner = LocalLifecycleOwner.current
        var lip = getLocalIpAddress(context)

        val infiniteTransition = rememberInfiniteTransition()


        var surface by remember { mutableStateOf<Surface?>(null) }
        var decoder by remember { mutableStateOf<VideoDecoder?>(null) }

        var surfaceRestartKey by remember { mutableStateOf(0) }




        // Continuously update image when new data is received
        fun stopAll(){


            try {
                byteArrayQueue.clear()
                sendOn=false;
            } catch (e: Exception) {
               e.printStackTrace()
            }

            try {
                closeCamera()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                server?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }




            try {
                val serviceIntent = Intent(context, ExampleForegroundService::class.java)
                stopService(serviceIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                client?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }




        }

        suspend fun onChangeClientServer(askPermission:Boolean=false){
            if(isServerClientSelected) {
                surfaceRestartKey++
                if (isServer) {
                    stopAll()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        if (askPermission)
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                        else {
                            Toast.makeText(
                                this,
                                "Notification permission is required for this app to function.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return
                    }
                    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        if (askPermission) {
                            requestPermissions(arrayOf(Manifest.permission.CAMERA), 2)
                        } else {
                            Toast.makeText(
                                this,
                                "Camera permission is required for this app to function.",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        return
                    }

                    delay(2000L)
                    startForegroundService()

                } else if (!isServer && isScreenOnResumeState) {
                    stopAll()
                    delay(2000L)
                    withContext(Dispatchers.IO) {
                        client = PersistentClient(text, port)
                        client?.start()
                    }

                } else if (!isServer && !isScreenOnResumeState) {
                    stopAll()
                }
            }

        }




        DisposableEffect(key1 = lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
//                closeCamera()
                }
                if (event == Lifecycle.Event.ON_RESUME) {
                    isScreenOnResumeState=true;
                    scope.launch {
                        onChangeClientServer()
                    }
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    isScreenOnResumeState=false;
                    if(!isServer){
                        stopAll()
                    }
                }



            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
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
                Text(text = "My Ip ${lip}",
                    color = Color.White,
                    modifier = modifier
                        .padding(8.dp)
                        .weight(1f)
                        .clickable {


                        })
            }


            Row(modifier = Modifier.padding(16.dp)) {
                Text(text = "Use as a : ", modifier = Modifier.padding(start = 8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .clickable {
                        Pref.isServerClientSelected=true
                        isServerClientSelected=true
                        isServer = true
                        Pref.isServer = isServer
                        scope.launch {
                            onChangeClientServer(askPermission = true)
                        }

                    }
                    .alpha(if (isServer&&isServerClientSelected) 1f else .3f)) {
                    Image(
                        painter = painterResource(id = R.drawable.baseline_camera_alt_24),
                        contentDescription = "Example Image",
                        modifier = Modifier.size(25.dp),
                        colorFilter = ColorFilter.tint(Color.White)

                    )
                    Text(text = "Camera", modifier = Modifier.padding(start = 8.dp))

                }

                Spacer(modifier = Modifier.width(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically,modifier = Modifier
                    .clickable {
                        Pref.isServerClientSelected=true
                        isServerClientSelected=true
                        isServer = false
                        Pref.isServer = false  // Upd
                        scope.launch {
                            onChangeClientServer()
                        }
                    }
                    .alpha(if (!isServer&&isServerClientSelected) 1f else .3f)) {
                    Image(
                        painter = painterResource(id = R.drawable.baseline_monitor_24),
                        contentDescription = "Example Image",
                        modifier = Modifier.size(25.dp),
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                    Text(text = "Monitor", modifier = Modifier.padding(start = 8.dp))
                }
            }










            VideoPlayerScreen(key=  surfaceRestartKey,::stopAll)



//            if (false) Box(
//                modifier = Modifier
//
//                    .background(color = Color.Green.copy(alpha = .1f))
//                    .fillMaxSize(),
//                contentAlignment = Alignment.Center
//            ) {
//                bitmapState.value?.let { bitmap ->
//                    Image(
//                        bitmap = bitmap.asImageBitmap(),
//                        contentDescription = "Live Camera Stream",
//                        modifier = Modifier
//                            .rotate(rotationAngle)
//                            .fillMaxSize()
//                    )
//
//
//                    Icon(imageVector = Icons.Default.Refresh,
//                        contentDescription = "Rotating Icon",
//                        modifier = modifier
//                            .align(alignment = Alignment.TopStart)
//                            .clickable {
//                                rotationAngle = (rotationAngle + 90) % 360
//                                Pref.rotationAngle=rotationAngle
//                            }
//                            .rotate(rotationAngle)
//                            .then(Modifier) // You can chain additional modifiers if needed.
//                    )
//
//
//                } ?: Text("Waiting for video feed...", color = Color.White)
//            }
//        if (true) Box(
//            modifier = Modifier
//                .background(color = Color.Red.copy(alpha = .1f))
//                .weight(1f)
//        ) {
//            CameraPreview()
//        }


        }
    }





}


fun byteArrayToBitmap(byteArray: ByteArray): Bitmap {
    return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
}




fun getLocalIpAddress(context: Context): String {
    val wifiMgr = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val wifiInfo = wifiMgr.connectionInfo
    val ip = wifiInfo.ipAddress

    return String.format(
        "%d.%d.%d.%d",
        (ip and 0xff),
        (ip shr 8 and 0xff),
        (ip shr 16 and 0xff),
        (ip shr 24 and 0xff)
    )
}





//
//@Composable
//fun CameraPreview() {
//    val context = LocalContext.current
//    val lifecycleOwner = LocalLifecycleOwner.current
//
//    AndroidView(
//        factory = {
//            val textureView = TextureView(context)
//            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
//                override fun onSurfaceTextureAvailable(
//                    surface: SurfaceTexture, width: Int, height: Int
//                ) {
//                    println("onSurfaceTextureAvailable")
////                    textureView_CAM = textureView
//
//                }
//
//                override fun onSurfaceTextureSizeChanged(
//                    surface: SurfaceTexture, width: Int, height: Int
//                ) {
//                    println("onSurfaceTextureSizeChanged")
//                }
//
//                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
//                    println("onSurfaceTextureDestroyed")
//                    return true
//                }
//
//                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
//                    println("onSurfaceTextureUpdated")
//                }
//            }
//            textureView
//        }, modifier = Modifier.fillMaxSize()
//    )
//
//    DisposableEffect(key1 = lifecycleOwner) {
//        val observer = LifecycleEventObserver { _, event ->
//            if (event == Lifecycle.Event.ON_DESTROY) {
////                closeCamera()
//            }
//             if (event == Lifecycle.Event.ON_RESUME) {
//                 textureView_CAMOn=true;
//            }
//              if (event == Lifecycle.Event.ON_PAUSE) {
//                  textureView_CAMOn=false;
//            }
//
//
//
//        }
//        lifecycleOwner.lifecycle.addObserver(observer)
//        onDispose {
//            lifecycleOwner.lifecycle.removeObserver(observer)
//        }
//    }
//}
//





fun bitmapToByteArray(
    bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 100
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
    fun clear() {
        queue.clear()
    }

    fun isEmpty(): Boolean = queue.isEmpty()
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





class VideoDecoder(private val surface: Surface) {
    private val mediaCodec: MediaCodec = MediaCodec.createDecoderByType("video/avc")

    init {
        val format = MediaFormat.createVideoFormat("video/avc", 640, 360) // Match encoding resolution
        format.setInteger(MediaFormat.KEY_ROTATION, Pref.rotationAngle.toInt())
        mediaCodec.configure(format, surface, null, 0) // Decoder mode
        mediaCodec.start()
    }

    fun decodeH264(data: ByteArray) {
        val inputIndex = mediaCodec.dequeueInputBuffer(10000)
        if (inputIndex >= 0) {
            val inputBuffer = mediaCodec.getInputBuffer(inputIndex)
            inputBuffer?.clear()
            inputBuffer?.put(data)
            mediaCodec.queueInputBuffer(inputIndex, 0, data.size, System.nanoTime(), 0)
        }

        // ✅ Handle output buffer to render frames
        val bufferInfo = MediaCodec.BufferInfo()
        var outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
        while (outputIndex >= 0) {
            mediaCodec.releaseOutputBuffer(outputIndex, true) // ✅ Render to Surface
            outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
        }
    }

    fun release() {
        mediaCodec.stop()
        mediaCodec.release()
    }
}



@Composable
fun VideoPlayerScreen(key:Int,kSuspendFunction0: suspend () -> Unit) {
    var surface by remember { mutableStateOf<Surface?>(null) }
    var decoder by remember { mutableStateOf<VideoDecoder?>(null) }
    val scope = rememberCoroutineScope()
    val status = remember { mutableStateOf("") }
    var rotationAngle by remember { mutableStateOf( Pref.rotationAngle) }
    val context = LocalContext.current


    Column(modifier = Modifier.fillMaxSize()) {
        Row {
            Text(
                text = "status ${status.value}",
                color = Color.White,
            )
                                Icon(imageVector = Icons.Default.Refresh,
                        contentDescription = "Rotating Icon",
                        modifier = Modifier
                            .clickable {
                                rotationAngle = (rotationAngle + 90) % 360
                                Pref.rotationAngle=rotationAngle
                               scope.launch {
                                   kSuspendFunction0.invoke()
                                   (context as Activity).recreate()

                               }



                            }
                            .rotate(rotationAngle)
                            .then(Modifier) // You can chain additional modifiers if needed.
                    )
        }
        AndroidView(modifier = Modifier.fillMaxSize(),factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        surface = Surface(surfaceTexture)
                        decoder =
                            VideoDecoder(surface!!) // ✅ Initialize decoder when Surface is ready
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        })
    }


    LaunchedEffect(decoder) {


            val imageDataFlow = flow {
                while (true) {
                    val imageData = byteArrayQueue.read() // Read next frame from queue
                    emit(imageData)
                }
            }.flowOn(Dispatchers.IO)
            imageDataFlow.collect { imageData ->
                withContext(Dispatchers.IO) {
                    if (imageData != null) {

                        if (imageData.isNotEmpty()) {
                            status.value=imageData.size.toString()
                            decoder?.decodeH264(imageData) //
                        } else {
                            status.value=imageData.size.toString()
                        }

                    }
                }
            }
        }
    }











