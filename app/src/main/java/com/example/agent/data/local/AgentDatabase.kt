package com.example.agent.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TaskEntity::class], version = 2, exportSchema = false)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        /**
         * v1 -> v2 added the recorded steps column so a task can be opened and read
         * back action by action. Existing rows simply get an empty list.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN steps TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
