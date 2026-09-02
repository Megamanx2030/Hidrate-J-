package com.example.data.repository

import com.example.utils.Zona

import com.example.data.db.Reminder
import com.example.data.db.ReminderDao
import com.example.data.db.UserSettings
import com.example.data.db.UserSettingsDao
import com.example.data.db.WaterLog
import com.example.data.db.WaterLogDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class WaterRepository(
    private val waterLogDao: WaterLogDao,
    private val reminderDao: ReminderDao,
    private val userSettingsDao: UserSettingsDao
) {
    /**
     * OS FORMATADORES SAO CRIADOS NA HORA, NAO GUARDADOS NUM CAMPO.
     *
     * Eram dois SimpleDateFormat criados uma vez, no nascimento do repositorio,
     * com o fuso gravado dentro. Dois problemas nisso:
     *
     * 1. SimpleDateFormat NAO E SEGURO ENTRE THREADS. Estes formatadores sao
     *    usados de coroutines de IO e da thread principal ao mesmo tempo; um
     *    objeto compartilhado ali pode devolver data corrompida, e o registro
     *    de agua cairia num dia que nao existe.
     *
     * 2. O fuso ficava congelado no que valia quando o app abriu. Quem trocasse
     *    de fuso (viagem) so veria o app se acertar depois de matar o processo.
     *
     * Criar o objeto a cada chamada custa praticamente nada -- estas funcoes
     * rodam algumas dezenas de vezes por dia, nao milhares por segundo.
     */
    private fun formatador(padrao: String) =
        SimpleDateFormat(padrao, Locale.getDefault()).apply { timeZone = Zona.fuso() }

    fun getTodayDateString(): String = formatador("yyyy-MM-dd").format(Date())

    fun getTodayDisplayDateString(): String = formatador("dd/MM/yyyy").format(Date())

    val userSettings: Flow<UserSettings> = userSettingsDao.getSettings().map {
        it ?: UserSettings()
    }

    /** O Historico pede so os dias que esta mostrando. Ver WaterLogDao. */
    fun getLogsForDateRange(inicio: String, fim: String): Flow<List<WaterLog>> =
        waterLogDao.getLogsForDateRange(inicio, fim)

    val totalDeRegistros: Flow<Int> = waterLogDao.contarLogs()

    /**
     * O DIA VEM DE FORA, NAO E LIDO AQUI DENTRO.
     *
     * Antes estas funcoes chamavam getTodayDateString() na hora de montar a
     * consulta, e o resultado ficava GRAVADO no Flow. Como a MainViewModel cria
     * esses Flows uma unica vez, o app aberto durante a virada da meia-noite
     * continuava somando a agua de ONTEM: o contador nao zerava e a meta do dia
     * novo comecava ja cheia.
     *
     * Recebendo a data por parametro, quem manda no dia e a ViewModel -- que
     * observa a virada e refaz a consulta.
     */
    fun getLogsForDate(dateString: String): Flow<List<WaterLog>> {
        return waterLogDao.getLogsForDate(dateString)
    }

    fun getTotalMlForDate(dateString: String): Flow<Int> {
        return waterLogDao.getDailySumMl(dateString).map { it ?: 0 }
    }

    val allReminders: Flow<List<Reminder>> = reminderDao.getAllReminders()

    suspend fun clearHistory() {
        waterLogDao.deleteAllLogs()
        reminderDao.resetAllReminderStatus()
    }

    suspend fun clearEverything() {
        waterLogDao.deleteAllLogs()
        reminderDao.deleteAllReminders()
    }

    suspend fun addWaterLog(amountMl: Int): Long {
        if (amountMl <= 0) return 0L
        val log = WaterLog(
            amountMl = amountMl,
            timestamp = System.currentTimeMillis(),
            dateString = getTodayDateString()
        )
        return waterLogDao.insertLog(log)
    }

    suspend fun updateSettings(settings: UserSettings) {
        userSettingsDao.insertOrUpdateSettings(settings)
    }

    suspend fun updateReminder(reminder: Reminder) {
        reminderDao.updateReminder(reminder)
    }

    suspend fun addReminder(reminder: Reminder) {
        reminderDao.insertReminder(reminder)
    }

    suspend fun deleteReminder(reminder: Reminder) {
        reminderDao.deleteReminder(reminder)
    }

    /**
     * A AGENDA QUE VEM PRONTA NA PRIMEIRA ABERTURA: das 09:00 as 18:20, de
     * 1h20 em 1h20.
     *
     * O HISTORICO DESTA DECISAO, porque ela ja mudou duas vezes:
     *
     * No comeco o app criava oito horarios comecando as 06:00. Isso foi
     * retirado ("App comeca vazio") por um motivo legitimo: um lembrete das
     * 06:00 tocava no dia seguinte da instalacao sem ninguem ter pedido, e
     * acordar o usuario e a pior primeira impressao possivel.
     *
     * So que o remedio criou outra doenca. Com o app comecando totalmente
     * vazio, a pessoa instala e nao acontece nada -- ela precisa descobrir
     * sozinha a tela de Lembretes e montar oito horarios na mao ANTES do app
     * fazer qualquer coisa. Num app para idosos, isso e uma parede logo na
     * porta de entrada, e foi o que apareceu no teste fechado: testadores que
     * instalaram e nunca chegaram a ver um lembrete tocar.
     *
     * A AGENDA DE AGORA RESOLVE OS DOIS LADOS. Ela comeca as 09:00, ja bem
     * depois da hora de acordar, e termina as 18:20, antes do jantar -- nenhum
     * lembrete de madrugada, nenhum lembrete na hora de dormir. O intervalo de
     * 1h20 distribui oito copos ao longo do dia util, que e a rotina que o app
     * assume por padrao (2,5 L divididos em copos de 250 ml da exatamente 10
     * copos; oito lembretes mais a agua das refeicoes fecha a conta).
     *
     * NADA DISSO E OBRIGATORIO: sao lembretes comuns, que a pessoa apaga,
     * edita ou acrescenta na tela de Lembretes como quaisquer outros.
     *
     * O QUE CONTINUA VAZIO: o nome (a tela cumprimenta com "Ola!" ate a pessoa
     * se apresentar), o historico de agua e o peso. Nenhum dado inventado sobre
     * o usuario.
     */
    private val horariosDeFabrica = listOf(
        "09:00", "10:20", "11:40", "13:00", "14:20", "15:40", "17:00", "18:20"
    )

    /**
     * Roda UMA VEZ, na primeira abertura. A porta e a ausencia da linha de
     * configuracoes: se ela ja existe, o app ja foi aberto antes e nao se mexe
     * em mais nada -- inclusive para quem apagou todos os lembretes de
     * proposito, que nao pode ve-los voltarem sozinhos na proxima abertura.
     */
    suspend fun initDefaultDataIfNeeded() {
        val existingSettings = userSettingsDao.getSettingsOnce()
        if (existingSettings != null) return

        userSettingsDao.insertOrUpdateSettings(UserSettings(hasSeededDefaults = true))

        horariosDeFabrica.forEach { horario ->
            reminderDao.insertReminder(Reminder(time = horario, title = "Hora da Água"))
        }
    }
}
