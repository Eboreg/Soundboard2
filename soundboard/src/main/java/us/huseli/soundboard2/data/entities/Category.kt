package us.huseli.soundboard2.data.entities

import androidx.annotation.ColorInt
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Category")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val name: String,
    @ColorInt val backgroundColor: Int,
    val order: Int,
    val collapsed: Boolean = false,
    val autoImportCategory: Boolean = false,
) {
    override fun toString() = name
    override fun equals(other: Any?) = other is Category && other.id == id
    override fun hashCode() = id
}