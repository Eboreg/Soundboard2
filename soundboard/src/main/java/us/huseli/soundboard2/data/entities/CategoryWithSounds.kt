package us.huseli.soundboard2.data.entities

import androidx.room.Embedded
import androidx.room.Relation

data class CategoryWithSounds(
    @Embedded val category: Category,
    @Relation(parentColumn = "id", entityColumn = "categoryId")
    val sounds: List<Sound>
)
