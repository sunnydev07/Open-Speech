package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Room database holding the session log (F2). Version 1 — no migrations yet. */
@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
abstract class FluencyDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var instance: FluencyDatabase? = null

        fun get(context: Context): FluencyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FluencyDatabase::class.java,
                    "fluency.db"
                ).build().also { instance = it }
            }
    }
}
