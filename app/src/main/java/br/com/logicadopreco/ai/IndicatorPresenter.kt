package br.com.logicadopreco.ai

/**
 * Modelo de apresentação, isolado do detector. Não converte leitura visual em
 * autorização de entrada. Um possível viés nunca é apresentado como sinal.
 */
data class IndicatorUiState(
    val title: String,
    val headline: String,
    val reason: String,
    val setup: String,
    val context: String,
    val capture: String,
    val badge: String,
    val statusColor: String
)

object IndicatorPresenter {
    private const val BLUE = "#67D3F5"
    private const val AMBER = "#FBBF24"
    private const val RED = "#F38B92"

    fun show(analysis: AnalysisSnapshot, capture: CaptureSnapshot, ageMs: Long): IndicatorUiState {
        val asset = analysis.asset.trim().ifBlank { "Ativo manual" }
        val setup = if (asset == "Não identificado" || asset == "Ativo não identificado")
            "Ativo não selecionado • ${analysis.timeframe}" else "$asset • ${analysis.timeframe}"
        val disconnected = capture.phase == "PARADO" || capture.phase == "ERRO" ||
            capture.phase == "SEM QUADROS" || capture.phase == "ERRO DE QUADRO" ||
            analysis.phase == "PARADO" || analysis.phase == "INDETERMINADO" ||
            (analysis.lastUpdateMs != 0L && ageMs > 5000L)
        if (disconnected) return IndicatorUiState(
            title = "LP  •  INDICADOR",
            headline = "CAPTURA PAUSADA",
            reason = "Sem dados atuais. Confira a captura da tela inteira.",
            setup = setup,
            context = "Nenhuma leitura operacional disponível",
            capture = "FONTE INDISPONÍVEL",
            badge = "ATENÇÃO",
            statusColor = RED
        )

        val connected = capture.phase == "CAPTURANDO" && analysis.lastUpdateMs != 0L
        if (!connected) return IndicatorUiState(
            title = "LP  •  INDICADOR",
            headline = "AGUARDAR",
            reason = "Inicie a captura e mantenha o gráfico visível.",
            setup = setup, context = "Aguardando informações visuais",
            capture = "PREPARANDO", badge = "ESTUDO",
            statusColor = BLUE
        )

        if (analysis.candles < 8 || analysis.quality < 68) return IndicatorUiState(
            title = "LP  •  INDICADOR",
            headline = "CALIBRAR",
            reason = "Leitura incompleta: ${analysis.candles} candles reconhecidos.",
            setup = setup,
            context = "Ajuste a região do gráfico no aplicativo",
            capture = "LEITURA INSUFICIENTE",
            badge = "ESTUDO", statusColor = AMBER
        )

        val possibleBias = when (analysis.direction) {
            "CALL" -> "Possível viés de alta (sem entrada)"
            "PUT" -> "Possível viés de baixa (sem entrada)"
            else -> "Nenhuma direção confirmada"
        }
        val pattern = analysis.pattern.trim().takeIf { it.isNotBlank() && it != "—" }
            ?: "Nenhum padrão confirmado"
        return IndicatorUiState(
            title = "LP  •  INDICADOR",
            headline = "AGUARDAR",
            reason = "O gatilho e o horário do candle não foram validados.",
            setup = setup,
            context = "$pattern\n$possibleBias",
            capture = "GRÁFICO EM ESTUDO",
            badge = "SEM ENTRADA",
            statusColor = AMBER
        )
    }
}
