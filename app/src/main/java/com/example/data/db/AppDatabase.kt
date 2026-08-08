package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WaterLog::class, Reminder::class, UserSettings::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun waterLogDao(): WaterLogDao
    abstract fun reminderDao(): ReminderDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE user_settings ADD COLUMN vibrateOnly INTEGER NOT NULL DEFAULT 0")
            }
        }

        // NOVA: adiciona lastResetDate preservando os dados do usuario.
        // Migration de verdade em vez de deixar o fallback destrutivo
        // apagar o historico de quem ja tem o app instalado.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE user_settings ADD COLUMN lastResetDate TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reminders ADD COLUMN waterLogId INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Indice por dia. O nome tem que ser exatamente este: e o que o Room
        // gera para @Index(value = ["dateString"]), e ele confere o schema na
        // abertura. Nome diferente = banco recriado do zero pelo fallback
        // destrutivo, ou seja, historico do usuario perdido.
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_water_logs_dateString ON water_logs (dateString)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hydracompanion_db"
                )
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
