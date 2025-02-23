package shakir.kadakkadan.home.camera

import android.content.Context
import android.media.MediaCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class PersistentServer(val context:Context ) {
     val serverSocket = ServerSocket(port)
    private val executorService = Executors.newCachedThreadPool() // Thread pool for clients
    private val clientList = mutableListOf<ClientHandler>()
    private val mutex = Mutex()


    var closeCalled=false

    fun start() {
        println("Server started on port $port")
        while (!closeCalled) {
            try {
                val clientSocket = serverSocket.accept()


                val clientHandler = ClientHandler(clientSocket)
                synchronized(clientList) {
                    clientList.add(clientHandler)
                }

                executorService.execute { clientHandler.handleClient() }
                executorService.execute { clientHandler.sendData() }
            } catch (e: Exception) {
               e.printStackTrace()
            }
        }
    }

    // New method to broadcast data to all clients
    suspend fun broadcastData(data: ByteArray) {
        mutex.withLock {
            synchronized(clientList) {
                val disconnectedClients = mutableListOf<ClientHandler>()

                clientList.forEach { client ->
                    try {
                        client.send1()
                    } catch (e: Exception) {
                        println("Failed to send to client: ${e.message}")
                        disconnectedClients.add(client)
                    }
                }

                // Remove disconnected clients
                clientList.removeAll(disconnectedClients)
            }
        }
    }




    fun stop(){
//        closeCalled=true
//        serverSocket.close()
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
                        GlobalScope.launch {
                            withContext(Dispatchers.Main){
                                openCamera(context)
                            }
                        }
                    } else if (message == "STOP_CAMERA") {
                        closeCamera()
                    }
                }
            } catch (e: Exception) {
                println("SERVER READ LOG Client disconnected: ${socket.inetAddress.hostAddress}")
            } finally {
                println("SERVER READ LOG Client finally:")
                stop()
                GlobalScope.launch {
                    withContext(Dispatchers.Main){
                       closeCamera()
                    }
                }
            }
        }

        fun sendData() {
            try {
                while (running) {
                    send1()
                                  }
            } catch (e: Exception) {
                println("SERVER WRITE LOG Error sending data: ${e.message}")
            } finally {
                println("SERVER WRITE LOG finally")
                //stop()
            }
        }

        fun send1(){



            if (mediaCodec!=null&&sendOn)
            try {

                val bufferInfo = MediaCodec.BufferInfo()
                while (mediaCodec!=null&&sendOn) {
                    println("attttttt1")
                    val outputIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, 10000)?:-1
                    println("attttttt2")
                    if (outputIndex >= 0) {
                        val outputBuffer = mediaCodec!!.getOutputBuffer(outputIndex)
                        val bytes = ByteArray(bufferInfo.size)
                        outputBuffer?.get(bytes)
                        println("send2 ${bytes.size}")
                        output.writeInt(1) // Send number of byte arrays
                        output.writeLong(System.currentTimeMillis())
                        output.writeInt(bytes.size) // Send size of byte array
                        output.write(bytes) // Send byte array
                        output.flush()
                        mediaCodec!!.releaseOutputBuffer(outputIndex, false)
                        continue
                    }

                }
            } catch (e: Exception) {
               e.printStackTrace()

            }


            output.writeInt(0) // No data available
            output.writeLong(System.currentTimeMillis())
            output.flush()


        }


        fun stop() {
            running = false
            synchronized(clientList) {
                clientList.remove(this)
            }
            //socket.close()
        }
    }
}