package us.huseli.soundboard2.helpers

import android.util.Log
import us.huseli.soundboard2.BuildConfig

interface LoggingObject {
    fun log(msg: String, level: Int = Log.DEBUG) {
        if (BuildConfig.DEBUG) Log.println(level, javaClass.simpleName, msg)
    }
}