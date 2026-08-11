package com.example.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * As tres permissoes que fazem o alarme falhar EM SILENCIO.
 *
 * Todas as tres estao declaradas no manifest, mas declarar nao e o mesmo
 * que ter. No Android moderno o usuario precisa conceder cada uma na mao,
 * e quando falta uma nao aparece erro nenhum -- o alarme simplesmente
 * nao acontece.
 */
object AlarmPermissionHelper {

    // ---------- 1. Tela cheia (a tela azul) ----------

    /**
     * No Android 14+ esta permissao vem NEGADA por padrao para apps que nao
     * sao de chamada ou despertador. Sem ela, o setFullScreenIntent e
     * rebaixado para notificacao comum e a tela azul nunca abre.
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canUseFullScreenIntent()
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------- 2. Alarme exato ----------

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------- 3. Otimizacao de bateria ----------

    /**
     * Esta e a que mais quebra alarme na pratica, principalmente em
     * Samsung e Xiaomi. Sem isencao, o sistema mata o app depois de
     * algumas horas sem uso e os alarmes param de tocar.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Abre a LISTA de otimizacao de bateria do sistema.
     *
     * Antes o app usava ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, que abre
     * uma caixinha "permitir?" e resolve em um toque -- so que ela exige a
     * permissao REQUEST_IGNORE_BATTERY_OPTIMIZATIONS declarada, e a politica da
     * Play so aceita essa permissao em app de saude em tempo real, navegacao ou
     * chamada. Lembrete de agua nao entra na lista.
     *
     * Esta tela aqui nao exige permissao nenhuma. Em compensacao ela e a lista
     * de TODOS os aplicativos: a pessoa precisa achar o "Hidrate Ja" e marcar
     * "Nao otimizar". Por isso o aviso que leva ate aqui explica o passo a
     * passo -- ver checkAlarmPermissions na MainActivity.
     */
    fun abrirListaDeOtimizacaoDeBateria(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // Fabricante sem essa tela: cai para os detalhes do proprio app,
            // que sempre existe e tem o item de bateria dentro.
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    // ---------- 4. Aviso por cima de outro aplicativo ----------

    /**
     * SEM ESTA, A TELA AZUL SO APARECE COM O CELULAR BLOQUEADO.
     *
     * Com o celular desbloqueado o Android nao honra o setFullScreenIntent
     * (rebaixa para notificacao heads-up), entao quem precisa abrir a tela
     * azul e um startActivity vindo do alarme. So que iniciar Activity a
     * partir do background e bloqueado por padrao -- confirmado no logcat
     * deste projeto com "Background activity launch blocked".
     *
     * Ter esta permissao e uma das isencoes de BAL previstas no AOSP, e por
     * ser do AOSP nenhum fabricante consegue barrar.
     *
     * NAO e obrigatoria: sem ela o app segue avisando por som, vibracao e
     * notificacao. So a tela azul com o celular desbloqueado deixa de sair.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return try {
            Settings.canDrawOverlays(context)
        } catch (e: Exception) {
            false
        }
    }

    fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Alguns fabricantes nao aceitam o atalho com o pacote.
            // Cai para a lista geral.
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    /** True quando as quatro estao OK. Use para mostrar um aviso na tela inicial. */
    fun allGranted(context: Context): Boolean =
        canUseFullScreenIntent(context) &&
        canScheduleExactAlarms(context) &&
        canDrawOverlays(context) &&
        isIgnoringBatteryOptimizations(context)
}
