package com.example.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaPorPesoTest {

    @Test
    fun `peso comum vira meta arredondada na centena`() {
        // 70 kg x 30 ml = 2100 ml, ja redondo
        assertEquals(2100, MetaPorPeso.sugerirMl(70))
        // 65 kg x 30 ml = 1950 ml -> 2000 ml
        assertEquals(2000, MetaPorPeso.sugerirMl(65))
        // 90 kg x 30 ml = 2700 ml
        assertEquals(2700, MetaPorPeso.sugerirMl(90))
    }

    @Test
    fun `resultado nunca passa dos limites de 1,5 L a 3 L`() {
        // 30 kg x 30 = 900 ml, abaixo do piso
        assertEquals(MetaPorPeso.META_MINIMA_ML, MetaPorPeso.sugerirMl(30))
        // 250 kg x 30 = 7500 ml, muito acima do teto
        assertEquals(MetaPorPeso.META_MAXIMA_ML, MetaPorPeso.sugerirMl(250))
    }

    @Test
    fun `NENHUM peso pode sugerir mais de 3 litros`() {
        // Esta e a garantia de seguranca do recurso, e vale a pena estar
        // escrita como teste: se alguem mexer na conta ou no teto sem pensar,
        // este teste quebra antes de a versao sair do computador.
        for (peso in MetaPorPeso.PESO_MINIMO_KG..MetaPorPeso.PESO_MAXIMO_KG) {
            val sugestao = MetaPorPeso.sugerirMl(peso)!!
            assertTrue(
                "peso $peso sugeriu ${sugestao}ml, acima do teto de seguranca",
                sugestao <= MetaPorPeso.META_MAXIMA_ML
            )
        }
    }

    @Test
    fun `a conta fica no piso da faixa 30 a 40, nao no teto`() {
        // O publico do app e idoso; ver o comentario longo em MetaPorPeso.
        // Se alguem trocar para 35 ou 40 ml/kg, este teste avisa.
        assertEquals(30, MetaPorPeso.ML_POR_QUILO)
    }

    @Test
    fun `peso fora da faixa nao gera sugestao nenhuma`() {
        // Digitou "7" em vez de "70", ou esqueceu um digito
        assertNull(MetaPorPeso.sugerirMl(7))
        assertNull(MetaPorPeso.sugerirMl(29))
        // Digitou "700" em vez de "70"
        assertNull(MetaPorPeso.sugerirMl(700))
        // Campo vazio chega aqui como 0
        assertNull(MetaPorPeso.sugerirMl(0))
        // Nao existe peso negativo, mas a funcao nao pode estourar por isso
        assertNull(MetaPorPeso.sugerirMl(-5))
    }

    @Test
    fun `sugestao cresce junto com o peso`() {
        var anterior = 0
        for (peso in MetaPorPeso.PESO_MINIMO_KG..MetaPorPeso.PESO_MAXIMO_KG) {
            val atual = MetaPorPeso.sugerirMl(peso)!!
            assertTrue("peso $peso sugeriu $atual, menos que o peso anterior ($anterior)", atual >= anterior)
            anterior = atual
        }
    }
}
