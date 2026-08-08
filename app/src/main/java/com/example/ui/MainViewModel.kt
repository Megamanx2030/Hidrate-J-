package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.alarm.NotificationHelper
import com.example.data.db.AppDatabase
import com.example.data.db.Reminder
import com.example.data.db.UserSettings
import com.example.data.db.WaterLog
import com.example.data.repository.WaterRepository
import com.example.utils.SoundAndVibrationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = WaterRepository(
        db.waterLogDao(),
        db.reminderDao(),
        db.userSettingsDao()
    )

    val soundAndVibrationManager = SoundAndVibrationManager(application)
    private val alarmScheduler = AlarmScheduler(application)

    val userSettings: StateFlow<UserSettings> = repository.userSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    val todayLogs: StateFlow<List<WaterLog>> = repository.getTodayLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val todayTotalMl: StateFlow<Int> = repository.getTodayTotalMl()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val monthlyTotalMl: StateFlow<Int> = repository.getMonthlyTotalMl()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val allReminders: StateFlow<List<Reminder>> = repository.allReminders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allLogs: StateFlow<List<WaterLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Alert Overlay state
    private val _isAlertVisible = MutableStateFlow(false)
    val isAlertVisible: StateFlow<Boolean> = _isAlertVisible.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(10)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private var countdownJob: Job? = null
    private var activeReminderId: Int? = null

    // Navigation tab
    private val _selectedTab = MutableStateFlow(0) // 0: Início, 1: Histórico, 2: Lembretes
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Dialog controls
    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _showAddWaterDialog = MutableStateFlow(false)
    val showAddWaterDialog: StateFlow<Boolean> = _showAddWaterDialog.asStateFlow()

    private val _pendingAlertReminderId = MutableStateFlow<Int?>(null)
    val pendingAlertReminderId: StateFlow<Int?> = _pendingAlertReminderId.asStateFlow()

    fun clearPendingAlert() {
        _pendingAlertReminderId.value = null
    }

    init {
        viewModelScope.launch {
            repository.initDefaultDataIfNeeded()

            normalizarModoDeAviso()

            // ORDEM IMPORTA: o reset diario tem que rodar ANTES da varredura
            // de "esquecidos", senao os lembretes de ontem sao marcados como
            // esquecidos hoje.
            resetDailyStatusIfNewDay()

            markMissedRemindersAsSkipped()
            
            checkMissedRecentAlert()

            scheduleAllReminders()
        }

        // Create notification channel early
        NotificationHelper(application)

        // Verificador em primeiro plano: so abre o overlay com o app aberto.
        // Som e vibracao sao do AlarmManager + WaterAlarmService.
        viewModelScope.launch {
            var lastTriggeredMinute: String? = null
            val spZone = java.time.ZoneId.of("America/Sao_Paulo")
            while (true) {
                delay(3000)
                if (!_isAlertVisible.value) {
                    val nowSp = java.time.LocalTime.now(spZone)
                    val currentHHmm = String.format("%02d:%02d", nowSp.hour, nowSp.minute)

                    if (currentHHmm != lastTriggeredMinute) {
                        val matchingReminder = allReminders.value
                            .find { it.time == currentHHmm && !it.isCompleted && !it.isSkipped }
                        if (matchingReminder != null) {
                            lastTriggeredMinute = currentHHmm
                            android.util.Log.d(
                                "HidrateJa",
                                "Poll 3s encontrou lembrete ${matchingReminder.id} em $currentHHmm"
                            )
                            triggerWaterAlert(matchingReminder.id, playMedia = false)
                        }
                    }
                }
            }
        }
    }

    /**
     * CORRECAO PRINCIPAL: virada de dia.
     *
     * O status bebido/esquecido e DIARIO, mas estava gravado como flag
     * permanente no Reminder. Sem este reset, no segundo dia de uso:
     *   - a tela mostrava "Bebido as 06:04" de ontem
     *   - o verificador nunca mais abria o overlay (filtra !isCompleted)
     *   - o scheduleAllReminders pulava os concluidos
     */
    private fun getNextOccurrenceDateString(time: String): String {
        val spZone = java.time.ZoneId.of("America/Sao_Paulo")
        val now = java.time.ZonedDateTime.now(spZone)
        val timeParts = time.split(":")
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
        
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        
        if (target.isBefore(now) || target.isEqual(now)) {
            target = target.plusDays(1)
        }
        
        return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(target)
    }

    /**
     * Conserta o estado antigo em que os dois modos ficaram gravados ligados.
     *
     * Quem estava com "Ativar Alertas" e "Somente Vibrar" ligados ao mesmo
     * tempo ja ouvia so a vibracao (o WaterAlarmService sempre deu preferencia
     * a ela), mas a tela mostrava os dois ligados. Aqui o banco passa a
     * refletir o que de fato acontece, sem mudar o comportamento sentido pelo
     * usuario.
     */
    private suspend fun normalizarModoDeAviso() {
        val atual = db.userSettingsDao().getSettingsOnce() ?: return
        if (atual.alertsEnabled && atual.vibrateOnly) {
            android.util.Log.d("HidrateJa", "Modos conflitantes gravados: mantendo Somente Vibrar")
            val corrigido = atual.copy(alertsEnabled = false)
            repository.updateSettings(corrigido)
            espelharModoNasPrefs(corrigido.alertsEnabled, corrigido.vibrateOnly)
        } else {
            espelharModoNasPrefs(atual.alertsEnabled, atual.vibrateOnly)
        }
    }

    /**
     * O Room continua sendo a fonte da verdade, mas o receiver e o servico
     * precisam do modo de forma SINCRONA, antes de montar a notificacao.
     * Por isso o espelho em SharedPreferences, regravado a cada mudanca.
     */
    private fun espelharModoNasPrefs(alertsEnabled: Boolean, vibrateOnly: Boolean) {
        com.example.alarm.AlertModePrefs.save(getApplication(), alertsEnabled, vibrateOnly)
    }

    private suspend fun resetDailyStatusIfNewDay() {
        val hoje = repository.getTodayDisplayDateString()
        val ultimoReset = db.userSettingsDao().getSettingsOnce()?.lastResetDate ?: ""

        if (ultimoReset != hoje) {
            db.reminderDao().getAllRemindersOnce().forEach { reminder ->
                repository.updateReminder(
                    reminder.copy(
                        isCompleted = false,
                        completedTime = "",
                        isSkipped = false,
                        skippedDate = "",
                        skippedTime = "",
                        waterLogId = 0,
                        date = getNextOccurrenceDateString(reminder.time)
                    )
                )
            }
            val atual = db.userSettingsDao().getSettingsOnce() ?: UserSettings()
            repository.updateSettings(atual.copy(lastResetDate = hoje))
        } else {
            db.reminderDao().getAllRemindersOnce().forEach { reminder ->
                val nextDate = getNextOccurrenceDateString(reminder.time)
                if (reminder.date != nextDate) {
                    repository.updateReminder(reminder.copy(date = nextDate))
                }
            }
        }
    }

    private val JANELA_CARENCIA_MIN = 15

    /** Marca como esquecido o que ja passou da hora e nao foi confirmado hoje. */
    private suspend fun markMissedRemindersAsSkipped() {
        val spZone = java.time.ZoneId.of("America/Sao_Paulo")
        val nowSp = java.time.LocalTime.now(spZone)
        val currentHHmm = String.format("%02d:%02d", nowSp.hour, nowSp.minute)
        val dateStr = repository.getTodayDisplayDateString()

        db.reminderDao().getAllRemindersOnce().forEach { reminder ->
            if (!reminder.isCompleted && !reminder.isSkipped && reminder.time < currentHHmm) {
                val parts = reminder.time.split(":")
                if (parts.size == 2) {
                    val h = parts[0].toIntOrNull() ?: 0
                    val m = parts[1].toIntOrNull() ?: 0
                    val reminderTime = java.time.LocalTime.of(h, m)
                    val duration = java.time.Duration.between(reminderTime, nowSp)
                    
                    // So marca como esquecido se ja passaram mais da janela de carencia
                    if (!duration.isNegative && duration.toMinutes() > JANELA_CARENCIA_MIN) {
                        repository.updateReminder(
                            reminder.copy(
                                isSkipped = true,
                                skippedDate = dateStr,
                                skippedTime = reminder.time,
                                date = getNextOccurrenceDateString(reminder.time)
                            )
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Chamada pelo onResume da MainActivity.
     *
     * Abrir o app pelo icone quando a task ja existe traz a Activity para a
     * frente SEM recriar a ViewModel (START_TASK_TO_FRONT), entao o init nao
     * roda e a varredura de alerta perdido nao acontecia -- comprovado no
     * logcat: nenhuma linha "Found/No missed alert" naquela abertura.
     */
    fun checkMissedAlertOnResume() {
        if (_isAlertVisible.value) {
            android.util.Log.d("HidrateJa", "onResume: alerta ja visivel, nao checa")
            return
        }
        viewModelScope.launch {
            android.util.Log.d("HidrateJa", "onResume: virada de dia + alerta perdido")

            // MESMA ORDEM DO init, E PELO MESMO MOTIVO.
            //
            // A virada de dia so rodava na criacao da ViewModel. Quem deixa o
            // app aberto de um dia para o outro, ou volta para ele pela task
            // ja existente, ficava com o status de ontem: a tela mostrava
            // "Bebido as 06:04" de ontem e o isCompleted/isSkipped velho
            // bloqueava o alerta de hoje.
            //
            // Rodando aqui, a virada acontece toda vez que o usuario volta ao
            // app, e os lembretes rolam sozinhos para o dia seguinte nos
            // mesmos horarios, sem ele refazer nada.
            resetDailyStatusIfNewDay()
            markMissedRemindersAsSkipped()
            checkMissedRecentAlert()
            scheduleAllReminders()
        }
    }

    private suspend fun checkMissedRecentAlert() {
        val spZone = java.time.ZoneId.of("America/Sao_Paulo")
        val nowSp = java.time.LocalTime.now(spZone)
        val currentHHmm = String.format("%02d:%02d", nowSp.hour, nowSp.minute)
        
        val recentMissed = db.reminderDao().getAllRemindersOnce().filter { reminder ->
            !reminder.isCompleted && !reminder.isSkipped && reminder.time < currentHHmm
        }.filter { reminder ->
            val parts = reminder.time.split(":")
            if (parts.size == 2) {
                val h = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                val reminderTime = java.time.LocalTime.of(h, m)
                val duration = java.time.Duration.between(reminderTime, nowSp)
                !duration.isNegative && duration.toMinutes() <= JANELA_CARENCIA_MIN
            } else false
        }.maxByOrNull { it.time }

        if (recentMissed != null) {
            android.util.Log.d("HidrateJa", "Found missed alert: ${recentMissed.id} at ${recentMissed.time}")
            _pendingAlertReminderId.value = recentMissed.id
        } else {
            android.util.Log.d("HidrateJa", "No missed alert found")
        }
    }

    private fun scheduleAllReminders() {
        viewModelScope.launch {
            val reminders = db.reminderDao().getAllRemindersOnce()
            val chimeType = userSettings.value.chimeType
            
            // Get current time to avoid re-scheduling a reminder that is firing RIGHT NOW
            val spZone = java.time.ZoneId.of("America/Sao_Paulo")
            val nowSp = java.time.LocalTime.now(spZone)
            val currentHHmm = String.format("%02d:%02d", nowSp.hour, nowSp.minute)
            
            reminders.forEach { reminder ->
                // Um lembrete ja concluido cujo horario JA PASSOU tem que ser
                // rearmado assim mesmo: a proxima ocorrencia dele e amanha, e
                // ate la o reset diario ja terá zerado o status. Sem isso ele
                // so voltava a ser agendado quando o usuario abria o app
                // depois da virada -- e se ficasse dias sem abrir, morria.
                //
                // O unico caso que NAO rearma e o concluido que ainda vai
                // chegar hoje: tocaria para algo que o usuario ja marcou como
                // bebido.
                val jaPassouHoje = reminder.time <= currentHHmm
                val concluidoEAindaVaiChegar = reminder.isCompleted && !jaPassouHoje

                if (!concluidoEAindaVaiChegar && reminder.time != currentHHmm) {
                    alarmScheduler.scheduleReminder(
                        reminderId = reminder.id,
                        time = reminder.time,
                        chimeType = chimeType,
                        title = reminder.title
                    )
                }
            }
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun openSettingsDialog() {
        _showSettingsDialog.value = true
    }

    fun closeSettingsDialog() {
        _showSettingsDialog.value = false
    }

    fun openAddWaterDialog() {
        _showAddWaterDialog.value = true
    }

    fun closeAddWaterDialog() {
        _showAddWaterDialog.value = false
    }

    fun addWater(amountMl: Int) {
        viewModelScope.launch {
            repository.addWaterLog(amountMl)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            // Reschedule all alarms since status was reset
            scheduleAllReminders()
        }
    }

    fun clearEverything() {
        viewModelScope.launch {
            // CORRIGIDO: antes os lembretes eram apagados mas os alarmes
            // continuavam agendados. Resultado: alarme orfao disparando
            // para lembrete que nao existe mais.
            alarmScheduler.cancelAll(db.reminderDao().getAllRemindersOnce())
            repository.clearEverything()
        }
    }

    private var lastDismissedReminderId: Int? = null
    private var lastDismissedTimestamp: Long = 0L

    fun triggerWaterAlert(reminderId: Int? = null, playMedia: Boolean = true) {
        viewModelScope.launch {
            android.util.Log.d("HidrateJa", "triggerWaterAlert id=$reminderId playMedia=$playMedia")
            if (reminderId != null) {
                // Modo silencioso: os dois interruptores desligados significam
                // "so a notificacao na barra". Este e o ponto unico que fecha
                // TODOS os caminhos de exibicao vindos de lembrete -- o poll de
                // 3s, o handleIntent e o alerta perdido. O botao de testar
                // alerta continua funcionando porque passa reminderId nulo.
                val s = userSettings.value
                if (!s.alertsEnabled && !s.vibrateOnly) {
                    android.util.Log.d("HidrateJa", "triggerWaterAlert ABORTOU: modo silencioso")
                    return@launch
                }

                val reminder = db.reminderDao().getAllRemindersOnce().find { it.id == reminderId }
                if (reminder != null && (reminder.isCompleted || reminder.isSkipped)) {
                    android.util.Log.d(
                        "HidrateJa",
                        "triggerWaterAlert ABORTOU: isCompleted=${reminder.isCompleted} isSkipped=${reminder.isSkipped}"
                    )
                    return@launch
                }
                if (reminderId == lastDismissedReminderId && (System.currentTimeMillis() - lastDismissedTimestamp < 2 * 60 * 1000)) {
                    android.util.Log.d("HidrateJa", "triggerWaterAlert ABORTOU: dispensado ha menos de 2 min")
                    return@launch
                }
            }

            activeReminderId = reminderId
            android.util.Log.d("HidrateJa", "triggerWaterAlert -> tela azul VISIVEL")
            _isAlertVisible.value = true
            _countdownSeconds.value = userSettings.value.ringtoneDurationSeconds

            if (playMedia) {
                val isVibrateOnly = userSettings.value.vibrateOnly
                val isAlertsEnabled = userSettings.value.alertsEnabled

                // Start sound and/or vibration based on settings
                if (isVibrateOnly) {
                    soundAndVibrationManager.vibrateOnly(
                        durationSeconds = userSettings.value.ringtoneDurationSeconds,
                        onFinished = {
                            // Keep overlay open
                        }
                    )
                } else if (isAlertsEnabled) {
                    soundAndVibrationManager.playGentleBellAndVibrate(
                        durationSeconds = userSettings.value.ringtoneDurationSeconds,
                        chimeType = userSettings.value.chimeType,
                        onFinished = {
                            // Keep overlay open
                        }
                    )
                }
            }

            // Countdown timer
            countdownJob?.cancel()
            countdownJob = launch {
                while (_countdownSeconds.value > 0 && _isAlertVisible.value) {
                    delay(1000)
                    _countdownSeconds.value = _countdownSeconds.value - 1
                }
            }
        }
    }

    fun confirmWaterAlert() {
        if (activeReminderId != null) {
            lastDismissedReminderId = activeReminderId
            lastDismissedTimestamp = System.currentTimeMillis()
        }
        com.example.alarm.WaterAlarmService.stop(getApplication())
        val idToCancel = activeReminderId
        if (idToCancel != null) {
            com.example.alarm.NotificationHelper(getApplication()).cancelNotification(idToCancel)
        }
        soundAndVibrationManager.stopAlert()
        countdownJob?.cancel()
        _isAlertVisible.value = false

        val remId = activeReminderId
        if (remId != null) {
            viewModelScope.launch {
                val reminder = db.reminderDao().getAllRemindersOnce().find { it.id == remId }
                if (reminder != null && !reminder.isCompleted) {
                    val logId = repository.addWaterLog(userSettings.value.glassSizeMl)

                    val spZone = java.time.ZoneId.of("America/Sao_Paulo")
                    val nowSp = java.time.LocalTime.now(spZone)
                    val currentTimeStr = String.format("%02d:%02d", nowSp.hour, nowSp.minute)
                    repository.updateReminder(
                        reminder.copy(
                            isCompleted = true,
                            completedTime = currentTimeStr,
                            isSkipped = false,
                            skippedDate = "",
                            skippedTime = "",
                            waterLogId = logId.toInt()
                        )
                    )
                    // Reschedule for tomorrow
                    alarmScheduler.rescheduleReminderForTomorrow(
                        reminderId = reminder.id,
                        time = reminder.time,
                        chimeType = userSettings.value.chimeType,
                        title = reminder.title
                    )
                }
            }
        }
        activeReminderId = null
    }

    fun stopWaterAlert() {
        if (activeReminderId != null) {
            lastDismissedReminderId = activeReminderId
            lastDismissedTimestamp = System.currentTimeMillis()
        }
        com.example.alarm.WaterAlarmService.stop(getApplication())
        val idToCancel = activeReminderId
        if (idToCancel != null) {
            com.example.alarm.NotificationHelper(getApplication()).cancelNotification(idToCancel)
        }
        soundAndVibrationManager.stopAlert()
        countdownJob?.cancel()
        _isAlertVisible.value = false

        val remId = activeReminderId
        if (remId != null) {
            viewModelScope.launch {
                val reminder = allReminders.value.find { it.id == remId }
                if (reminder != null) {
                    val spZone = java.time.ZoneId.of("America/Sao_Paulo")
                    val nowSp = java.time.LocalTime.now(spZone)
                    val timeStr = String.format("%02d:%02d", nowSp.hour, nowSp.minute)
                    val dateStr = repository.getTodayDisplayDateString()
                    repository.updateReminder(
                        reminder.copy(
                            isSkipped = true,
                            skippedDate = dateStr,
                            skippedTime = timeStr,
                            date = getNextOccurrenceDateString(reminder.time)
                        )
                    )
                    // Reschedule for tomorrow
                    alarmScheduler.rescheduleReminderForTomorrow(
                        reminderId = reminder.id,
                        time = reminder.time,
                        chimeType = userSettings.value.chimeType,
                        title = reminder.title
                    )
                }
            }
        }
        activeReminderId = null
    }

    fun toggleReminderCompleted(reminder: Reminder) {
        viewModelScope.launch {
            val currentReminder = db.reminderDao().getAllRemindersOnce().find { it.id == reminder.id } ?: return@launch
            if (!currentReminder.isCompleted) {
                val logId = repository.addWaterLog(userSettings.value.glassSizeMl)
                val spZone = java.time.ZoneId.of("America/Sao_Paulo")
                val nowSp = java.time.LocalTime.now(spZone)
                val timeStr = String.format("%02d:%02d", nowSp.hour, nowSp.minute)
                repository.updateReminder(
                    currentReminder.copy(
                        isCompleted = true,
                        completedTime = timeStr,
                        isSkipped = false,
                        waterLogId = logId.toInt(),
                        date = getNextOccurrenceDateString(currentReminder.time)
                    )
                )
            } else {
                if (currentReminder.waterLogId != 0) {
                    db.waterLogDao().deleteLogById(currentReminder.waterLogId)
                }
                repository.updateReminder(
                    currentReminder.copy(
                        isCompleted = false,
                        completedTime = "",
                        waterLogId = 0,
                        date = getNextOccurrenceDateString(currentReminder.time)
                    )
                )
                // Reschedule alarm since it's back to pending
                alarmScheduler.scheduleReminder(
                    reminderId = currentReminder.id,
                    time = currentReminder.time,
                    chimeType = userSettings.value.chimeType,
                    title = currentReminder.title
                )
            }
        }
    }

    fun updateChimeType(chimeType: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(chimeType = chimeType)
            repository.updateSettings(updated)
            // Play a 2-second preview of the sound
            soundAndVibrationManager.playGentleBellAndVibrate(
                durationSeconds = 2,
                chimeType = chimeType
            )
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            // Mudar o HORARIO cria uma ocorrencia nova: o status de hoje
            // (bebido/esquecido) era do horario antigo e nao vale mais.
            //
            // Sem isto, um lembrete marcado como esquecido continuava esquecido
            // depois de mudar de horario, e ficava mudo o resto do dia --
            // isSkipped bloqueia o triggerWaterAlert, o checkMissedRecentAlert
            // e o poll de 3s. Foi exatamente o que aconteceu no teste das 21:23.
            val anterior = db.reminderDao().getAllRemindersOnce().find { it.id == reminder.id }
            val horarioMudou = anterior != null && anterior.time != reminder.time

            val paraSalvar = if (horarioMudou) {
                android.util.Log.d(
                    "HidrateJa",
                    "updateReminder ${reminder.id}: horario ${anterior?.time} -> ${reminder.time}, limpando status"
                )
                reminder.copy(
                    isCompleted = false,
                    completedTime = "",
                    isSkipped = false,
                    skippedDate = "",
                    skippedTime = "",
                    waterLogId = 0
                )
            } else {
                reminder
            }

            repository.updateReminder(paraSalvar)
            // Reschedule alarm with new time
            alarmScheduler.scheduleReminder(
                reminderId = paraSalvar.id,
                time = paraSalvar.time,
                chimeType = userSettings.value.chimeType,
                title = paraSalvar.title
            )
        }
    }

    fun addReminder(time: String, date: String, title: String) {
        viewModelScope.launch {
            repository.addReminder(Reminder(time = time, date = date, title = title))

            // CORRIGIDO: o delay(200) era corrida desnecessaria -- o insert
            // suspenso do Room ja commitou quando retorna. E findLast pegava
            // conforme a ordenacao por hora, nem sempre o recem-criado.
            val novo = db.reminderDao().getAllRemindersOnce()
                .filter { it.time == time && it.title == title }
                .maxByOrNull { it.id }

            if (novo != null) {
                alarmScheduler.scheduleReminder(
                    reminderId = novo.id,
                    time = novo.time,
                    chimeType = userSettings.value.chimeType,
                    title = novo.title
                )
            }
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            // Cancel alarm first
            alarmScheduler.cancelReminder(reminder.id)
            repository.deleteReminder(reminder)
        }
    }

    fun saveUserSettings(
        name: String,
        dailyGoalMl: Int,
        monthlyGoalLiters: Float,
        glassSizeMl: Int,
        alertsEnabled: Boolean,
        chimeType: String
    ) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(
                userName = name,
                dailyGoalMl = dailyGoalMl,
                monthlyGoalLiters = monthlyGoalLiters,
                glassSizeMl = glassSizeMl,
                alertsEnabled = alertsEnabled,
                // Mantem a exclusividade tambem por aqui: salvar as
                // configuracoes com o som ligado nao pode reintroduzir o
                // estado conflitante dos dois modos ligados juntos.
                vibrateOnly = if (alertsEnabled) false else userSettings.value.vibrateOnly,
                chimeType = chimeType
            )
            repository.updateSettings(updated)
            espelharModoNasPrefs(updated.alertsEnabled, updated.vibrateOnly)
            closeSettingsDialog()
        }
    }

    /**
     * "Ativar Alertas" e "Somente Vibrar" sao um SELETOR DE MODO, nao dois
     * interruptores independentes. Ligar um sempre desliga o outro.
     *
     * Antes os dois podiam ficar ligados ao mesmo tempo. Na pratica o
     * WaterAlarmService ja tratava vibrar como vencedor, entao a tela mostrava
     * "Ligado" nos dois e o som nao saia -- o usuario nao tinha como entender
     * o que estava valendo. Para um app de idoso isso e pior do que um modo a
     * menos.
     *
     * Estados possiveis agora:
     *   alertas ON  + vibrar OFF -> toca o sino e vibra
     *   alertas OFF + vibrar ON  -> so vibra
     *   alertas OFF + vibrar OFF -> silencioso (so a tela azul e a notificacao)
     *
     * Em nenhum caso os lembretes sao cancelados: a tela azul continua
     * aparecendo, inclusive no modo so vibrar. Por isso o reagendamento e
     * feito nos dois lados.
     */
    fun toggleAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(
                alertsEnabled = enabled,
                // Ligar o som desliga o so-vibrar, senao este switch nao teria
                // efeito nenhum e pareceria quebrado.
                vibrateOnly = if (enabled) false else userSettings.value.vibrateOnly
            )
            android.util.Log.d(
                "HidrateJa",
                "toggleAlertsEnabled($enabled) -> alertas=${updated.alertsEnabled} vibrar=${updated.vibrateOnly}"
            )
            repository.updateSettings(updated)
            espelharModoNasPrefs(updated.alertsEnabled, updated.vibrateOnly)
            scheduleAllReminders()
        }
    }

    fun toggleVibrateOnly(enabled: Boolean) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(
                vibrateOnly = enabled,
                // Ligar o so-vibrar desliga o som; desligar devolve o som.
                alertsEnabled = !enabled
            )
            android.util.Log.d(
                "HidrateJa",
                "toggleVibrateOnly($enabled) -> alertas=${updated.alertsEnabled} vibrar=${updated.vibrateOnly}"
            )
            repository.updateSettings(updated)
            espelharModoNasPrefs(updated.alertsEnabled, updated.vibrateOnly)
            // Sem isto o modo so-vibrar podia ficar sem alarme armado.
            scheduleAllReminders()
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundAndVibrationManager.stopAlert()
    }
}
