package br.com.logicadopreco.ai

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Motor de estudos determinístico, baseado nas seções I a VI do documento fornecido.
 *
 * ATENÇÃO: as imagens não fornecem OHLC/timestamps auditáveis.
 * Estas classificações são HIPÓTESES VISUAIS, nunca sinais confirmados.
 * Todo candle mais à direita é considerado em formação e excluído das decisões.
 */
data class Evidence(
    val type: String,
    val direction: String,
    val description: String,
    val pageHint: String,
    val x: Int,
    val markers: List<Int> = emptyList(),
    val heuristic: Boolean = false
)

data class StudyResult(
    val evidence: List<Evidence>,
    val environment: String,
    val caution: String,
    val candidateDirection: String = "NEUTRO",
    val candidateReason: String = "",
    val priceLevelsAvailable: Boolean = false,
    val candleTimesAvailable: Boolean = false,
    val triggerConfirmed: Boolean = false
)

class ExpandedPatternEngine {
    private fun openingWick(c: VisionCandle) = if (c.up) c.lowerWick else c.upperWick
    private fun closingWick(c: VisionCandle) = if (c.up) c.upperWick else c.lowerWick
    private fun middle(c: VisionCandle) = (c.openY + c.closeY) / 2
    private fun direction(c: VisionCandle) = if (c.up) "ALTA" else "BAIXA"

    /** Comando N1 sem pavios, N2 sem pavio de abertura, expirado N3 sem pavio de fechamento. */
    fun commandLevel(c: VisionCandle, tolerance: Int): Int {
        val begin = openingWick(c) <= tolerance
        val end = closingWick(c) <= tolerance
        return when {
            begin && end -> 1
            begin && !end -> 2
            !begin && end -> 3
            else -> 0
        }
    }

    private fun isStrength(c: VisionCandle, prior: List<VisionCandle>, tolerance: Int): Boolean {
        if (prior.isEmpty()) return false
        val broke = if (c.up) c.highY < prior.takeLast(3).minOf { it.highY } - tolerance
                    else c.lowY > prior.takeLast(3).maxOf { it.lowY } + tolerance
        return broke && c.bodyHeight >= tolerance * 2
    }

    fun evaluate(candles: List<VisionCandle>, chartHeight: Int): StudyResult {
        if (candles.size < 9) return StudyResult(
            emptyList(), "DESCONHECIDO", "Poucos candles. Não existe base para análise."
        )
        // Exclusão explícita do último candle, que ainda pode estar em formação.
        val closed = candles.dropLast(1)
        if (closed.any { it.quality < 0.6 }) return StudyResult(
            emptyList(), "INDETERMINADO", "Reconhecimento visual incerto."
        )

        val tolerance = max(2, chartHeight / 120)
        val evidence = mutableListOf<Evidence>()
        val newest = closed.last()
        val previous = closed[closed.lastIndex - 1]
        val older = closed.takeLast(7)
        val upCount = older.count { it.up }
        val zigzags = older.zipWithNext().count { it.first.up != it.second.up }
        val environment = when {
            zigzags >= 4 -> "TOPOS E FUNDOS / LATERALIZAÇÃO (HIPÓTESE)"
            upCount >= 6 -> "TENDÊNCIA DE ALTA (HIPÓTESE)"
            upCount <= 1 -> "TENDÊNCIA DE BAIXA (HIPÓTESE)"
            else -> "MISTO / INDETERMINADO"
        }

        // Varredura apenas de candles concluidos. Prioriza os 6 mais recentes.
        val start = max(1, closed.size - 6)
        for (index in start until closed.size) {
            val c = closed[index]
            val p = closed[index - 1]
            val earlier = closed.subList(0, index)
            val lvl = commandLevel(c, tolerance)
            val isFirst = c.up != p.up
            val breaks = if (c.up) c.highY < earlier.takeLast(3).minOf { it.highY } - tolerance
                         else c.lowY > earlier.takeLast(3).maxOf { it.lowY } + tolerance
            val commandContext = isFirst || breaks

            if (lvl in 1..2 && commandContext) {
                evidence.add(Evidence(
                    "Comando nível $lvl", direction(c),
                    if (lvl == 1)
                        "Candle sem pavios, com mudança ou rompimento visual do movimento."
                    else "Candle sem pavio de abertura com mudança ou rompimento visual.",
                    "Parte I, páginas 6–20 e 24–27", c.x,
                    if (lvl == 1) listOf(c.openY, middle(c), c.closeY)
                    else listOf(c.openY, middle(c))
                ))
            } else if (lvl == 3 && commandContext) {
                evidence.add(Evidence(
                    "Comando expirado / fim de taxa (N3)", direction(c),
                    "Candle sem pavio de fechamento. A interpretação exige contexto de topo/fundo.",
                    "Parte I, páginas 21–27", c.x,
                    listOf(middle(c), c.closeY), heuristic = true
                ))
            }

            if (isStrength(c, earlier, tolerance)) {
                evidence.add(Evidence(
                    "Vela de força", direction(c),
                    "Superou extrema de candles anteriores; leitura de força dependente do contexto.",
                    "Parte I, páginas 11–16", c.x,
                    listOf(c.openY, middle(c), c.closeY), heuristic = true
                ))
            }

            if (index > 1 && index >= closed.size - 3 && c.up == p.up) {
                val pLvl = commandLevel(p, tolerance)
                val strengthC = isStrength(c, closed.subList(0, index), tolerance)
                val strengthP = isStrength(p, closed.subList(0, index - 1), tolerance)
                val tax = when {
                    pLvl == 1 && lvl == 1 -> "Taxa dividida nível 1"
                    (pLvl == 3 && strengthC) || (lvl == 3 && strengthP) -> "Taxa dividida nível 2"
                    (pLvl == 2 && strengthC) || (lvl == 2 && strengthP) -> "Taxa dividida nível 3"
                    (pLvl == 3 && lvl == 2) || (pLvl == 2 && lvl == 3) ->
                        "Taxa dividida intermediária"
                    else -> null
                }
                if (tax != null) evidence.add(Evidence(
                    tax, direction(c),
                    "Composição candidata de candles da mesma cor. Fracionamento do preço " +
                        "ainda precisa ser validado visualmente.",
                    "Parte I, páginas 33–46", c.x,
                    listOf(p.closeY, c.openY), heuristic = true
                ))
            }

            if (index >= 2) {
                val p2 = closed[index - 2]
                val firstReversal = p.up != p2.up
                val secondContinues = p.up == c.up
                val breaking = if (c.up) c.highY < p.highY - tolerance
                               else c.lowY > p.lowY + tolerance
                if (firstReversal && secondContinues && breaking) evidence.add(Evidence(
                    "Nova posição + " + if (c.up) "nova alta" else "nova baixa",
                    direction(c),
                    "Segundo candle após uma reversão e rompimento visual da extrema " +
                        "do primeiro candle.",
                    "Parte II, páginas 113–120", c.x,
                    listOf(c.openY, if (c.up) c.highY else c.lowY),
                    heuristic = true
                ))
            }
        }

        // Regiões de abertura dos comandos anteriores são referências de defesa.
        val pastCommands = evidence.filter { it.type.startsWith("Comando nível") }
        val lastCommand = pastCommands.lastOrNull()
        if (lastCommand != null) {
            val c = closed.lastOrNull { it.x == lastCommand.x }
            if (c != null && newest.x != c.x) {
                val mark = c.openY
                if (newest.highY <= mark + tolerance &&
                    newest.lowY >= mark - tolerance) {
                    evidence.add(Evidence(
                        "Defesa na abertura do comando", direction(c),
                        "Retorno a uma referência de abertura de comando. " +
                            "Não comprova reação futura.",
                        "Parte III, páginas 138–152", newest.x,
                        listOf(mark), heuristic = true
                    ))
                }
            }
        }

        // Seleção conservadora: oportunidade SÓ EM OBSERVAÇÃO quando há dois motivos distintos.
        val recent = evidence.filter { it.x >= previous.x }
        val sameDirection = recent.groupBy { it.direction }
            .filterKeys { it == "ALTA" || it == "BAIXA" }
            .maxByOrNull { it.value.map { hit -> hit.type.substringBefore(" nível") }.distinct().size }
        val group = sameDirection?.value.orEmpty()
        val independent = group.map { it.type.substringBefore(" nível") }.distinct().size
        val candidate = if (independent >= 2 && group.any { it.type.contains("Defesa") }) {
            if (sameDirection?.key == "ALTA") "CALL" else "PUT"
        } else "NEUTRO"
        val reason = if (candidate != "NEUTRO") {
            "Confluências visuais possíveis: " + group.joinToString(" + ") { it.type } +
                ". Não há confirmação temporal, reação futura nem preços OHLC."
        } else ""

        return StudyResult(
            evidence = evidence,
            environment = environment,
            caution = "Leitura visual estimada, sem preço ou horário sincronizado. " +
                "Nenhum gatilho de entrada confirmado.",
            candidateDirection = candidate,
            candidateReason = reason
        )
    }
}
