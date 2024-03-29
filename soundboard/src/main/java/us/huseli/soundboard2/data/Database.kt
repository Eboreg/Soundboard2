package us.huseli.soundboard2.data

import android.content.Context
import android.graphics.Color
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import us.huseli.soundboard2.BuildConfig
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.data.dao.CategoryDao
import us.huseli.soundboard2.data.dao.SoundDao
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.Sound
import us.huseli.soundboard2.helpers.LoggingObject
import java.util.concurrent.Executors

@androidx.room.Database(
    entities = [Sound::class, Category::class],
    exportSchema = false,
    version = 7,
)
@TypeConverters(Converters::class)
abstract class Database : RoomDatabase() {
    abstract fun soundDao(): SoundDao
    abstract fun categoryDao(): CategoryDao

    companion object : LoggingObject {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE Sound RENAME COLUMN path TO uri")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
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
                    )""".trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO Sound_new (id, categoryId, name, uri, 'order', duration, checksum, volume, added)
                    SELECT id, categoryId, name, uri, 'order', duration, checksum, volume, added FROM Sound
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE Sound")
                database.execSQL("ALTER TABLE Sound_new RENAME TO Sound")

                database.execSQL(
                    """
                    CREATE TABLE Category_new (
                        id INTEGER PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        backgroundColor INTEGER NOT NULL,
                        'order' INTEGER NOT NULL,
                        collapsed INTEGER NOT NULL DEFAULT 0
                    )""".trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO Category_new (id, name, backgroundColor, 'order', collapsed)
                    SELECT id, name, backgroundColor, 'order', collapsed FROM Category
                """.trimIndent()
                )
                database.execSQL("DROP TABLE Category")
                database.execSQL("ALTER TABLE Category_new RENAME TO Category")
                database.execSQL("CREATE INDEX index_Sound_categoryId ON Sound(categoryId)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE Category ADD COLUMN soundSorting INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Removed SoundSorting.Parameter.CUSTOM and subtracted 1 from the values of the others:
                database.execSQL(
                    """
                    UPDATE Category SET soundSorting = CASE 
                        WHEN soundSorting < 0 THEN soundSorting + 1 
                        ELSE soundSorting - 1 
                        END
                    """.trimIndent()
                )
                // Renamed Category.order to position:
                database.execSQL("ALTER TABLE Category RENAME COLUMN 'order' TO position")
                // Removed Sound.order:
                database.execSQL(
                    """
                    CREATE TABLE Sound_new (
                        id INTEGER PRIMARY KEY NOT NULL,
                        categoryId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        checksum TEXT NOT NULL,
                        volume INTEGER NOT NULL,
                        added INTEGER NOT NULL,
                        FOREIGN KEY (categoryId) REFERENCES Category(id) ON UPDATE CASCADE ON DELETE SET NULL
                    )""".trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO Sound_new (id, categoryId, name, uri, duration, checksum, volume, added)
                    SELECT id, categoryId, name, uri, duration, checksum, volume, added FROM Sound
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE Sound")
                database.execSQL("ALTER TABLE Sound_new RENAME TO Sound")
                database.execSQL("CREATE INDEX index_Sound_categoryId ON Sound(categoryId)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE Sound ADD COLUMN backgroundColor INTEGER NOT NULL DEFAULT ${Color.TRANSPARENT}")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE Category_new (
                        categoryId INTEGER PRIMARY KEY NOT NULL,
                        categoryName TEXT NOT NULL,
                        backgroundColor INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        collapsed INTEGER NOT NULL DEFAULT 0,
                        soundSorting INTEGER NOT NULL DEFAULT 1
                    )""".trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO Category_new (categoryId, categoryName, backgroundColor, position, collapsed, soundSorting)
                    SELECT id, name, backgroundColor, position, collapsed, soundSorting FROM Category
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE Category")
                database.execSQL("ALTER TABLE Category_new RENAME TO Category")

                database.execSQL(
                    """
                    CREATE TABLE Sound_new (
                        soundId INTEGER PRIMARY KEY NOT NULL,
                        soundCategoryId INTEGER NOT NULL,
                        soundName TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        checksum TEXT NOT NULL,
                        volume INTEGER NOT NULL,
                        added INTEGER NOT NULL,
                        soundBackgroundColor INTEGER NOT NULL DEFAULT ${Color.TRANSPARENT},
                        FOREIGN KEY (soundCategoryId) REFERENCES Category(categoryId) ON UPDATE CASCADE ON DELETE SET NULL
                    )""".trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO Sound_new (soundId, soundCategoryId, soundName, uri, duration, checksum, volume, added, soundBackgroundColor)
                    SELECT id, categoryId, name, uri, duration, checksum, volume, added, backgroundColor FROM Sound
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE Sound")
                database.execSQL("ALTER TABLE Sound_new RENAME TO Sound")
                database.execSQL("CREATE INDEX index_Sound_categoryId ON Sound(soundCategoryId)")
            }
        }

        fun build(context: Context): Database {
            val builder = Room
                .databaseBuilder(context.applicationContext, Database::class.java, Constants.DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)

            if (BuildConfig.DEBUG) {
                class Callback : QueryCallback {
                    override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                        log("$sqlQuery, bindArgs=$bindArgs")
                    }
                }

                val executor = Executors.newSingleThreadExecutor()
                builder.setQueryCallback(Callback(), executor)
            }

            return builder.build()
        }
    }
}