package br.com.logicadopreco.ai

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Ink = Color(0xFF080E18)
private val Deep = Color(0xFF0F1928)
private val Raised = Color(0xFF142235)
private val Edge = Color(0xFF283B51)
private val Snow = Color(0xFFF2F6FA)
private val Muted = Color(0xFF9AACC1)
private val Cyan = Color(0xFF65D7E7)
private val Mint = Color(0xFF9AE5BE)
private val Amber = Color(0xFFF4C276)
private val Scarlet = Color(0xFFF48C98)

@Composable
private fun BrandMark() {
    Box(
        modifier = Modifier.size(48.dp)
            .background(Brush.linearGradient(listOf(Color(0xFF163444), Color(0xFF0B2436))), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF326A7A), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(30.dp)) {
            val s = size.width
            drawLine(Cyan, Offset(s*.18f,s*.66f), Offset(s*.18f,s*.25f), 2.5.dp.toPx(), cap=StrokeCap.Round)
            drawLine(Cyan, Offset(s*.11f,s*.45f), Offset(s*.25f,s*.45f), 6.dp.toPx(), cap=StrokeCap.Round)
            drawLine(Mint, Offset(s*.46f,s*.81f), Offset(s*.46f,s*.17f), 2.5.dp.toPx(), cap=StrokeCap.Round)
            drawLine(Mint, Offset(s*.39f,s*.42f), Offset(s*.53f,s*.42f), 8.dp.toPx(), cap=StrokeCap.Round)
            drawLine(Cyan, Offset(s*.69f,s*.53f), Offset(s*.88f,s*.13f), 3.dp.toPx(), cap=StrokeCap.Round)
            drawLine(Cyan, Offset(s*.75f,s*.13f), Offset(s*.88f,s*.13f), 3.dp.toPx(), cap=StrokeCap.Round)
        }
    }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Deep, RoundedCornerShape(22.dp))
            .border(1.dp, Edge.copy(alpha=.78f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        content = content
    )
}

@Composable
private fun Tag(label: String, color: Color) {
    Box(
        modifier = Modifier.background(color.copy(alpha=.10f), RoundedCornerShape(9.dp))
            .border(1.dp, color.copy(alpha=.25f), RoundedCornerShape(9.dp))
            .padding(horizontal=9.dp,vertical=5.dp)
    ) {
        Text(label, color=color, fontWeight=FontWeight.SemiBold, fontSize=10.sp)
    }
}

@Composable
private fun Action(
    label: String,
    main: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if(main) Cyan else Raised,
            contentColor = if(main) Ink else Snow,
            disabledContainerColor=Raised.copy(alpha=.45f),
            disabledContentColor=Muted
        ),
        shape = RoundedCornerShape(14.dp),
        border = if(main) null else BorderStroke(1.dp, Edge)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
private fun Metric(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Raised, RoundedCornerShape(12.dp)).padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontSize=9.sp, color=Muted)
        Text(value, fontSize=17.sp, color=Snow, fontWeight=FontWeight.Bold,maxLines=1)
    }
}

@Composable
fun NexoDashboard(
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
    val active = snap.phase in setOf("CAPTURANDO", "INICIANDO", "SEM QUADROS", "ERRO DE QUADRO")
    val ui = IndicatorPresenter.show(analysis, snap, AnalysisStatus.ageMs())
    val hue = when(ui.headline) {
        "CAPTURA PAUSADA" -> Scarlet
        "CALIBRAR" -> Amber
        else -> Cyan
    }
    var calibration by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }

    Column(
        modifier=Modifier.fillMaxSize().background(Ink)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal=18.dp, vertical=24.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            BrandMark()
            Column(Modifier.weight(1f)) {
                Text("LÓGICA DO PREÇO", color=Snow, fontWeight=FontWeight.ExtraBold, fontSize=18.sp)
                Text("VISION ENGINE  /  v0.6", color=Cyan, fontSize=10.sp, fontWeight=FontWeight.Bold)
            }
            Tag("BETA", Cyan)
        }

        Column(
            modifier=Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF162D39),Deep)), RoundedCornerShape(24.dp))
                .border(1.dp,Color(0xFF315564),RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically) {
                Text("LEITURA DO MERCADO", color=Muted, fontSize=10.sp, fontWeight=FontWeight.Bold)
                Tag(ui.capture, hue)
            }
            Text(ui.headline, fontSize=28.sp,color=hue, fontWeight=FontWeight.ExtraBold)
            Text(ui.reason, color=Snow, fontSize=13.sp, lineHeight=19.sp)
            HorizontalDivider(color=Edge)
            Text(ui.setup.uppercase(), color=Cyan, fontSize=12.sp,fontWeight=FontWeight.Bold)
            Text(ui.context, color=Muted, fontSize=12.sp, lineHeight=18.sp)
            if(!overlayGranted) {
                Text("Autorize a sobreposição para iniciar.",color=Amber,fontSize=12.sp)
                Action("AUTORIZAR PAINEL", main=true,modifier=Modifier.fillMaxWidth(),onClick=onOverlayPermission)
            } else if(active) {
                Action("ENCERRAR MONITORAMENTO",modifier=Modifier.fillMaxWidth(),onClick=onStop)
            } else {
                Action("INICIAR MONITORAMENTO",main=true,modifier=Modifier.fillMaxWidth(),onClick=onStart)
            }
            if (overlayGranted) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Action("ABRIR CHROME",modifier=Modifier.weight(1f),onClick=onOpenChrome)
                    Action("MOSTRAR PAINEL",modifier=Modifier.weight(1f),onClick=onShowOverlay)
                }
            }
        }

        Section {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically) {
                Text("MONITORAMENTO",color=Snow,fontSize=14.sp,fontWeight=FontWeight.Bold)
                Tag(if(active) "ATIVO" else "DESLIGADO", if(active)Mint else Muted)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Metric("CANDLES",analysis.candles.toString(),Modifier.weight(1f))
                Metric("ANÁLISES",analysis.analyzedFrames.toString(),Modifier.weight(1f))
                Metric("QUADROS",snap.frameCount.toString(),Modifier.weight(1f))
            }
            Text("Contagem de candles não é precisão de sinais.",color=Muted,fontSize=11.sp)
        }

        Section {
            Text("FERRAMENTAS",color=Snow,fontSize=14.sp,fontWeight=FontWeight.Bold)
            Text("Análise de imagem e ajuste de captura",color=Muted,fontSize=12.sp)
            Action("ANALISAR PRINT SALVO",modifier=Modifier.fillMaxWidth(),
                enabled=!active,onClick=onAnalyzeScreenshot)
            Action(if(calibration)"FECHAR CALIBRAÇÃO" else "CALIBRAR GRÁFICO",
                modifier=Modifier.fillMaxWidth(),onClick={calibration=!calibration})
            if (calibration) {
                Text("Recorte somente os candles; não inclua controles da corretora.",
                    fontSize=11.sp,color=Muted)
                Action("PERFIL QUOTEX DO SEU PRINT",modifier=Modifier.fillMaxWidth(),
                    onClick={onCropChange(VisionCrop(2,30,73,73))})
                CropTune("ESQUERDA",crop.left,0,crop.right-20) {onCropChange(crop.copy(left=it))}
                CropTune("DIREITA",crop.right,crop.left+20,100) {onCropChange(crop.copy(right=it))}
                CropTune("TOPO",crop.top,0,crop.bottom-20) {onCropChange(crop.copy(top=it))}
                CropTune("BASE",crop.bottom,crop.top+20,100) {onCropChange(crop.copy(bottom=it))}
                OutlinedTextField(value=asset,onValueChange=onAssetChange,
                    label={Text("Ativo (manual)")}, singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("PERÍODO",color=Muted,fontSize=11.sp)
                    Tag(timeframe,Cyan)
                    Action("M1 / M5",modifier=Modifier.weight(1f),
                        onClick={onTimeframeChange(if(timeframe=="M1")"M5" else "M1")})
                }
            }
        }

        Section {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically) {
                Text("LEITURA TÉCNICA",color=Snow,fontWeight=FontWeight.Bold,fontSize=14.sp)
                TextButton(onClick={details=!details}) {
                    Text(if(details) "RECOLHER" else "DETALHES",color=Cyan,fontSize=11.sp)
                }
            }
            Text(analysis.pattern.takeIf{it!="—"}?:"Nenhum padrão confirmado",
                color=Snow,fontSize=14.sp,fontWeight=FontWeight.Medium)
            Text(analysis.detail,color=Muted,fontSize=12.sp,lineHeight=18.sp)
            if (details) {
                Text("CENÁRIO: " + analysis.environment,fontSize=11.sp,color=Cyan)
                analysis.evidence.takeLast(8).forEach { Text("• " + it,color=Muted,fontSize=11.sp) }
                Text("CAPTURA: " + snap.phase + " | " + snap.fps.roundToInt() +
                    " FPS | brilho " + (snap.meanBrightness?:0),color=Muted,fontSize=11.sp)
                analysis.events.take(5).forEach{Text(it,fontSize=11.sp,color=Muted)}
            }
        }

        Text("MODO ESTUDO. Sem sincronização temporal e precisão validada, " +
            "nenhum alerta representa autorização para operar dinheiro real.",
            modifier=Modifier.fillMaxWidth().padding(bottom=16.dp),
            color=Muted,fontSize=11.sp,lineHeight=17.sp,textAlign=TextAlign.Center)
    }
}

@Composable
private fun CropTune(label:String,value:Int,from:Int,to:Int,update:(Int)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(label,color=Muted,fontSize=10.sp)
            Text(value.toString()+"%",color=Snow,fontSize=11.sp)
        }
        Slider(value=value.toFloat(),onValueChange={ update(it.roundToInt()) },
            valueRange=from.toFloat()..to.toFloat(),
            colors=SliderDefaults.colors(thumbColor=Cyan,activeTrackColor=Cyan,inactiveTrackColor=Edge),
            modifier=Modifier.fillMaxWidth())
    }
}
