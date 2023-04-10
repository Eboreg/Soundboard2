package us.huseli.soundboard2.data

import android.net.Uri
import androidx.room.TypeConverter
import us.huseli.soundboard2.helpers.SoundSorting
import java.util.*

object Converters {
    @TypeConverter
    @JvmStatic
    fun dateToLong(value: Date): Long = value.time

    @TypeConverter
    @JvmStatic
    fun intToSoundSorting(value: Int): SoundSorting = SoundSorting.fromInt(value)

    @TypeConverter
    @JvmStatic
    fun longToDate(value: Long): Date = Date(value)

    @TypeConverter
    @JvmStatic
    fun soundSortingToInt(value: SoundSorting): Int = value.parameter.value * value.order.value

    @TypeConverter
    @JvmStatic
    fun stringToUri(value: String): Uri = Uri.parse(value)

    @TypeConverter
    @JvmStatic
    fun uriToString(value: Uri): String = value.toString()
}