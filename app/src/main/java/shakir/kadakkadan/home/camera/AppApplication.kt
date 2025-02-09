package shakir.kadakkadan.home.camera

import android.app.Application

class AppApplication : Application() {



    override fun onCreate() {
        instance = this
        super.onCreate()
    }

    companion object {
        lateinit var instance :AppApplication
    }
}