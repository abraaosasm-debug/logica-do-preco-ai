package br.com.logicadopreco.ai

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PatternReading(
    val label: String = "SEM PADRÃO",
    val direction: String = "NEUTRO",
    val status: String = "AGUARDANDO",
    val rationale: String = "Nenhuma configuração confirmada.",
    val markerY: Int? = null,
    val sourceX: Int? = null
)

/**
 * Recorte didático da teoria do documento "Estudos e anotações sobre a lógica do preço".
 * Não representa probabilidade de acerto. O último candle visível é tratado como ABERTO.
 */
class PatternEngine {
    fun evaluate(candles: List<VisionCandle>, chartHeight: Int): PatternReading {
        if (candles.size < 8) return PatternReading(
            status = "CALIBRAR", rationale = "Precisamos de pelo menos 8 candles reconhecidos."
        )
        val quality = candles.map { it.quality }.average()
        if (quality < 0.68) return PatternReading(
            status = "IMAGEM INCERTA", rationale = "Qualidade visual insuficiente para alertas."
        )
        val closed = candles.dropLast(1)
        val active = candles.last()
        val previous = closed.lastOrNull() ?: return PatternReading()
        val before = closed.dropLast(1).lastOrNull() ?: return PatternReading()
        val tolerance = max(2, chartHeight / 110)

        fun comando(c: VisionCandle, older: List<VisionCandle>): Boolean {
            val wick = if (c.up) c.lowerWick else c.upperWick
            val noOpeningWick = wick <= tolerance
            val changed = older.lastOrNull()?.up != c.up
            val brokePrevious = if (c.up) older.takeLast(3).any { c.highY < it.highY }
                else older.takeLast(3).any { c.lowY > it.lowY }
            return noOpeningWick && (changed || brokePrevious)
        }

        val lastIsCommand = comando(previous, closed.dropLast(1))
        val commandLevel = if (previous.up) previous.openY else previous.openY
        val midpoint = (previous.openY + previous.closeY) / 2

        // Um alerta em observação é permitido apenas quando reconhecido um comando
        // FECHADO e a vela atual volta à região da metade do corpo.
        if (lastIsCommand && previous.bodyHeight >= tolerance * 2 && active.x > previous.x) {
            val touch = active.highY <= midpoint + tolerance &&
                active.lowY >= midpoint - tolerance
            val opensNear = abs(active.openY - previous.closeY) <=
                max(tolerance * 3, previous.bodyHeight / 3)
            if (touch && opensNear) {
                return PatternReading(
                    label = if (previous.upperWick <= tolerance &&
                        previous.lowerWick <= tolerance) "Comando nível 1" else "Comando nível 2",
                    direction = if (previous.up) "CALL" else "PUT",
                    status = "OBSERVAÇÃO",
                    rationale = "Candle de comando fechado + retorno à região de 50%. " +
                        "A abertura do candle atual não foi sincronizada com a Quotex; " +
                        "o gatilho temporal de 10–15 s NÃO está confirmado.",
                    markerY = midpoint,
                    sourceX = previous.x
                )
            }
            return PatternReading(
                label = "Comando " + if (previous.up) "de alta" else "de baixa",
                direction = "NEUTRO",
                status = "MARCAÇÃO",
                rationale = "Comando fechado reconhecido. Aguardando aproximação de 50% " +
                    "ou da abertura. Sem gatilho de entrada.",
                markerY = midpoint, sourceX = previous.x
            )
        }

        // Divergência e rompimento do candle anterior: informação estrutural,
        // NÃO interpretação como entrada validada.
        if (previous.highY < before.highY - tolerance && previous.up) {
            return PatternReading(
                label = "Nova alta visual", status = "ESTRUTURA",
                rationale = "Máxima de candle fechado acima da máxima anterior. " +
                    "Não equivale por si só a CALL."
            )
        }
        if (previous.lowY > before.lowY + tolerance && !previous.up) {
            return PatternReading(
                label = "Nova baixa visual", status = "ESTRUTURA",
                rationale = "Mínima de candle fechado abaixo da mínima anterior. " +
                    "Não equivale por si só a PUT."
            )
        }

        return PatternReading(
            label = "Leitura estrutural", status = "AGUARDANDO",
            rationale = "Ainda não há confluência reconhecida pelas regras implementadas."
        )
    }
}
