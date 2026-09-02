package com.example.utils

/**
 * Sugestao de quanta agua beber por dia a partir do peso.
 *
 * PEDIDO PELOS TESTADORES do teste fechado: "calculo de agua por peso
 * corporal". Ate aqui a pessoa tinha que adivinhar a propria meta, ou aceitar
 * os 2,5 L que vinham de fabrica.
 *
 * ---------------------------------------------------------------------------
 * POR QUE 30 ml/kg E NAO OS 35 QUE A INTERNET REPETE
 * ---------------------------------------------------------------------------
 * A regra de bolso mais citada e uma FAIXA de 30 a 40 ml por quilo, e ela vale
 * para adulto saudavel. Este app nao e para adulto saudavel em geral: ele foi
 * feito para pessoas idosas, e essa escolha esta na primeira linha do README.
 *
 * Isso muda o lado da faixa em que se deve ficar. Com a idade o rim perde
 * capacidade de concentrar urina e de se livrar de agua sobrando, e cresce
 * muito a chance de a pessoa ter insuficiencia cardiaca, doenca renal cronica
 * ou de tomar remedio (diuretico, alguns antidepressivos) que mexe com a
 * retencao de agua. Para quem esta em qualquer dessas situacoes, beber demais
 * nao e inofensivo: dilui o sodio do sangue (hiponatremia) e sobrecarrega
 * coracao e rim.
 *
 * Entao, para ESTE publico, o certo e ficar no piso da faixa e nao no teto.
 * 30 ml/kg da um numero util e continua sendo uma conta de bolso reconhecida;
 * 35 empurraria justamente quem menos pode ser empurrado.
 *
 * O TETO DE 3 L SEGUE A MESMA LOGICA. Ele so aperta acima de 100 kg, e existe
 * para que nenhuma combinacao de peso produza um numero que, sozinho na tela,
 * pareca uma ordem para beber 4 litros.
 *
 * ---------------------------------------------------------------------------
 * ISTO NAO E UM CALCULO MEDICO
 * ---------------------------------------------------------------------------
 * E uma sugestao de ponto de partida, que a pessoa aceita ou nao tocando num
 * botao -- digitar o peso, sozinho, nao muda meta nenhuma. O aviso na tela vem
 * ANTES do numero, nao depois, e cita por nome quem nao deve usar a conta.
 *
 * Nada aqui usa Health Connect, nenhum dado sai do aparelho, e o peso fica no
 * mesmo banco local do tamanho do copo.
 *
 * OS LIMITES DE PESO PROTEGEM DE DEDO ESCORREGADO, nao julgam o corpo de
 * ninguem: fora de 30 a 250 kg a funcao devolve null e a tela simplesmente nao
 * mostra sugestao (quem digita "7" ou "700" errou o teclado).
 */
object MetaPorPeso {

    const val ML_POR_QUILO = 30

    const val PESO_MINIMO_KG = 30
    const val PESO_MAXIMO_KG = 250

    const val META_MINIMA_ML = 1500
    const val META_MAXIMA_ML = 3000

    /**
     * Devolve a meta sugerida em ml, ou null quando o peso esta fora da faixa
     * aceitavel -- e null aqui quer dizer "nao mostre sugestao", nao "erro".
     */
    fun sugerirMl(pesoKg: Int): Int? {
        if (pesoKg < PESO_MINIMO_KG || pesoKg > PESO_MAXIMO_KG) return null
        val bruto = pesoKg * ML_POR_QUILO
        val arredondadoNaCentena = ((bruto + 50) / 100) * 100
        return arredondadoNaCentena.coerceIn(META_MINIMA_ML, META_MAXIMA_ML)
    }
}
