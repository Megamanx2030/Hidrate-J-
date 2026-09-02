package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * exportSchema PASSOU A SER true, e isso e uma correcao, nao um detalhe.
 *
 * Com ele desligado o Room nao guardava o retrato de nenhuma versao do banco.
 * Foi exatamente por isso que as migracoes das versoes 1 a 5 se perderam: nao
 * existe registro de como aquelas tabelas eram, nem no codigo nem no historico
 * do git (o commit mais antigo deste repositorio ja nasce na versao 6).
 *
 * A partir daqui cada versao fica gravada em app/schemas. Quem for escrever a
 * migracao da 9 para a 10 vai ter o antes e o depois na mao.
 */
@Database(
    entities = [WaterLog::class, Reminder::class, UserSettings::class],
    version = 11,
    exportSchema = true
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

        /**
         * O peso do usuario, para a sugestao de meta por quilo.
         *
         * DEFAULT 0 quer dizer "nao informado": quem ja usava o app sobe a
         * versao sem perder nada e sem ganhar um peso inventado. O campo e
         * opcional na tela e continua opcional no banco.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE user_settings ADD COLUMN pesoKg INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * O instante em que cada lembrete foi criado.
         *
         * DEFAULT 0 e o valor certo para quem ja tinha lembretes: zero e menor
         * que qualquer horario de hoje, entao nada muda para eles. Ver o
         * comentario de criadoEmMs no Reminder.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reminders ADD COLUMN criadoEmMs INTEGER NOT NULL DEFAULT 0")
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
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                /**
                 * O FALLBACK DEIXOU DE SER GERAL.
                 *
                 * Antes era .fallbackToDestructiveMigration() sem argumento:
                 * QUALQUER versao sem migracao fazia o Room apagar o banco
                 * inteiro, calado. Numa versao publicada isso significa que
                 * subir o version para 10 e esquecer a migracao apagaria o
                 * historico de agua de todos os usuarios, sem erro, sem aviso,
                 * sem volta. O desenvolvedor so descobriria pelas avaliacoes.
                 *
                 * Agora ele vale SO para as versoes 1 a 4, que sao as unicas
                 * que nao tem como ser migradas: o esquema delas nao existe em
                 * lugar nenhum (ver o comentario do exportSchema acima), e o
                 * app nunca foi publicado, entao ninguem no mundo tem um banco
                 * nessas versoes.
                 *
                 * A 5 NAO entra na lista, e o Room e rigoroso quanto a isso:
                 * como existe MIGRATION_5_6, listar a 5 aqui seria dizer duas
                 * coisas contrarias sobre a mesma versao. O Room recusa na hora
                 * de abrir o banco, com "Inconsistency detected" -- e o app nem
                 * chega a mostrar a primeira tela.
                 *
                 * Da 5 em diante, faltar migracao agora ESTOURA na hora de
                 * abrir o banco. E o comportamento certo: falha barulhenta no
                 * teste do desenvolvedor em vez de perda silenciosa no celular
                 * do usuario.
                 */
                .fallbackToDestructiveMigrationFrom(
                    dropAllTables = true,
                    startVersions = intArrayOf(1, 2, 3, 4)
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
