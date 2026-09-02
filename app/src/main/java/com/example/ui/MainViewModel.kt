package com.example.ui

import com.example.utils.Zona

import com.example.utils.Registro

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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
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

    /**
     * O DIA DE HOJE COMO ESTADO, E NAO COMO UM VALOR LIDO UMA VEZ.
     *
     * ISTO E O QUE ZERA A META NA VIRADA DO DIA.
     *
     * Antes o todayTotalMl era criado uma vez, no nascimento da ViewModel, com
     * a data daquele instante gravada dentro da consulta. Quem deixasse o app
     * aberto (ou so em segundo plano, que e o normal num app de lembrete)
     * atravessando a meia-noite continuava vendo a agua de ONTEM somada: a
     * jarra ficava cheia, a porcentagem nao voltava para 0% e a meta do dia
     * novo ja comecava batida.
     *
     * Agora o dia e um estado observado. Quando ele muda, a consulta e refeita
     * sozinha para a data nova -- e como os registros sao gravados por dia, o
     * total do dia novo comeca naturalmente em zero. O mesmo gatilho reaproveita
     * a virada para zerar o status dos lembretes e rearmar os alarmes.
     */
    private val _diaDeHoje = MutableStateFlow(repository.getTodayDateString())

    /**
     * DECLARADO AQUI EM CIMA DE PROPOSITO, ANTES DO BLOCO init.
     *
     * Em Kotlin as propriedades sao inicializadas na ordem em que aparecem no
     * arquivo, e o init corre junto com elas. Este campo estava declarado
     * DEPOIS do init, que chama o iniciarVerificadorDeMinuto() -- ou seja, o
     * verificador tentava coletar um Flow que ainda era nulo e o app morria com
     * NullPointerException antes de mostrar a primeira tela.
     *
     * Mover a declaracao para cima e a correcao; nao ha nada de especial no
     * campo em si.
     */
    private val _appEmPrimeiroPlano = MutableStateFlow(false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val todayTotalMl: StateFlow<Int> = _diaDeHoje
        .flatMapLatest { dia -> repository.getTotalMlForDate(dia) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /*
     * REMOVIDO O TOTAL MENSAL, E NAO E PERDA DE FUNCAO: ELE NUNCA APARECEU.
     *
     * Existia uma corrente inteira -- consulta no DAO, funcao no repositorio,
     * StateFlow aqui, parametro na HomeScreen e cinco variaveis calculadas la
     * dentro (progresso, litros bebidos, meta, quanto falta) -- e NENHUMA delas
     * era desenhada na tela. O app somava o mes inteiro a cada registro de agua
     * para jogar o resultado fora.
     *
     * Pior: a conta usava base diferente do resto. Todo o app soma por
     * dateString ("2026-09-02"), o texto que fica gravado no registro; so o
     * mensal somava por timestamp entre dois instantes. Nos registros antigos,
     * gravados quando o app usava o fuso fixo de Sao Paulo, os dois podem
     * discordar perto da meia-noite -- ou seja, a corrente morta ainda era a
     * unica parte do app que podia dar um numero diferente do historico.
     *
     * E o monthlyGoalLiters do UserSettings? Continua no banco (tirar coluna
     * pede migracao e nao vale o risco), mas e igualmente inerte: vale 60 L
     * para todo mundo, nao ha tela nenhuma para muda-lo, e ele nem bate com a
     * meta diaria -- 2,5 L por dia dao 75 L num mes de 30 dias, nao 60.
     *
     * QUEM MOSTRA TOTAL DE PERIODO E A TELA DE HISTORICO, e ela faz a conta
     * certa: le os dias pelo mesmo dateString e soma os registros que leu. Para
     * ver um mes fechado, basta escolher do dia 1 ao dia 30 em "Escolher Datas".
     */

    val allReminders: StateFlow<List<Reminder>> = repository.allReminders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * O periodo que a tela de Historico esta mostrando, em "yyyy-MM-dd".
     *
     * Padrao: os ultimos 7 dias, o mesmo que a tela abre mostrando.
     */
    private val _periodoHistorico = MutableStateFlow(periodoPadrao())
    val periodoHistorico: StateFlow<Pair<String, String>> = _periodoHistorico.asStateFlow()

    /**
     * ISTO E O BUG DO "HISTORICO PAROU DE REGISTRAR".
     *
     * O SINTOMA: a pessoa bebe agua, confirma, a jarra da tela inicial sobe --
     * e o Historico continua mostrando o mesmo de sempre, sem a agua de hoje.
     * Parece que o app parou de gravar. Nao parou: o registro esta no banco. O
     * que estava errado era a JANELA que a tela pedia.
     *
     * A CAUSA: _periodoHistorico nascia com "os ultimos 7 dias" calculados uma
     * unica vez, no nascimento da ViewModel, e nunca mais era recalculado. O
     * observarViradaDoDia cuidava do total de hoje e do reset dos lembretes,
     * mas ninguem lembrava do periodo do historico.
     *
     * POR QUE ISSO APARECE TANTO NESTE APP: a ViewModel de um app de lembrete
     * vive dias. A Activity e singleTask e fica na pilha; a pessoa nao "fecha"
     * o app, so volta para a tela inicial do celular. Quem abriu no dia 2 e
     * voltou no dia 6 ainda tinha a tela pedindo os dias 27 a 2 -- e o dia 6
     * simplesmente nao estava na consulta. So matar o app pela lista de
     * recentes resolvia, o que ninguem descobre sozinho.
     *
     * A CORRECAO: na virada do dia (e ao voltar para o app), a janela padrao
     * anda junto com o calendario. Mas SO a padrao: se a pessoa escolheu um
     * periodo no calendario da tela, aquilo foi uma decisao dela e nao pode ser
     * trocada pelas costas -- e para isso que serve a marca abaixo.
     */
    private var periodoEscolhidoPeloUsuario = false

    fun definirPeriodoHistorico(inicio: String, fim: String) {
        periodoEscolhidoPeloUsuario = true
        val novo = inicio to fim
        if (_periodoHistorico.value != novo) _periodoHistorico.value = novo
    }

    /** Ver o comentario de periodoEscolhidoPeloUsuario. */
    private fun deslizarPeriodoPadraoSeNecessario() {
        if (periodoEscolhidoPeloUsuario) return
        val novo = periodoPadrao()
        if (_periodoHistorico.value != novo) {
            Registro.d("Historico: janela padrao deslizou para $novo")
            _periodoHistorico.value = novo
        }
    }

    private fun periodoPadrao(): Pair<String, String> {
        val zona = Zona.id()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val hoje = java.time.LocalDate.now(zona)
        return hoje.minusDays(6).format(fmt) to hoje.format(fmt)
    }

    /**
     * So os registros do periodo mostrado.
     *
     * Antes era um allLogs com a tabela inteira, reemitido a cada copo de agua
     * registrado. Ver o comentario do WaterLogDao.getLogsForDateRange.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val logsDoPeriodo: StateFlow<List<WaterLog>> = _periodoHistorico
        .flatMapLatest { (inicio, fim) -> repository.getLogsForDateRange(inicio, fim) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Quantos registros existem no total. Usado no aviso do botao Limpar. */
    val totalDeRegistros: StateFlow<Int> = repository.totalDeRegistros
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
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

        observarViradaDoDia()

        iniciarVerificadorDeMinuto()
    }

    /**
     * Rede de seguranca para quando o AlarmManager atrasa com o app ABERTO na
     * frente do usuario. Som e vibracao continuam sendo do AlarmManager + do
     * WaterAlarmService; aqui so aparece a tela azul.
     *
     * DUAS ECONOMIAS DE BATERIA, PELO MESMO MOTIVO: ISTO NAO E O QUE DISPARA O
     * ALARME, E SIM UM PARA-QUEDAS.
     *
     * 1. O intervalo era de 3 segundos -- 1.200 despertares de CPU por hora.
     *    Como a comparacao e por MINUTO (it.time == currentHHmm), 30 segundos
     *    garante do mesmo jeito que nenhum minuto passe batido, com 90% menos
     *    despertares.
     *
     * 2. O laco era um while(true) eterno que apenas PULAVA a checagem quando o
     *    app estava em segundo plano -- ou seja, continuava acordando a CPU a
     *    noite inteira para nao fazer nada. Agora ele vive dentro de um
     *    collectLatest do estado de primeiro plano: quando o app sai da tela, o
     *    collectLatest CANCELA o bloco anterior e o laco deixa de existir. Zero
     *    despertar em segundo plano. E o mesmo efeito do repeatOnLifecycle, so
     *    que valendo para a ViewModel, que e onde o laco mora.
     */
    private fun iniciarVerificadorDeMinuto() {
        viewModelScope.launch {
            _appEmPrimeiroPlano.collectLatest { emPrimeiroPlano ->
                if (!emPrimeiroPlano) return@collectLatest

                var ultimoMinutoDisparado: String? = null
                val spZone = Zona.id()
                while (true) {
                    // A checagem vem ANTES da espera: assim o primeiro giro
                    // acontece no instante em que o app volta para a tela, e nao
                    // meio minuto depois.
                    if (_isAlertVisible.value) {
                        delay(30_000)
                        continue
                    }

                    val nowSp = java.time.LocalTime.now(spZone)
                    val currentHHmm = String.format("%02d:%02d", nowSp.hour, nowSp.minute)

                    if (currentHHmm != ultimoMinutoDisparado) {
                        val matchingReminder = allReminders.value
                            .find { it.time == currentHHmm && !it.isCompleted && !it.isSkipped }
                        if (matchingReminder != null) {
                            ultimoMinutoDisparado = currentHHmm
                            Registro.d("Verificador achou o lembrete ${matchingReminder.id} em $currentHHmm")
                            triggerWaterAlert(matchingReminder.id, playMedia = false)
                        }
                    }

                    delay(30_000)
                }
            }
        }
    }

    /** Chamado pelo onResume/onPause da MainActivity. */
    fun definirAppEmPrimeiroPlano(emPrimeiroPlano: Boolean) {
        _appEmPrimeiroPlano.value = emPrimeiroPlano
    }

    /**
     * Vigia da virada do dia.
     *
     * O reset ja existia, mas so rodava ao criar a ViewModel e no onResume --
     * ou seja, dependia do usuario ABRIR o app depois da meia-noite. Quem
     * deixasse o telefone de lado via, na manha seguinte, a agua de ontem ainda
     * somada e a meta ja batida.
     *
     * Aqui a virada e percebida sozinha. Meio minuto de folga e de sobra: nao
     * existe nada urgente acontecendo a meia-noite, e um laco mais apertado so
     * gastaria bateria.
     */
    private fun observarViradaDoDia() {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                val hoje = repository.getTodayDateString()
                if (_diaDeHoje.value != hoje) {
                    Registro.d("Virou o dia: ${_diaDeHoje.value} -> $hoje")
                    _diaDeHoje.value = hoje
                    // Sem esta linha o Historico fica preso nos 7 dias de
                    // quando o app foi aberto. Ver deslizarPeriodoPadraoSeNecessario.
                    deslizarPeriodoPadraoSeNecessario()
                    resetDailyStatusIfNewDay()
                    markMissedRemindersAsSkipped()
                    scheduleAllReminders()
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
        val spZone = Zona.id()
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
            Registro.d("Modos conflitantes gravados: mantendo Somente Vibrar")
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
        val spZone = Zona.id()
        val nowSp = java.time.LocalTime.now(spZone)
        val currentHHmm = String.format("%02d:%02d", nowSp.hour, nowSp.minute)
        val dateStr = repository.getTodayDisplayDateString()

        db.reminderDao().getAllRemindersOnce().forEach { reminder ->
            if (!reminder.isCompleted && !reminder.isSkipped && reminder.time < currentHHmm) {
                val parts = reminder.time.split(":")
                if (parts.size == 2) {
                    val h = parts[0].toIntOrNull() ?: 0
                    val m = parts[1].toIntOrNull() ?: 0

                    /**
                     * NAO SE ESQUECE UM LEMBRETE QUE AINDA NAO EXISTIA.
                     *
                     * Quem instalava o app as 17:30 abria a tela de Lembretes e
                     * encontrava SEIS avisos vermelhos -- "Esqueceu hoje as
                     * 10:20", "Esqueceu hoje as 11:40"... -- por horarios da
                     * agenda de fabrica criados trinta segundos antes. A
                     * primeira coisa que o aplicativo dizia a pessoa era que ela
                     * tinha falhado.
                     *
                     * Vale para a agenda de fabrica e para qualquer lembrete
                     * criado a mao: quem cadastra as 17h um horario das 10h nao
                     * pode ver "esqueceu" no mesmo segundo.
                     *
                     * Lembretes antigos tem criadoEmMs = 0 e continuam como
                     * antes -- zero e anterior a qualquer horario de hoje.
                     */
                    val instanteDeHoje = java.time.LocalDate.now(spZone)
                        .atTime(h, m)
                        .atZone(spZone)
                        .toInstant()
                        .toEpochMilli()
                    if (instanteDeHoje < reminder.criadoEmMs) {
                        Registro.d("Lembrete ${reminder.id} das ${reminder.time} ainda nao existia: nao e esquecido")
                        return@forEach
                    }

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
            Registro.d("onResume: alerta ja visivel, nao checa")
            return
        }
        // Voltar ao app depois da meia-noite tem que refazer a consulta do dia
        // na mesma hora, sem esperar o proximo giro do vigia.
        val hoje = repository.getTodayDateString()
        if (_diaDeHoje.value != hoje) {
            Registro.d("onResume: dia mudou para $hoje")
            _diaDeHoje.value = hoje
        }
        // Fora do "if" de proposito: o vigia da virada pode ter atualizado o
        // _diaDeHoje enquanto o app estava em segundo plano e ter deixado a
        // janela do historico para tras. Aqui a conferencia e barata e a
        // funcao nao faz nada quando ja esta certo.
        deslizarPeriodoPadraoSeNecessario()
        viewModelScope.launch {
            Registro.d("onResume: virada de dia + alerta perdido")

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

    /**
     * ESTA E A CORRECAO DA TELA AZUL QUE ABRIA SOZINHA.
     *
     * O QUE ACONTECIA: esta funcao varria o banco atras de "qualquer lembrete
     * cujo horario passou ha menos de 15 minutos e que nao foi confirmado" e
     * abria a tela azul em cima disso. Horario passado NAO e prova de que o
     * alarme tocou. Como ela roda a cada onResume, bastava abrir o app -- ou
     * voltar da tela de permissoes, ou de outro aplicativo -- dentro daquela
     * janela de 15 minutos para a tela azul aparecer do nada. Pior: logo depois
     * da virada do dia, quando o reset zera o status de todos os lembretes,
     * qualquer horario recente virava candidato.
     *
     * O QUE FAZ AGORA: le o registro que o WaterReminderReceiver grava quando o
     * alarme DISPARA de verdade, e consome esse registro (le uma vez e apaga).
     * Sem alarme disparado nao existe tela azul, e um mesmo disparo nunca abre
     * a tela duas vezes.
     *
     * A conferencia final continua no triggerWaterAlert; aqui ja descartamos os
     * casos obvios -- inclusive o do lembrete que foi APAGADO e deixou um alarme
     * orfao para tras.
     */
    private suspend fun checkMissedRecentAlert() {
        val id = com.example.alarm.DisparoAlarmePrefs.consumir(
            getApplication(),
            JANELA_CARENCIA_MIN * 60_000L
        )
        if (id == null) {
            Registro.d("Nenhum alarme disparado recentemente")
            return
        }

        val reminder = db.reminderDao().getAllRemindersOnce().find { it.id == id }
        if (reminder == null) {
            Registro.d("Alarme $id disparou para lembrete que nao existe mais")
            return
        }
        if (reminder.isCompleted || reminder.isSkipped) {
            Registro.d("Alarme $id ja foi resolvido, sem tela azul")
            return
        }

        Registro.d("Alerta perdido: ${reminder.id} das ${reminder.time}")
        _pendingAlertReminderId.value = reminder.id
    }

    /**
     * A REGRA DE REARME SAIU DAQUI.
     *
     * Ela existia em duas copias -- esta e a do receiver de boot -- e as duas
     * ja tinham divergido: so esta excluia o lembrete que esta disparando neste
     * minuto. Agora as duas chamam o ReagendarAlarmes, que e o unico dono da
     * regra. Ver o comentario de la.
     */
    private fun scheduleAllReminders() {
        viewModelScope.launch {
            com.example.alarm.ReagendarAlarmes.tudo(getApplication(), "app aberto")
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
            Registro.d("triggerWaterAlert id=$reminderId playMedia=$playMedia")

            // Dois caminhos podem pedir a MESMA tela azul quase junto: o
            // handleIntent (que veio do alarme) e o alerta perdido do onResume.
            // A conferencia que existia no onResume nao pegava esse caso porque
            // o triggerWaterAlert e assincrono: quando ela rodava, a tela ainda
            // nao tinha sido marcada como visivel. Sem esta guarda a contagem de
            // 10 segundos reiniciava do zero no meio do alerta.
            if (_isAlertVisible.value && reminderId != null && reminderId == activeReminderId) {
                Registro.d("triggerWaterAlert ABORTOU: ja esta na tela")
                return@launch
            }

            if (reminderId != null) {
                // Modo silencioso: os dois interruptores desligados significam
                // "so a notificacao na barra". Este e o ponto unico que fecha
                // TODOS os caminhos de exibicao vindos de lembrete -- o poll de
                // 3s, o handleIntent e o alerta perdido. O botao de testar
                // alerta continua funcionando porque passa reminderId nulo.
                val s = userSettings.value
                if (!s.alertsEnabled && !s.vibrateOnly) {
                    Registro.d("triggerWaterAlert ABORTOU: modo silencioso")
                    return@launch
                }

                val reminder = db.reminderDao().getAllRemindersOnce().find { it.id == reminderId }
                if (reminder != null && (reminder.isCompleted || reminder.isSkipped)) {
                    Registro.d("triggerWaterAlert ABORTOU: isCompleted=${reminder.isCompleted} isSkipped=${reminder.isSkipped}"
                    )
                    return@launch
                }
                if (reminderId == lastDismissedReminderId && (System.currentTimeMillis() - lastDismissedTimestamp < 2 * 60 * 1000)) {
                    Registro.d("triggerWaterAlert ABORTOU: dispensado ha menos de 2 min")
                    return@launch
                }
            }

            activeReminderId = reminderId
            Registro.d("triggerWaterAlert -> tela azul VISIVEL")
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
        // O disparo ja foi atendido aqui na tela; nao pode sobrar registro para
        // reabrir a tela azul na proxima vez que o app for aberto.
        com.example.alarm.DisparoAlarmePrefs.limpar(getApplication())
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

                    val spZone = Zona.id()
                    val nowSp = java.time.LocalTime.now(spZone)
                    val currentTimeStr = String.format("%02d:%02d", nowSp.hour, nowSp.minute)
                    repository.updateReminder(
                        reminder.copy(
                            isCompleted = true,
                            completedTime = currentTimeStr,
                            isSkipped = false,
                            // skippedDate e skippedTime NAO sao apagados de
                            // proposito: e o que permite mostrar "Esqueceu as
                            // 06:00, mas bebeu depois as 09:30" nas duas telas.
                            // Antes eram zerados aqui e a informacao de que o
                            // lembrete tinha sido esquecido sumia para sempre.
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
        com.example.alarm.DisparoAlarmePrefs.limpar(getApplication())
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
                    val spZone = Zona.id()
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
                val spZone = Zona.id()
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
                Registro.d("updateReminder ${reminder.id}: horario ${anterior?.time} -> ${reminder.time}, limpando status"
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
            // Mesmo motivo da agenda de fabrica: quem cria as 17h um lembrete
            // para as 10h nao pode ver "Esqueceu hoje as 10:00" no mesmo
            // segundo. Ver criadoEmMs no Reminder.
            repository.addReminder(
                Reminder(
                    time = time,
                    date = date,
                    title = title,
                    criadoEmMs = System.currentTimeMillis()
                )
            )

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

    /**
     * [pesoKg] nulo quer dizer "nao mexe no peso guardado".
     *
     * Esta funcao e chamada de dois lugares muito diferentes: da tela de
     * Configurar, que sabe o peso, e do dialogo de adicionar agua, que so quer
     * trocar o tamanho do copo e nao tem nada a ver com peso. Sem o nulo, o
     * segundo caminho teria que repassar o peso corretamente todas as vezes --
     * e no dia em que alguem esquecesse, o peso da pessoa seria zerado em
     * silencio ao ela registrar um copo de agua.
     */
    fun saveUserSettings(
        name: String,
        dailyGoalMl: Int,
        monthlyGoalLiters: Float,
        glassSizeMl: Int,
        alertsEnabled: Boolean,
        chimeType: String,
        pesoKg: Int? = null
    ) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(
                userName = name,
                dailyGoalMl = dailyGoalMl,
                monthlyGoalLiters = monthlyGoalLiters,
                glassSizeMl = glassSizeMl,
                pesoKg = pesoKg ?: userSettings.value.pesoKg,
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
            Registro.d("toggleAlertsEnabled($enabled) -> alertas=${updated.alertsEnabled} vibrar=${updated.vibrateOnly}"
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
            Registro.d("toggleVibrateOnly($enabled) -> alertas=${updated.alertsEnabled} vibrar=${updated.vibrateOnly}"
            )
            repository.updateSettings(updated)
            espelharModoNasPrefs(updated.alertsEnabled, updated.vibrateOnly)
            // Sem isto o modo so-vibrar podia ficar sem alarme armado.
            scheduleAllReminders()
        }
    }

    /**
     * Tira o lembrete da lista de "esquecidos" SEM apagar o lembrete.
     *
     * O horario e o alarme continuam intactos: some apenas o registro de que
     * ele foi esquecido, para a lista nao acumular. Apagar o lembrete de
     * verdade e outra acao, na tela de Lembretes.
     */
    fun limparRegistroEsquecido(reminder: Reminder) {
        viewModelScope.launch {
            Registro.d("Limpando registro de esquecido do lembrete ${reminder.id}")
            repository.updateReminder(
                reminder.copy(
                    isSkipped = false,
                    skippedDate = "",
                    skippedTime = ""
                )
            )
        }
    }

    /**
     * Apaga o consumo de agua de um dia inteiro.
     *
     * Antes de apagar, guarda os ids dos registros para tambem zerar qualquer
     * lembrete que apontasse para eles pelo waterLogId. Sem isso o lembrete
     * continuaria marcado como "Bebido" apontando para um registro que nao
     * existe mais, e o total do dia nao bateria com a tela de lembretes.
     */
    fun apagarConsumoDoDia(dateKey: String) = apagarConsumoDoPeriodo(dateKey, dateKey)

    /**
     * Apaga o consumo de agua de um intervalo de dias.
     *
     * E o que o botao Limpar do Historico usa. Antes ele so sabia apagar TUDO,
     * de todos os dias, mesmo com a tela mostrando um periodo escolhido -- quem
     * filtrasse uma semana e tocasse em Limpar perdia anos de registro sem que
     * a tela avisasse.
     */
    fun apagarConsumoDoPeriodo(inicio: String, fim: String) {
        viewModelScope.launch {
            val logs = db.waterLogDao().getLogsForDateRangeOnce(inicio, fim)
            val idsApagados = logs.map { it.id }.toSet()
            Registro.d("Apagando consumo de $inicio a $fim: ${logs.size} registro(s)"
            )

            db.waterLogDao().deleteLogsForDateRange(inicio, fim)

            // Um lembrete marcado como "Bebido" que aponta para um registro
            // apagado tem que voltar a pendente, senao o total do dia deixa de
            // bater com a tela de Lembretes.
            db.reminderDao().getAllRemindersOnce().forEach { reminder ->
                if (reminder.waterLogId != 0 && idsApagados.contains(reminder.waterLogId)) {
                    repository.updateReminder(
                        reminder.copy(
                            isCompleted = false,
                            completedTime = "",
                            waterLogId = 0
                        )
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundAndVibrationManager.stopAlert()
    }
}
