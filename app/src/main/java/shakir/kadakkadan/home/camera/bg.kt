package shakir.kadakkadan.home.camera

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

var server: PersistentServer?=null



// ExampleForegroundService.kt
class ExampleForegroundService : Service() {
    private val CHANNEL_ID = "ForegroundServiceChannel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Create notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Service")
            .setContentText("Camera Service is running...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .build()

        // Start foreground service
        startForeground(NOTIFICATION_ID, notification)

        // Do your background work here
        doWork()

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun doWork() {
        // Background work goes here


        GlobalScope.launch {

//            GlobalScope.launch {
//                withContext(Dispatchers.Main){
//                    openCamera(this@ExampleForegroundService)
//                }
//            }


            while (true) {



                withContext(Dispatchers.IO){
                    try {
                      if (  server?.serverSocket?.isBound==true){
//                          server?.closeCalled=true
//                          server?.serverSocket?.close()
                      }else{
                          server = PersistentServer(this@ExampleForegroundService);
                          server?.start()
                      }
                    } catch (e: Exception) {
                      e.printStackTrace()
                    }


                }




            }
        }


    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}





