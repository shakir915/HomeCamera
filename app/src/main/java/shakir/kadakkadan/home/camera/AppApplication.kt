package shakir.kadakkadan.home.camera

import android.app.Application
import java.io.File

var recordingFile:File?=null

class AppApplication : Application() {



    override fun onCreate() {
        instance = this
        super.onCreate()
        recordingFile=File(filesDir,"Recording",)
        try {
            recordingFile?.delete()
        } catch (e: Exception) {
           e.printStackTrace()
        }

    }

    companion object {
        lateinit var instance :AppApplication
    }
}