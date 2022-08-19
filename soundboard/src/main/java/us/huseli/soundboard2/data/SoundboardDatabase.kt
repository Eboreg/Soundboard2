package us.huseli.soundboard2.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.data.dao.CategoryDao
import us.huseli.soundboard2.data.dao.SoundDao
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.Sound

@Database(
    entities = [Sound::class, Category::class],
    exportSchema = false,
    version = 2,
)
@TypeConverters(Converters::class)
abstract class SoundboardDatabase : RoomDatabase() {
    abstract fun soundDao(): SoundDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE Sound RENAME COLUMN path TO uri")
            }
        }

        fun build(context: Context): SoundboardDatabase {
            return Room
                .databaseBuilder(context.applicationContext, SoundboardDatabase::class.java, Constants.DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}