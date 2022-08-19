package us.huseli.soundboard2.data

import android.net.Uri
import androidx.room.TypeConverter
import java.util.*

object Converters {
    @TypeConverter
    @JvmStatic
    fun longToDate(value: Long): Date = Date(value)

    @TypeConverter
    @JvmStatic
    fun dateToLong(value: Date): Long = value.time

    @TypeConverter
    @JvmStatic
    fun uriToString(value: Uri): String = value.toString()

    @TypeConverter
    @JvmStatic
    fun stringToUri(value: String): Uri = Uri.parse(value)
}