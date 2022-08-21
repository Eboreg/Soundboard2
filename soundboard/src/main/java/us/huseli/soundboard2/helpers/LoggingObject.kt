package us.huseli.soundboard2.helpers

import android.annotation.SuppressLint
import android.icu.text.SimpleDateFormat
import android.util.Log
import us.huseli.soundboard2.BuildConfig
import java.util.*

interface LoggingObject {
    @SuppressLint("SimpleDateFormat")
    private fun getTimeString(): String {
        return SimpleDateFormat("HH:mm:ss.SSSS").format(Calendar.getInstance().time)
    }

    fun log(msg: String, level: Int = Log.DEBUG) {
        if (BuildConfig.DEBUG) Log.println(
            level,
            javaClass.simpleName,
            "[${Thread.currentThread()}] [${getTimeString()}] $msg"
        )
    }
}