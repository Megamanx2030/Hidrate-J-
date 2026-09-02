package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.AddWaterDialog
import com.example.ui.screens.AlertOverlay
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryContainer

@Composable
fun HydraApp(viewModel: MainViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val todayTotalMl by viewModel.todayTotalMl.collectAsStateWithLifecycle()
    val reminders by viewModel.allReminders.collectAsStateWithLifecycle()
    val logsDoPeriodo by viewModel.logsDoPeriodo.collectAsStateWithLifecycle()
    val totalDeRegistros by viewModel.totalDeRegistros.collectAsStateWithLifecycle()

    val isAlertVisible by viewModel.isAlertVisible.collectAsStateWithLifecycle()
    val countdownSeconds by viewModel.countdownSeconds.collectAsStateWithLifecycle()

    val showSettingsDialog by viewModel.showSettingsDialog.collectAsStateWithLifecycle()
    val showAddWaterDialog by viewModel.showAddWaterDialog.collectAsStateWithLifecycle()
    val pendingAlertReminderId by viewModel.pendingAlertReminderId.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(pendingAlertReminderId) {
        pendingAlertReminderId?.let { id ->
            viewModel.triggerWaterAlert(id, playMedia = false)
            viewModel.clearPendingAlert()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 8.dp
                ) {
                    // Tab 0: Início
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "Início",
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Início",
                                // labelLarge (20sp) nao cabe na largura de um
                                // terco de tela: com a fonte do sistema
                                // aumentada, "Lembretes" quebrava em
                                // "Lembrete" / "s". labelMedium continua
                                // grande para um app de idoso e cabe inteiro.
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = Color.White
                        ),
                        modifier = Modifier.testTag("nav_home")
                    )

                    // Tab 1: Histórico
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 1) Icons.Filled.History else Icons.Outlined.History,
                                contentDescription = "Histórico",
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Histórico",
                                // labelLarge (20sp) nao cabe na largura de um
                                // terco de tela: com a fonte do sistema
                                // aumentada, "Lembretes" quebrava em
                                // "Lembrete" / "s". labelMedium continua
                                // grande para um app de idoso e cabe inteiro.
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = Color.White
                        ),
                        modifier = Modifier.testTag("nav_history")
                    )

                    // Tab 2: Lembretes
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 2) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                contentDescription = "Lembretes",
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Lembretes",
                                // labelLarge (20sp) nao cabe na largura de um
                                // terco de tela: com a fonte do sistema
                                // aumentada, "Lembretes" quebrava em
                                // "Lembrete" / "s". labelMedium continua
                                // grande para um app de idoso e cabe inteiro.
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = Color.White
                        ),
                        modifier = Modifier.testTag("nav_reminders")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> HomeScreen(
                        settings = settings,
                        todayTotalMl = todayTotalMl,
                        reminders = reminders,
                        onAddWater = { amount -> viewModel.addWater(amount) },
                        onOpenAddDialog = { viewModel.openAddWaterDialog() },
                        onOpenSettings = { viewModel.openSettingsDialog() }
                    )
                    1 -> HistoryScreen(
                        logs = logsDoPeriodo,
                        reminders = reminders,
                        dailyGoalMl = settings.dailyGoalMl,
                        totalDeRegistros = totalDeRegistros,
                        onClearHistory = { viewModel.clearHistory() },
                        onClearEverything = { viewModel.clearEverything() },
                        onApagarConsumoDoDia = { dateKey -> viewModel.apagarConsumoDoDia(dateKey) },
                        onApagarConsumoDoPeriodo = { inicio, fim ->
                            viewModel.apagarConsumoDoPeriodo(inicio, fim)
                        },
                        onPeriodoMudou = { inicio, fim ->
                            viewModel.definirPeriodoHistorico(inicio, fim)
                        },
                        onLimparRegistroEsquecido = { reminder -> viewModel.limparRegistroEsquecido(reminder) }
                    )
                    2 -> RemindersScreen(
                        settings = settings,
                        reminders = reminders,
                        onToggleAlerts = { enabled -> viewModel.toggleAlertsEnabled(enabled) },
                        onToggleVibrateOnly = { enabled -> viewModel.toggleVibrateOnly(enabled) },
                        onSelectChime = { chimeType -> viewModel.updateChimeType(chimeType) },
                        onTestAlert = { viewModel.triggerWaterAlert() },
                        onUpdateReminder = { reminder -> viewModel.updateReminder(reminder) },
                        onAddReminder = { time, date, title -> viewModel.addReminder(time, date, title) },
                        onDeleteReminder = { reminder -> viewModel.deleteReminder(reminder) },
                        onToggleCompleted = { reminder -> viewModel.toggleReminderCompleted(reminder) }
                    )
                }
            }
        }

        // Fullscreen Water Alert Overlay (Sound + Vibration + Countdown)
        AlertOverlay(
            isVisible = isAlertVisible,
            countdownSeconds = countdownSeconds,
            onConfirm = { viewModel.confirmWaterAlert() },
            onStopAlert = { viewModel.stopWaterAlert() }
        )

        // Settings Dialog
        if (showSettingsDialog) {
            SettingsDialog(
                settings = settings,
                onDismiss = { viewModel.closeSettingsDialog() },
                onSave = { name, dailyGoal, monthlyGoal, glassSize, alertsEnabled, chimeType, pesoKg ->
                    viewModel.saveUserSettings(name, dailyGoal, monthlyGoal, glassSize, alertsEnabled, chimeType, pesoKg)
                }
            )
        }

        // Quick Add Water Dialog
        if (showAddWaterDialog) {
            AddWaterDialog(
                onDismiss = { viewModel.closeAddWaterDialog() },
                onAdd = { amountMl ->
                    viewModel.saveUserSettings(
                        name = settings.userName,
                        dailyGoalMl = settings.dailyGoalMl,
                        monthlyGoalLiters = settings.monthlyGoalLiters,
                        glassSizeMl = amountMl,
                        alertsEnabled = settings.alertsEnabled,
                        chimeType = settings.chimeType
                    )
                    viewModel.closeAddWaterDialog()
                }
            )
        }
    }
}
