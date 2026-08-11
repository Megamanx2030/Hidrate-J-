package com.example.utils

import android.util.Log
import com.example.BuildConfig

/**
 * Registro de depuracao que so escreve na versao de desenvolvimento.
 *
 * POR QUE ISTO EXISTE:
 * o app tinha 46 chamadas soltas de Log.d/Log.w espalhadas pelo alarme, pelo
 * servico e pela ViewModel -- uteis para cacar o bug da tela azul, mas que iam
 * inteiras para a versao publicada. Log em producao custa CPU e disco a cada
 * chamada, e ainda expoe no logcat do aparelho o que o app esta fazendo
 * (horarios dos lembretes, ids, estado do alarme).
 *
 * Envolver cada chamada num "if (BuildConfig.DEBUG)" resolveria, mas seriam 46
 * ifs para alguem esquecer no futuro. Com este objeto a regra fica em um lugar
 * so e a chamada continua curta.
 *
 * O ERRO CONTINUA SENDO REGISTRADO SEMPRE: quando algo falha de verdade em
 * producao, o rastro no logcat e a unica pista que sobra para entender o
 * relato de um usuario.
 */
object Registro {

    private const val ETIQUETA = "HidrateJa"

    fun d(mensagem: String) {
        if (BuildConfig.DEBUG) Log.d(ETIQUETA, mensagem)
    }

    fun w(mensagem: String) {
        if (BuildConfig.DEBUG) Log.w(ETIQUETA, mensagem)
    }

    fun e(mensagem: String, erro: Throwable? = null) {
        if (erro != null) Log.e(ETIQUETA, mensagem, erro) else Log.e(ETIQUETA, mensagem)
    }
}
