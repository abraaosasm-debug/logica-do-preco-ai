package br.com.logicadopreco.ai

import android.Manifest
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
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var overlayGranted by mutableStateOf(false)
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
                val snap by CaptureStatus.status.collectAsState()
                MainScreen(
                    snap = snap,
                    overlayGranted = overlayGranted,
                    onOverlayPermission = { requestOverlayPermission() },
                    onStart = { requestScreenCapture() },
                    onStop = { stopCapture() },
                    onShowOverlay = { showOverlay() },
                    onOpenChrome = { openChrome() },
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
    overlayGranted: Boolean,
    onOverlayPermission: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShowOverlay: () -> Unit,
    onOpenChrome: () -> Unit,
    onNotificationPermission: () -> Unit
) {
    val active = snap.phase in listOf("INICIANDO", "CAPTURANDO", "SEM QUADROS", "ERRO DE QUADRO")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Base)
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
                StatBox("FPS", String.format(Locale.US, "%.1f", snap.fps), Modifier.weight(1f))
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
            Text("01  Permissões", fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
            Text("02  Sessão", fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
        }

        Panel {
            Text("03  Procedimento de teste", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            Step("1", "Autorize a sobreposição.")
            Step("2", "Inicie a captura e confirme o diálogo do Android.")
            Step("3", "Volte ao Chrome e abra o gráfico da Quotex.")
            Step("4", "Toque na bolha LP AI para expandir os dados.")
            Step("5", "Confira se os quadros aumentam e encerre a captura.")
        }

        Panel {
            Text("Limite técnico desta versão", fontWeight = FontWeight.Bold, color = Warning)
            Spacer(Modifier.height(6.dp))
            Text(
                "O diagnóstico usa quadros capturados de verdade. Esta versão NÃO " +
                    "reconhece candles, padrões, CALL ou PUT e NÃO envia ordens. " +
                    "Não use o protótipo como fonte de sinais financeiros.",
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
