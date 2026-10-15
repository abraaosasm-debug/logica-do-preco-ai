package br.com.logicadopreco.ai

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt

/** Janela arrastável sobre o Chrome. Sem acesso aos controles da corretora. */
class OverlayService : Service() {
    companion object {
        const val ACTION_SHOW = "br.com.logicadopreco.ai.OVERLAY_SHOW"
        const val ACTION_HIDE = "br.com.logicadopreco.ai.OVERLAY_HIDE"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var container: LinearLayout? = null
    private var details: LinearLayout? = null
    private var statusText: TextView? = null
    private var frameText: TextView? = null
    private var analysisText: TextView? = null
    private val overlayPrefs by lazy { getSharedPreferences("overlay_layout", MODE_PRIVATE) }
    private var expanded = false
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (container == null) createOverlay()
        return START_NOT_STICKY
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun rounded(color: String, radius: Float = 18f) = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radius.roundToInt()).toFloat()
        setStroke(dp(1), Color.parseColor("#334155"))
    }

    private fun text(value: String, size: Float, color: String, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.parseColor(color))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun createOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded("#0B1220")
            elevation = dp(9).toFloat()
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = text("◉  LP AI", 15f, "#38BDF8", true)
        header.addView(title)
        val chevron = text("   ▸", 16f, "#F1F5F9", true)
        header.addView(chevron)
        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(10), 0, 0)
        }
        statusText = text("Aguardando captura...", 13f, "#E2E8F0")
        frameText = text("Sem quadros recebidos", 12f, "#94A3B8")
        val interpretation = text("Padrões: aguardando leitura...", 12f, "#38BDF8")
        analysisText = interpretation
        val disclaimer = text("ESTUDO • Sem sinais validados", 11f, "#FBBF24")

        body.addView(statusText)
        body.addView(frameText)
        body.addView(interpretation)
        body.addView(disclaimer)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        val hideButton = text("MINIMIZAR", 11f, "#38BDF8", true).apply {
            setPadding(dp(4), dp(6), dp(15), dp(6))
            setOnClickListener {
                expanded = false
                body.visibility = View.GONE
                chevron.text = "   ▸"
                updateSize()
            }
        }
        val stopButton = text("ENCERRAR", 11f, "#F87171", true).apply {
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setOnClickListener {
                startService(Intent(this@OverlayService, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_STOP
                })
                stopSelf()
            }
        }
        actionRow.addView(hideButton)
        actionRow.addView(stopButton)
        body.addView(actionRow)
        root.addView(body)

        details = body
        container = root
        val layout = WindowManager.LayoutParams(
            dp(120),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = overlayPrefs.getInt("x", dp(12))
            y = overlayPrefs.getInt("y", dp(105))
        }
        params = layout

        var beginX = 0f
        var beginY = 0f
        var initialX = 0
        var initialY = 0
        var dragging = false

        header.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    beginX = event.rawX
                    beginY = event.rawY
                    initialX = layout.x
                    initialY = layout.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - beginX
                    val dy = event.rawY - beginY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) dragging = true
                    if (dragging) {
                        val screen = resources.displayMetrics
                        layout.x = (initialX + dx).roundToInt()
                            .coerceIn(0, (screen.widthPixels - dp(80)).coerceAtLeast(0))
                        layout.y = (initialY + dy).roundToInt()
                            .coerceIn(0, (screen.heightPixels - dp(80)).coerceAtLeast(0))
                        windowManager.updateViewLayout(root, layout)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        overlayPrefs.edit().putInt("x", layout.x).putInt("y", layout.y).apply()
                    }
                    if (!dragging) {
                        expanded = !expanded
                        body.visibility = if (expanded) View.VISIBLE else View.GONE
                        chevron.text = if (expanded) "   ▾" else "   ▸"
                        updateSize()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(root, layout)
        scope.launch {
            AnalysisStatus.status.collectLatest { snapshot ->
                val stale = snapshot.lastUpdateMs != 0L &&
                    AnalysisStatus.ageMs() > 5000L
                analysisText?.text = if (stale) {
                    "CAPTURA DESATUALIZADA\nObservações suspensas"
                } else {
                    "Candles: " + snapshot.candles +
                        if (snapshot.candles >= 8) " | Nitidez: " + snapshot.quality + "%" else " | Calibrar" +
                        "\nEstado: " + snapshot.phase +
                        if (snapshot.direction != "NEUTRO")
                            "\nPossível " + snapshot.direction + " (não validado)\n" +
                                snapshot.pattern
                        else "\n" + snapshot.pattern +
                            if (snapshot.candles < 8) "\n" + snapshot.detail else ""
                }
            }
        }
        scope.launch {
            CaptureStatus.status.collectLatest { snapshot ->
                statusText?.text = "Captura real • sem operações automáticas"
                frameText?.text = "Captura: ${snapshot.phase}" +
                    " • FPS: ${"%.0f".format(snapshot.fps)}"
            }
        }
    }

    private fun updateSize() {
        val current = container ?: return
        val layout = params ?: return
        layout.width = if (expanded) dp(244) else dp(120)
        val available = resources.displayMetrics.widthPixels - dp(8)
        layout.width = layout.width.coerceAtMost(available.coerceAtLeast(dp(120)))
        layout.x = layout.x.coerceIn(0, (resources.displayMetrics.widthPixels - layout.width).coerceAtLeast(0))
        windowManager.updateViewLayout(current, layout)
    }

    override fun onDestroy() {
        scope.cancel()
        container?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        container = null
        super.onDestroy()
    }
}
