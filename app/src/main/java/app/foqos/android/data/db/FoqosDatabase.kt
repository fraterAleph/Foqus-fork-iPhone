package app.foqos.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ProfileEntity::class, SessionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FoqosDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var instance: FoqosDatabase? = null

        fun get(context: Context): FoqosDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FoqosDatabase::class.java,
                "foqos.db",
            ).build().also { instance = it }
        }
    }
}
