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
    version = 3,
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE Sound_new (
                        id INTEGER PRIMARY KEY NOT NULL,
                        categoryId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        'order' INTEGER NOT NULL,
                        duration INTEGER NOT NULL,
                        checksum TEXT NOT NULL,
                        volume INTEGER NOT NULL,
                        added INTEGER NOT NULL,
                        FOREIGN KEY (categoryId) REFERENCES Category(id) ON UPDATE CASCADE ON DELETE SET NULL
                    )""".trimIndent())
                database.execSQL("""
                    INSERT INTO Sound_new (id, categoryId, name, uri, 'order', duration, checksum, volume, added)
                    SELECT id, categoryId, name, uri, 'order', duration, checksum, volume, added FROM Sound
                """.trimIndent())
                database.execSQL("DROP TABLE Sound")
                database.execSQL("ALTER TABLE Sound_new RENAME TO Sound")

                database.execSQL("""
                    CREATE TABLE Category_new (
                        id INTEGER PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        backgroundColor INTEGER NOT NULL,
                        'order' INTEGER NOT NULL,
                        collapsed INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO Category_new (id, name, backgroundColor, 'order', collapsed)
                    SELECT id, name, backgroundColor, 'order', collapsed FROM Category
                """.trimIndent())
                database.execSQL("DROP TABLE Category")
                database.execSQL("ALTER TABLE Category_new RENAME TO Category")
                database.execSQL("CREATE INDEX index_Sound_categoryId ON Sound(categoryId)")
            }
        }

        fun build(context: Context): SoundboardDatabase {
            return Room
                .databaseBuilder(context.applicationContext, SoundboardDatabase::class.java, Constants.DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .build()
        }
    }
}