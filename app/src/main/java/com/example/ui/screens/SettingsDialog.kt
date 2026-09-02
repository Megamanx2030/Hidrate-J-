package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.db.UserSettings
import com.example.utils.MetaPorPeso
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.OnErrorContainer
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryContainer

/**
 * Uma escolha pronta, do tamanho de um botao de verdade.
 *
 * O campo de digitar continua existindo logo abaixo, mas ele nao pode ser o
 * unico caminho: escrever "2.5" num teclado numerico -- e acertar o PONTO, que
 * em portugues a pessoa escreveria com virgula -- e onde mais se erra. Tocar
 * em "2,5 L" nao tem como dar errado.
 */
@Composable
private fun EscolhaRapida(
    texto: String,
    selecionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selecionada) PrimaryBlue else Color.White)
            .border(
                width = 2.dp,
                color = if (selecionada) PrimaryBlue else SecondaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selecionada) Color.White else PrimaryBlue,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TituloDoCampo(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
fun SettingsDialog(
    settings: UserSettings,
    onDismiss: () -> Unit,
    onSave: (name: String, dailyGoalMl: Int, monthlyGoalLiters: Float, glassSizeMl: Int, alertsEnabled: Boolean, chimeType: String, pesoKg: Int) -> Unit
) {
    var name by remember { mutableStateOf(settings.userName) }
    var metaMl by remember { mutableStateOf(settings.dailyGoalMl.coerceAtLeast(500)) }
    var copoMl by remember { mutableStateOf(settings.glassSizeMl.coerceAtLeast(50)) }

    // Os campos de digitar seguem as escolhas rapidas, e vice-versa.
    var metaDigitada by remember { mutableStateOf((settings.dailyGoalMl / 1000f).toString().replace('.', ',')) }
    var copoDigitado by remember { mutableStateOf(settings.glassSizeMl.toString()) }

    // Peso zero significa "nunca informou": o campo abre vazio, e nao com um
    // "0" que a pessoa teria que apagar antes de digitar.
    var pesoDigitado by remember {
        mutableStateOf(if (settings.pesoKg > 0) settings.pesoKg.toString() else "")
    }
    val pesoKg = pesoDigitado.toIntOrNull() ?: 0
    val sugestaoMl = MetaPorPeso.sugerirMl(pesoKg)

    val ptBR = java.util.Locale("pt", "BR")
    val coposPorDia = (metaMl / copoMl.coerceAtLeast(1)).coerceAtLeast(1)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = "Minha meta de água",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TituloDoCampo("Como quer ser chamado")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                TituloDoCampo("Quanta água por dia")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1500, 2000, 2500, 3000).forEach { valor ->
                        EscolhaRapida(
                            texto = String.format(ptBR, "%.1f L", valor / 1000f),
                            selecionada = metaMl == valor,
                            onClick = {
                                metaMl = valor
                                metaDigitada = String.format(ptBR, "%.1f", valor / 1000f)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = metaDigitada,
                    onValueChange = { novo ->
                        metaDigitada = novo
                        // Aceita virgula E ponto: o teclado numerico do Android
                        // oferece um ou outro dependendo do aparelho, e a pessoa
                        // digita o que ve.
                        novo.replace(',', '.').toFloatOrNull()?.let { litros ->
                            if (litros > 0f) metaMl = (litros * 1000).toInt()
                        }
                    },
                    label = { Text("ou digite em litros") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                /**
                 * A SUGESTAO NAO MEXE NA META SOZINHA.
                 *
                 * Digitar o peso nao muda nada: aparece um cartao com a conta
                 * pronta e um botao. So o botao troca a meta. Num app para
                 * idosos, um numero que se altera sozinho enquanto a pessoa
                 * digita e assustador -- ela nao sabe se estragou alguma coisa,
                 * e nao tem como voltar ao valor que tinha antes.
                 *
                 * Por isso tambem o cartao mostra a meta ATUAL ao lado da
                 * sugerida quando as duas sao diferentes: a pessoa ve o que vai
                 * trocar pelo que ANTES de decidir.
                 */
                TituloDoCampo("Calcular pelo meu peso")

                /**
                 * O AVISO VEM ANTES DO NUMERO, E NAO DEPOIS.
                 *
                 * Estava embaixo do resultado, em letra pequena, do jeito que
                 * ninguem le. Um aviso que aparece depois da conta chega tarde:
                 * a pessoa ja viu "3,2 L" e ja formou a ideia.
                 *
                 * E ELE CITA AS SITUACOES POR NOME. "Nao e recomendacao medica"
                 * e verdade e nao serve para nada: nao ajuda ninguem a
                 * reconhecer que a frase e sobre ELE. Quem tem insuficiencia
                 * cardiaca ou doenca renal costuma ter recebido do medico uma
                 * ORDEM de beber menos, e precisa entender que esta conta nao
                 * vale para o seu caso. Num app cujo publico e idoso, essa e
                 * exatamente a parcela que mais tem essas condicoes.
                 */
                Text(
                    text = "Seu peso × 30 ml. É só um ponto de partida, não é " +
                        "conselho médico.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                /**
                 * O AVISO PRECISA PARECER UM AVISO.
                 *
                 * Ele ja vinha antes do numero, mas escrito no mesmo cinza e no
                 * mesmo tamanho do resto: virava paragrafo, e paragrafo em tela
                 * de configuracao ninguem le. Numa caixa vermelha clara, com
                 * borda, ele para de ser texto e passa a ser sinal.
                 *
                 * E ESTA CURTO DE PROPOSITO. A versao anterior tinha cinco
                 * linhas e duas oracoes subordinadas ("ou se algum medico ja
                 * mandou controlar quanto liquido voce bebe"). Aviso comprido
                 * protege o autor e nao protege o leitor -- ainda mais o leitor
                 * de 70 anos que este app foi feito para atender. Tres coisas,
                 * ditas direto: coracao, rins, ordem do medico.
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ErrorContainer)
                        .border(
                            width = 2.dp,
                            color = ErrorRed,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Não use se você tem problema no coração ou nos rins, " +
                            "ou se o médico mandou controlar o quanto você bebe. " +
                            "Siga o que ele disse.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = pesoDigitado,
                    onValueChange = { novo ->
                        // So digito, no maximo 3: um teclado numerico ainda
                        // deixa colar texto, e "70kg" viraria peso invalido
                        // sem a pessoa entender por que a sugestao sumiu.
                        pesoDigitado = novo.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("Seu peso em kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (sugestaoMl != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SecondaryContainer)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = String.format(
                                    ptBR,
                                    "Para %d kg, a sugestão é %.1f L por dia",
                                    pesoKg,
                                    sugestaoMl / 1000f
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            /**
                             * A CONTA ESCRITA POR EXTENSO, LOGO ABAIXO DO
                             * RESULTADO.
                             *
                             * Um numero que aparece sozinho pede fe. "2,1 L"
                             * nao explica nada, e num app para idoso a reacao
                             * comum a um numero inexplicado nao e desconfiar --
                             * e obedecer. Mostrar "70 x 30 ml = 2.100 ml" muda
                             * o tipo de coisa que esta na tela: deixa de ser um
                             * veredito e vira uma conta que a pessoa confere,
                             * refaz de cabeca e discorda se quiser.
                             *
                             * QUANDO O LIMITE APERTA, A LINHA DIZ ISSO. Sem
                             * essa segunda frase, quem pesa 110 kg leria
                             * "110 x 30 ml = 3.300 ml" bem em cima de uma
                             * sugestao de 3,0 L e concluiria que o app erra
                             * conta -- quando na verdade ele esta segurando de
                             * proposito. Ver o teto em MetaPorPeso.
                             */
                            val contaBrutaMl = pesoKg * MetaPorPeso.ML_POR_QUILO
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = String.format(
                                    ptBR,
                                    "A conta: %d × %d ml = %,d ml",
                                    pesoKg,
                                    MetaPorPeso.ML_POR_QUILO,
                                    contaBrutaMl
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            val textoDoLimite = when {
                                contaBrutaMl > MetaPorPeso.META_MAXIMA_ML ->
                                    "O app não sugere mais de 3 L por dia."
                                contaBrutaMl < MetaPorPeso.META_MINIMA_ML ->
                                    "O app não sugere menos de 1,5 L por dia."
                                else -> null
                            }
                            if (textoDoLimite != null) {
                                Text(
                                    text = textoDoLimite,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (sugestaoMl != metaMl) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = String.format(
                                        ptBR,
                                        "Sua meta hoje é %.1f L",
                                        metaMl / 1000f
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        metaMl = sugestaoMl
                                        metaDigitada = String.format(ptBR, "%.1f", sugestaoMl / 1000f)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = "Usar esta meta",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "É a meta que você já está usando.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                }

                Spacer(modifier = Modifier.height(18.dp))

                TituloDoCampo("Tamanho do seu copo")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(150, 200, 250, 300).forEach { valor ->
                        EscolhaRapida(
                            texto = "$valor",
                            selecionada = copoMl == valor,
                            onClick = {
                                copoMl = valor
                                copoDigitado = valor.toString()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = copoDigitado,
                    onValueChange = { novo ->
                        copoDigitado = novo
                        novo.toIntOrNull()?.let { ml -> if (ml > 0) copoMl = ml }
                    },
                    label = { Text("ou digite em ml") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // O resultado da conta, na hora. Sem isso a pessoa so descobre
                // quantos copos combinou depois de salvar e voltar para a tela.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SecondaryContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "São $coposPorDia copos de $copoMl ml por dia",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name.ifBlank { settings.userName },
                        metaMl,
                        settings.monthlyGoalLiters,
                        copoMl,
                        settings.alertsEnabled,
                        settings.chimeType,
                        pesoKg
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = "Salvar", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = "Cancelar", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}
