package org.voiddog.coughdetect.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CoughRecord::class],
    version = 3, // 更新版本号
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CoughDetectDatabase : RoomDatabase() {
    
    abstract fun coughRecordDao(): CoughRecordDao
    
    companion object {
        @Volatile
        private var INSTANCE: CoughDetectDatabase? = null
        
        // 定义数据库迁移脚本
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 为 cough_records 表添加 extension 字段
                database.execSQL("ALTER TABLE cough_records ADD COLUMN extension TEXT NOT NULL DEFAULT '{}'")
            }
        }
        
        fun getDatabase(context: Context): CoughDetectDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CoughDetectDatabase::class.java,
                    "cough_detect_database"
                )
                    .addMigrations(MIGRATION_2_3) // 添加迁移脚本
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}