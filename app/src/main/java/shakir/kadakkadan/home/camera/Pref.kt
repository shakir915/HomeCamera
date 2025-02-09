package shakir.kadakkadan.home.camera

import android.content.Context
import android.content.SharedPreferences


object Pref {


    val preferences: SharedPreferences by lazy {
        AppApplication.instance.getSharedPreferences("1", Context.MODE_PRIVATE)
    }


    private inline fun SharedPreferences.edit(operation: (SharedPreferences.Editor) -> Unit) {
        val editor = edit()
        operation(editor)
        editor.commit()
    }

    var token: String?
        get() = preferences.getString("TOKEN", null)
        set(value) = preferences.edit {
            it.putString("TOKEN", value)
        }

    var phone: String?
        get() = preferences.getString("phone", null)
        set(value) = preferences.edit {
            it.putString("phone", value)
        }



    var isHealth2AllowClicked: Boolean
        get() = preferences.getBoolean("isHealth2AllowClicked", false)
        set(value) = preferences.edit {
            it.putBoolean("isHealth2AllowClicked", value)
        }



    var locationNotAllowClicked: Boolean
        get() = preferences.getBoolean("locationNotAllowClicked", false)
        set(value) = preferences.edit {
            it.putBoolean("locationNotAllowClicked", value)
        }




    val nextInt: Int
        get() {
            val a = preferences.getInt("nextInt", 0) + 1
            preferences.edit {
                it.putInt("nextInt", a)
            }
            return a;

        }


    var BASE_URL: String?
        get() = preferences.getString("BASE_URL", null)
        set(value) = preferences.edit {
            it.putString("BASE_URL", value)
        }

    var nextTimeForNotification: Long
        get() {
            return preferences.getLong("nextTimeForNotification", 0L)
        }
        set(value) {
            preferences.edit {
                it.putLong("nextTimeForNotification", value)
            }
        }

    var isDailyReminderOn: Boolean
        get() = preferences.getBoolean("isDailyReminderOn", true)
        set(value) = preferences.edit {
            it.putBoolean("isDailyReminderOn", value)
        }





    var lastTypedIP: String
        get() = preferences.getString("lastTypedIP", "")?:""
        set(value) = preferences.edit {
            it.putString("lastTypedIP", value)
        }


    var rotationAngle: Float
        get() = preferences.getFloat("rotationAngle", 0f)?:0f
        set(value) = preferences.edit {
            it.putFloat("rotationAngle", value)
        }




}
