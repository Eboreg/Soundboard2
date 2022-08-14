package us.huseli.soundboard2.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.data.dao.CategoryDao
import us.huseli.soundboard2.data.dao.SoundDao
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.Sound

@Database(
    entities = [Sound::class, Category::class],
    exportSchema = false,
    version = 1,
)
@TypeConverters(Converters::class)
abstract class SoundboardDatabase : RoomDatabase() {
    abstract fun soundDao(): SoundDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        fun build(context: Context): SoundboardDatabase {
            return Room
                .databaseBuilder(context.applicationContext, SoundboardDatabase::class.java, Constants.DATABASE_NAME)
                .build()
        }
    }
}