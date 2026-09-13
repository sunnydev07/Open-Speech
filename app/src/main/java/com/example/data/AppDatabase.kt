package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Room database holding the session log. Version 2 includes audioPath and self-assessment metrics. */
@Database(entities = [SessionEntity::class], version = 2, exportSchema = false)
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
                ).fallbackToDestructiveMigration(true).build().also { instance = it }
            }
    }
}
