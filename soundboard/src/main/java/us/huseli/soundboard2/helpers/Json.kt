package us.huseli.soundboard2.helpers

import android.net.Uri
import com.google.gson.*
import java.lang.reflect.Type

class UriSerializer : JsonSerializer<Uri> {
    override fun serialize(src: Uri?, typeOfSrc: Type?, context: JsonSerializationContext?) =
        src?.path?.let { JsonPrimitive(it) }
}

class UriDeserializer : JsonDeserializer<Uri> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Uri? =
        json?.asJsonPrimitive?.asString?.let { Uri.parse(it) }
}