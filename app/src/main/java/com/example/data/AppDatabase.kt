package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Room database holding the session log (F2). Version 1 — no migrations yet. */
@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
abstract class OpenSpeechDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var instance: OpenSpeechDatabase? = null

        fun get(context: Context): OpenSpeechDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OpenSpeechDatabase::class.java,
                    "openspeech.db"
                ).build().also { instance = it }
            }
    }
}
