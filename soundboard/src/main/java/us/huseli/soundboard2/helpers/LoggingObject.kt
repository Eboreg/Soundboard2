package us.huseli.soundboard2.helpers

import android.util.Log
import us.huseli.soundboard2.BuildConfig

interface LoggingObject {
    fun log(msg: String, level: Int = Log.INFO) {
        if (BuildConfig.DEBUG) Log.println(
            level,
            "${javaClass.simpleName}<${System.identityHashCode(this)}>",
            "[${Thread.currentThread()}] $msg"
        )
    }
}