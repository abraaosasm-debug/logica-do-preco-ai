package br.com.logicadopreco.ai

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Indicador flutuante de leitura gráfica.
 * Não emite ordens e nunca transforma padrões experimentais em "OPERAR".
 */
class OverlayService : Service() {
    companion object {
        const val ACTION_SHOW = "br.com.logicadopreco.ai.OVERLAY_SHOW"
        const val ACTION_HIDE = "br.com.logicadopreco.ai.OVERLAY_HIDE"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var manager: WindowManager
    private val prefs by lazy { getSharedPreferences("overlay_layout", MODE_PRIVATE) }
    private var root: LinearLayout? = null
    private var panel: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false

    private var miniStatus: TextView? = null
    private var label: TextView? = null
    private var headline: TextView? = null
    private var reason: TextView? = null
    private var setup: TextView? = null
    private var context: TextView? = null
    private var badge: TextView? = null
    private var dataState: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) buildOverlay()
        return START_NOT_STICKY
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun shape(bg: String, border: String = "#324257", radius: Int = 18): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(bg))
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.parseColor(border))
        }

    private fun text(value: String, size: Float, color: String, strong: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(Color.parseColor(color))
            gravity = Gravity.CENTER_VERTICAL
            if (strong) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = true
        }

    private fun buildOverlay() {
        manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape("#101825", "#41506A", 18)
            elevation = dp(8).toFloat()
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val handle = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            minimumHeight = dp(38)
        }
        val brand = text("▰  LP", 14f, "#67D3F5", true)
        handle.addView(brand)
        miniStatus = text("   AGUARDAR", 12f, "#FBBF24", true).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        handle.addView(miniStatus, LinearLayout.LayoutParams(0, dp(38), 1f))
        outer.addView(handle, LinearLayout.LayoutParams(-1, dp(38)))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(1), dp(8), dp(1), dp(2))
        }

        label = text("LEITURA DO GRÁFICO", 10f, "#AAB9CE", true)
        body.addView(label)
        headline = text("AGUARDAR", 25f, "#FBBF24", true).apply {
            setPadding(0, dp(6), 0, dp(3))
        }
        body.addView(headline)
        reason = text("Aguardando dados do gráfico.", 12f, "#F3F6FB")
        body.addView(reason)

        val separator = View(this).apply { setBackgroundColor(Color.parseColor("#334255")) }
        body.addView(separator, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        })

        setup = text("Ativo não selecionado • M1", 12f, "#D7E2EF", true)
        body.addView(setup)
        context = text("Nenhuma leitura confirmada", 12f, "#BCCADA").apply {
            setPadding(0, dp(5), 0, dp(8))
        }
        body.addView(context)
        dataState = text("FONTE: AGUARDANDO", 10f, "#88A4C2", true)
        body.addView(dataState)

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        badge = text("ESTUDO", 10f, "#FBBF24", true)
        footer.addView(badge, LinearLayout.LayoutParams(0, dp(34), 1f))

        val collapse = text("RECOLHER", 10f, "#67D3F5", true).apply {
            setPadding(dp(4), 0, dp(10), 0)
            gravity = Gravity.CENTER
            setOnClickListener { setExpanded(false) }
        }
        val stop = text("PARAR", 10f, "#F38B92", true).apply {
            setPadding(dp(5), 0, 0, 0)
            gravity = Gravity.CENTER
            setOnClickListener {
                startService(Intent(this@OverlayService, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_STOP
                })
                stopSelf()
            }
        }
        footer.addView(collapse, LinearLayout.LayoutParams(-2, dp(38)))
        footer.addView(stop, LinearLayout.LayoutParams(-2, dp(38)))
        body.addView(footer)

        outer.addView(body, LinearLayout.LayoutParams(-1, -2))
        root = outer
        panel = body

        val lp = WindowManager.LayoutParams(
            dp(174), -2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", dp(12))
            y = prefs.getInt("y", dp(90))
        }
        params = lp

        var downX = 0f
        var downY = 0f
        var initialX = 0
        var initialY = 0
        var dragged = false
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    initialX = lp.x
                    initialY = lp.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) dragged = true
                    if (dragged) {
                        val bounds = resources.displayMetrics
                        lp.x = (initialX + dx).roundToInt()
                            .coerceIn(0, (bounds.widthPixels - lp.width).coerceAtLeast(0))
                        lp.y = (initialY + dy).roundToInt()
                            .coerceIn(0, (bounds.heightPixels - dp(70)).coerceAtLeast(0))
                        manager.updateViewLayout(outer, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
                    else setExpanded(!expanded)
                    true
                }
                else -> false
            }
        }

        manager.addView(outer, lp)
        scope.launch {
            AnalysisStatus.status.collectLatest { refresh() }
        }
        scope.launch {
            CaptureStatus.status.collectLatest { refresh() }
        }
        refresh()
    }

    private fun setExpanded(value: Boolean) {
        expanded = value
        panel?.visibility = if (expanded) View.VISIBLE else View.GONE
        val lp = params ?: return
        lp.width = if (expanded) dp(264) else dp(174)
        lp.width = lp.width.coerceAtMost((resources.displayMetrics.widthPixels - dp(12)).coerceAtLeast(dp(140)))
        lp.x = lp.x.coerceIn(0, (resources.displayMetrics.widthPixels - lp.width).coerceAtLeast(0))
        root?.let { manager.updateViewLayout(it, lp) }
    }

    private fun refresh() {
        if (root == null) return
        val analysis = AnalysisStatus.status.value
        val capture = CaptureStatus.status.value
        val state = IndicatorPresenter.show(analysis, capture, AnalysisStatus.ageMs())
        miniStatus?.text = if (state.headline == "CAPTURA PAUSADA") "PAUSADA" else state.headline
        miniStatus?.setTextColor(Color.parseColor(state.statusColor))
        label?.text = state.title
        headline?.text = state.headline
        headline?.setTextColor(Color.parseColor(state.statusColor))
        reason?.text = state.reason
        setup?.text = state.setup
        context?.text = state.context
        dataState?.text = state.capture
        badge?.text = state.badge
        badge?.setTextColor(Color.parseColor(state.statusColor))
    }

    override fun onDestroy() {
        scope.cancel()
        root?.let {
            try { manager.removeView(it) } catch (_: Exception) { }
        }
        root = null
        super.onDestroy()
    }
}
