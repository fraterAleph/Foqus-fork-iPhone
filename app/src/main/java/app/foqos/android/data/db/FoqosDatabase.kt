package app.foqos.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, SessionEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FoqosDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun sessionDao(): SessionDao

    companion object {
        /**
         * Adds NFC-only unlock. Existing profiles are migrated with it on: this is a blocker, and
         * the safe direction for a surprise is the stricter one, not the looser one.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE profiles ADD COLUMN nfcOnlyUnlock INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        @Volatile
        private var instance: FoqosDatabase? = null

        fun get(context: Context): FoqosDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FoqosDatabase::class.java,
                "foqos.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
