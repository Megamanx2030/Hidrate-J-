package com.example

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.alarm.AlarmPermissionHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.ui.HydraApp
import com.example.ui.MainViewModel
import com.example.ui.screens.AberturaAnimada
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ui.theme.HydraCompanionTheme

class MainActivity : ComponentActivity() {

    private companion object {
        const val TAG = "HidrateJa"
    }

    private val viewModel: MainViewModel by viewModels()

    // Evita repetir o aviso a cada volta para o app dentro da mesma sessao.
    private var permissionDialogShown = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* resultado tratado no onResume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        val veioDoAlarme = intent?.getBooleanExtra("from_notification", false) == true
        android.util.Log.d(TAG, "MainActivity.onCreate veioDoAlarme=$veioDoAlarme")

        // O splash do sistema sai na hora: quem segura a marca na tela agora e a
        // AberturaAnimada, desenhada em Compose. Prender o splash do sistema
        // aqui so somaria uma espera parada antes da animacao comecar.
        splashScreen.setKeepOnScreenCondition { false }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // NOVO: em bloqueio sem senha (deslizar), isso dispensa o keyguard
            // automaticamente. Em bloqueio com senha o Android exige o
            // desbloqueio -- comportamento correto, nao da para contornar.
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        enableEdgeToEdge()

        askNotificationPermission()

        setContent {
            HydraCompanionTheme {
                // Quando o app foi aberto PELO ALARME nao ha abertura nenhuma:
                // a tela azul do lembrete tem que aparecer imediatamente.
                var mostrarAbertura by androidx.compose.runtime.saveable.rememberSaveable {
                    androidx.compose.runtime.mutableStateOf(!veioDoAlarme)
                }

                HydraApp(viewModel = viewModel)

                if (mostrarAbertura) {
                    AberturaAnimada(onTerminou = { mostrarAbertura = false })
                }
            }
        }

        window.decorView.post {
            handleIntent(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        // Ver o comentario do verificador de 3s na MainViewModel: fora da tela
        // ele nao pode abrir nada.
        viewModel.definirAppEmPrimeiroPlano(false)
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d(TAG, "MainActivity.onResume")
        viewModel.definirAppEmPrimeiroPlano(true)

        // Abrir o app pelo icone com a task ja existente NAO recria a Activity
        // nem a ViewModel (o sistema so traz a task para frente), entao o init
        // da ViewModel nao roda e a varredura de alerta perdido nunca
        // acontecia. Por isso ela precisa estar aqui tambem.
        viewModel.checkMissedAlertOnResume()

        // A checagem fica no onResume porque o usuario volta para ca depois
        // de conceder (ou nao) a permissao na tela de configuracoes.
        checkAlarmPermissions()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        android.util.Log.d(TAG, "MainActivity.onNewIntent")
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra("from_notification", false) == true) {
            val reminderId = intent.getIntExtra("reminder_id", -1)
            val alertTimestamp = intent.getLongExtra("alert_timestamp", 0L)

            intent.replaceExtras(android.os.Bundle())
            setIntent(intent)

            val isTooOld = System.currentTimeMillis() - alertTimestamp > 2 * 60 * 1000
            val jaVisivel = viewModel.isAlertVisible.value

            android.util.Log.d(
                TAG,
                "MainActivity.handleIntent reminderId=$reminderId isTooOld=$isTooOld jaVisivel=$jaVisivel"
            )

            if (reminderId != -1 && !isTooOld && !jaVisivel) {
                // O WaterAlarmService ja esta tocando o som, entao aqui
                // so exibimos a tela azul.
                viewModel.triggerWaterAlert(reminderId, playMedia = false)
            } else {
                android.util.Log.d(TAG, "MainActivity.handleIntent NAO abriu a tela azul")
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * ESTA E A CORRECAO PRINCIPAL.
     *
     * As tres permissoes abaixo estao declaradas no manifest, mas declarar
     * nao e o mesmo que ter. Quando falta alguma, o alarme falha EM SILENCIO:
     * nao aparece erro, nao trava, simplesmente nao acontece nada no horario.
     *
     * Aviso em linguagem simples, com um botao so, pensado para idoso.
     */
    private fun checkAlarmPermissions() {
        if (permissionDialogShown) return
        if (viewModel.isAlertVisible.value) return  // nao atrapalha o alarme tocando

        val semTelaCheia = !AlarmPermissionHelper.canUseFullScreenIntent(this)
        val semAlarmeExato = !AlarmPermissionHelper.canScheduleExactAlarms(this)
        val semBateria = !AlarmPermissionHelper.isIgnoringBatteryOptimizations(this)

        if (!semTelaCheia && !semAlarmeExato && !semBateria) return

        permissionDialogShown = true

        val (mensagem, acao) = when {
            semAlarmeExato -> Pair(
                "O aplicativo precisa de permissão para avisar na hora certa. " +
                "Sem isso, o lembrete pode atrasar ou não tocar.\n\n" +
                "Toque em LIBERAR e ative a opção que aparecer.",
                { AlarmPermissionHelper.openExactAlarmSettings(this) }
            )
            semTelaCheia -> Pair(
                "O aplicativo precisa de permissão para mostrar o aviso na tela " +
                "cheia quando o celular estiver bloqueado.\n\n" +
                "Toque em LIBERAR e ative a opção que aparecer.",
                { AlarmPermissionHelper.openFullScreenIntentSettings(this) }
            )
            else -> Pair(
                "O celular está economizando bateria e pode desligar os lembretes " +
                "quando o aplicativo ficar um tempo fechado.\n\n" +
                "Toque em LIBERAR e escolha PERMITIR.",
                { AlarmPermissionHelper.requestIgnoreBatteryOptimizations(this) }
            )
        }

        AlertDialog.Builder(this)
            .setTitle("Falta uma permissão")
            .setMessage(mensagem)
            .setPositiveButton("LIBERAR") { _, _ ->
                acao()
                // Permite reavaliar na proxima volta ao app, caso ainda falte outra.
                permissionDialogShown = false
            }
            .setNegativeButton("Agora não", null)
            .setCancelable(false)
            .show()
    }
}
