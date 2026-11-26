package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CleanHistoryEntity::class], version = 1, exportSchema = false)
abstract class CleanProDatabase : RoomDatabase() {
    abstract fun cleanHistoryDao(): CleanHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: CleanProDatabase? = null

        fun getInstance(context: Context): CleanProDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CleanProDatabase::class.java,
                    "clean_pro_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
