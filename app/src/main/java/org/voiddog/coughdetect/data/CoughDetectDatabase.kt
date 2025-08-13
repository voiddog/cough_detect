package org.voiddog.coughdetect.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CoughRecord::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CoughDetectDatabase : RoomDatabase() {
    
    abstract fun coughRecordDao(): CoughRecordDao
    
    companion object {
        @Volatile
        private var INSTANCE: CoughDetectDatabase? = null
        
        fun getDatabase(context: Context): CoughDetectDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CoughDetectDatabase::class.java,
                    "cough_detect_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}