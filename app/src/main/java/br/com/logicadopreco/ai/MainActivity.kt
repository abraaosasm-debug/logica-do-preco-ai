package br.com.logicadopreco.ai

import android.Manifest
import android.graphics.BitmapFactory
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var overlayGranted by mutableStateOf(false)
    private val screenshotPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        if (CaptureStatus.status.value.phase in listOf("INICIANDO", "CAPTURANDO")) {
            Toast.makeText(this, "Encerre a captura antes de analisar uma imagem.", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        Thread {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                val factor = (maxOf(options.outWidth, options.outHeight) / 2300).coerceAtLeast(1)
                val decode = BitmapFactory.Options().apply { inSampleSize = factor }
                val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decode)
                } ?: throw IllegalStateException("Imagem não decodificada")
                val config = VisionSettings(applicationContext)
                val result = MarketVision().analyzeBitmap(bitmap, config.getCrop())
                val original = PatternEngine().evaluate(result.candles, result.sampledHeight)
                val expanded = ExpandedPatternEngine().evaluate(result.candles, result.sampledHeight)
                AnalysisStatus.update(result, original, config.getAsset(), config.getTimeframe(), expanded)
                VisionAudit.publish(bitmap, config.getCrop(), result)
                bitmap.recycle()
                runOnUiThread {
                    Toast.makeText(this, "Print analisado: " + result.candles.size +
                        " candles visuais detectados.", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                AnalysisStatus.error("Falha ao ler o print: " + error.javaClass.simpleName)
            }
        }.start()
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
                putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(CaptureService.EXTRA_DATA, data)
            }
            try {
                ContextCompat.startForegroundService(this, service)
                if (Settings.canDrawOverlays(this)) {
                    startService(Intent(this, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_SHOW
                    })
                }
            } catch (error: Exception) {
                CaptureStatus.setPhase("ERRO", "Falha ao iniciar serviço: ${error.javaClass.simpleName}")
            }
        } else {
            CaptureStatus.setPhase("PARADO", "O Android não autorizou a captura.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overlayGranted = Settings.canDrawOverlays(this)
        setContent {
            LpTheme {
                val settings = remember { VisionSettings(applicationContext) }
                var crop by remember { mutableStateOf(settings.getCrop()) }
                var asset by remember { mutableStateOf(settings.getAsset()) }
                var timeframe by remember { mutableStateOf(settings.getTimeframe()) }
                val snap by CaptureStatus.status.collectAsState()
                val analysis by AnalysisStatus.status.collectAsState()
                val audit by VisionAudit.state.collectAsState()
                NexoDashboard(
                    snap = snap,
                    analysis = analysis,
                    audit = audit,
                    crop = crop,
                    asset = asset,
                    timeframe = timeframe,
                    onCropChange = { next -> crop = next; settings.setCrop(next) },
                    onAssetChange = { next -> asset = next; settings.setAsset(next) },
                    onTimeframeChange = { next -> timeframe = next; settings.setTimeframe(next) },
                    overlayGranted = overlayGranted,
                    onOverlayPermission = { requestOverlayPermission() },
                    onStart = { requestScreenCapture() },
                    onStop = { stopCapture() },
                    onShowOverlay = { showOverlay() },
                    onOpenChrome = { openChrome() },
                    onAnalyzeScreenshot = { screenshotPicker.launch("image/*") },
                    onReviewAudit = { VisionAudit.markResult(it) },
                    onClearAudit = { VisionAudit.clear() },
                    onExportDiagnostic = { exportDiagnosticCsv() },
                    onNotificationPermission = { requestNotificationPermission() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayGranted = Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 112)
        }
    }

    private fun requestScreenCapture() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Autorize o painel flutuante primeiro.", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopCapture() {
        startService(Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_STOP
        })
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else {
            startService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW
            })
        }
    }

    private fun exportDiagnosticCsv() {
        try {
            if (SessionDiagnostic.count() == 0) {
                Toast.makeText(this, "Sem dados. Inicie e teste uma captura primeiro.", Toast.LENGTH_LONG).show()
                return
            }
            val out = File(cacheDir, "lp_vision_diagnostico_${System.currentTimeMillis()}.csv")
            out.writeText(SessionDiagnostic.exportCsv(), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", out)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Compartilhar diagnóstico de candles"))
        } catch (error: Exception) {
            Toast.makeText(this, "Erro ao exportar: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openChrome() {
        val intent = packageManager.getLaunchIntentForPackage("com.android.chrome")
        if (intent != null) startActivity(intent)
        else Toast.makeText(this, "Chrome não encontrado; abra seu navegador.", Toast.LENGTH_LONG).show()
    }
}

private val Base = Color(0xFF0B1220)
private val SurfaceCard = Color(0xFF17283E)
private val Accent = Color(0xFF38BDF8)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextMuted = Color(0xFF94A3B8)
private val Positive = Color(0xFF22C55E)
private val Warning = Color(0xFFFBBF24)

@Composable
private fun LpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Base,
            background = Base,
            onBackground = TextPrimary,
            surface = SurfaceCard,
            onSurface = TextPrimary,
            secondary = Positive
        ),
        content = content
    )
}

@Composable
private fun MainScreen(
    snap: CaptureSnapshot,
    analysis: AnalysisSnapshot,
    crop: VisionCrop,
    asset: String,
    timeframe: String,
    onCropChange: (VisionCrop) -> Unit,
    onAssetChange: (String) -> Unit,
    onTimeframeChange: (String) -> Unit,
    overlayGranted: Boolean,
    onOverlayPermission: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShowOverlay: () -> Unit,
    onOpenChrome: () -> Unit,
    onAnalyzeScreenshot: () -> Unit,
    onNotificationPermission: () -> Unit
) {
    val active = snap.phase in listOf("INICIANDO", "CAPTURANDO", "SEM QUADROS", "ERRO DE QUADRO")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Base)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("LP  /  MARKET ANALYSIS", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Bold)
        Text("Lógica do Preço", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Text(
            "Captura e painel flutuante · v0.1",
            fontSize = 14.sp, color = TextMuted
        )

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val stateColor = when (snap.phase) {
                    "CAPTURANDO" -> Positive
                    "ERRO", "ERRO DE QUADRO", "SEM QUADROS" -> Warning
                    else -> Accent
                }
                Box(Modifier.size(10.dp).background(stateColor, RoundedCornerShape(50)))
                Text(snap.phase, color = stateColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(snap.message, fontSize = 13.sp, color = TextMuted, lineHeight = 19.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("QUADROS", snap.frameCount.toString(), Modifier.weight(1f))
                StatBox("FPS CAP.", String.format(Locale.US, "%.1f", snap.fps), Modifier.weight(1f))
                StatBox("BRILHO", snap.meanBrightness?.toString() ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (snap.width > 0) "Resolução capturada: ${snap.width} × ${snap.height}"
                else "Sem resolução disponível",
                color = TextMuted, fontSize = 12.sp
            )
        }

        Panel {
            Text("01  Permissões", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (overlayGranted) "● Sobreposição autorizada"
                    else "○ Sobreposição não autorizada",
                    fontSize = 13.sp,
                    color = if (overlayGranted) Positive else Warning,
                    modifier = Modifier.weight(1f)
                )
                if (!overlayGranted) {
                    TextButton(onClick = onOverlayPermission) { Text("AUTORIZAR", fontSize = 11.sp) }
                }
            }
            Text(
                "A captura de tela sempre exige consentimento no diálogo do Android.",
                color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp
            )
            TextButton(onClick = onNotificationPermission) {
                Text("HABILITAR NOTIFICAÇÕES (OPCIONAL)", fontSize = 11.sp)
            }
        }

        Panel {
            Text("02  Sessão", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            if (!active) {
                Button(
                    onClick = onStart,
                    enabled = overlayGranted,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("INICIAR CAPTURA", fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C2532), contentColor = TextPrimary),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("ENCERRAR CAPTURA", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onShowOverlay,
                modifier = Modifier.fillMaxWidth(),
                enabled = overlayGranted
            ) { Text("MOSTRAR PAINEL FLUTUANTE") }
            OutlinedButton(onClick = onOpenChrome, modifier = Modifier.fillMaxWidth()) {
                Text("ABRIR CHROME")
            }
            OutlinedButton(
                onClick = onAnalyzeScreenshot,
                modifier = Modifier.fillMaxWidth(),
                enabled = !active
            ) { Text("ANALISAR PRINT DO GRÁFICO") }
        }

        Panel {
            Text("03  Análise real — experimental", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Candles detectados: " + analysis.candles, color = TextPrimary, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
                Text(
                    if (analysis.candles >= 8) "Nitidez: " + analysis.quality + "%" else "Dados insuficientes",
                    color = Accent, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("Situação: " + analysis.phase, color = if (analysis.direction != "NEUTRO") Positive else Warning,
                fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Padrão: " + analysis.pattern, color = TextPrimary, fontSize = 13.sp)
            if (analysis.direction != "NEUTRO") {
                Text("Possível " + analysis.direction + " — apenas observação",
                    color = if (analysis.direction == "CALL") Positive else Color(0xFFF87171),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(analysis.detail, color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text("Nitidez é uma estimativa do desenho detectado, não taxa de acerto dos sinais.",
                color = TextMuted, fontSize = 11.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(6.dp))
            Text("Quadros processados: " + analysis.analyzedFrames,
                color = TextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Text("CENÁRIO", color = Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(analysis.environment, color = TextPrimary, fontSize = 12.sp)
            Spacer(Modifier.height(7.dp))
            Text("EVIDÊNCIAS RECONHECIDAS (" + analysis.ruleCount + ")",
                color = Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            analysis.evidence.takeLast(8).forEach { item ->
                Text("• " + item, color = TextMuted, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
            if (analysis.evidence.isEmpty()) {
                Text("Aguardando reconhecimento visual.", color = TextMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(analysis.limitations, color = Warning, fontSize = 11.sp, lineHeight = 17.sp)
        }

        Panel {
            Text("04  Calibrar área do gráfico", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Selecione somente a região dos candles, sem botões, preços, menu ou painel LP AI. " +
                "Ajuste os percentuais, volte ao Chrome e confira a quantidade detectada.",
                color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onCropChange(VisionCrop(2, 30, 73, 73)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("USAR PERFIL INICIAL QUOTEX (PRINT ENVIADO)") }
            Text("Perfil sugerido a partir do seu print; ajuste se mudar o zoom ou a tela.",
                color = TextMuted, fontSize = 11.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(7.dp))
            CropSlider("Borda esquerda", crop.left, 0, crop.right - 20) {
                onCropChange(crop.copy(left = it))
            }
            CropSlider("Borda direita", crop.right, crop.left + 20, 100) {
                onCropChange(crop.copy(right = it))
            }
            CropSlider("Borda superior", crop.top, 0, crop.bottom - 20) {
                onCropChange(crop.copy(top = it))
            }
            CropSlider("Borda inferior", crop.bottom, crop.top + 20, 100) {
                onCropChange(crop.copy(bottom = it))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = asset,
                onValueChange = { onAssetChange(it) },
                label = { Text("Nome do ativo (informado manualmente)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Timeframe selecionado:", fontSize = 12.sp, color = TextMuted)
                OutlinedButton(onClick = {
                    onTimeframeChange(if (timeframe == "M1") "M5" else "M1")
                }) { Text(timeframe) }
            }
            Text("O aplicativo ainda não lê automaticamente o ativo nem sincroniza " +
                "o início dos candles com o relógio da corretora.",
                color = Warning, fontSize = 11.sp, lineHeight = 17.sp)
        }

        Panel {
            Text("05  Histórico da sessão", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
            if (analysis.events.isEmpty()) {
                Text("Sem observações registradas.", color = TextMuted, fontSize = 12.sp)
            } else {
                analysis.events.take(10).forEach { event ->
                    Text(event, color = TextMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }

        Panel {
            Text("06  Procedimento de teste", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Step("1", "Autorize a sobreposição.")
            Step("2", "Inicie a captura e, se houver escolha, compartilhe a tela inteira.")
            Step("3", "Volte ao Chrome e abra o gráfico da Quotex.")
            Step("4", "Toque na bolha LP AI para expandir os dados.")
            Step("5", "Confira se os quadros aumentam e encerre a captura.")
        }

        Panel {
            Text("Limite técnico desta versão", fontWeight = FontWeight.Bold, color = Warning)
            Spacer(Modifier.height(6.dp))
            Text(
                "O reconhecimento de candles é experimental. Padrões e possíveis CALL/PUT " +
                    "são apenas observações não validadas. NÃO há integração de cotações, " +
                    "sincronização de segundos nem execução automática. " +
                    "Não use os alertas para arriscar dinheiro real.",
                color = TextMuted, fontSize = 13.sp, lineHeight = 20.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Privacidade: as imagens são processadas em memória. " +
                "O app não salva capturas nem coleta credenciais.",
            color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(SurfaceCard, RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFF26394F), RoundedCornerShape(18.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(Base, RoundedCornerShape(12.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextMuted, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun Step(number: String, instruction: String) {
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(instruction, color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun CropSlider(label: String, value: Int, low: Int, high: Int, onChange: (Int) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextMuted, fontSize = 12.sp)
            Text(value.toString() + "%", color = TextPrimary, fontSize = 12.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = low.toFloat()..high.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
