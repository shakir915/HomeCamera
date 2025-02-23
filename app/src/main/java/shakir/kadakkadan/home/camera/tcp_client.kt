package shakir.kadakkadan.home.camera

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.TimeUnit

class PersistentClient(
    private val serverAddress: String,
    private val port: Int,

    ) {
    var socket: Socket?=null
    private var isRunning = true

    fun start() {
        while (isRunning) {
            try {
                println("Connecting to server at $serverAddress:$port...")

                socket = Socket(serverAddress, port)
                val input = DataInputStream(socket!!.getInputStream())
                val output = DataOutputStream(socket!!.getOutputStream())

                println("Connected to server!")

                // Start camera (or any initial command)
                output.writeUTF("START_CAMERA")
                output.flush()

                while (isRunning) {


                    val count = input.readInt()
                    if (count > 0) {
                        println("Receiving $count byte arrays")
                        lastSentTimeStamp=input.readLong()
                        lastDataSize=count
                        repeat(count) {
                            val size = input.readInt()
                            val data = ByteArray(size)
                            input.readFully(data) // Read entire byte array
                            byteArrayQueue.write(data)
                            Thread.sleep(5)


                            //viewModel.processAndDisplayImage(data)
                        }
                    } else if (count==0) {
                        lastDataSize=0
                        lastSentTimeStamp=input.readLong()
                        println("No new data available")
                        byteArrayQueue.write(ByteArray(0))
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
        socket?.close()
    }
}
