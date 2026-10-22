package com.example.agent.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TaskEntity::class, AuditEntity::class], version = 4, exportSchema = false)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun auditDao(): AuditDao

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

        /**
         * v2 -> v3 adds the task totals. They existed on the domain model but were never
         * given columns, so the diagnostics always read back zero. Existing rows get zero,
         * which is honest - the totals were not recorded for them.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN inputTokens INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN outputTokens INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN modelMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v3 -> v4 adds the local audit log. A new table only, so no existing row changes.
         * The SQL must match [AuditEntity] exactly; exportSchema is off, so nothing else
         * checks it before Room validates the table on open.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audit_events` (" +
                        "`id` TEXT NOT NULL, " +
                        "`timestampMillis` INTEGER NOT NULL, " +
                        "`requestText` TEXT NOT NULL, " +
                        "`intentType` TEXT NOT NULL, " +
                        "`intentSummary` TEXT NOT NULL, " +
                        "`guardVerdict` TEXT NOT NULL, " +
                        "`confirmed` INTEGER, " +
                        "`result` TEXT NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`appVersion` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_audit_events_timestampMillis` " +
                        "ON `audit_events` (`timestampMillis`)",
                )
            }
        }

        /** Every migration, in order. Pass to `addMigrations(*AgentDatabase.ALL_MIGRATIONS)`. */
        val ALL_MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
