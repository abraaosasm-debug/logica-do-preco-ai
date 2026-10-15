package br.com.logicadopreco.ai

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
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

/** Cartão discreto para leitura sobre o navegador; não executa operações. */
class OverlayService : Service() {
    companion object {
        const val ACTION_SHOW = "br.com.logicadopreco.ai.OVERLAY_SHOW"
        const val ACTION_HIDE = "br.com.logicadopreco.ai.OVERLAY_HIDE"
    }
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val statePrefs by lazy { getSharedPreferences("overlay_layout", MODE_PRIVATE) }
    private lateinit var wm: WindowManager
    private var frame: LinearLayout? = null
    private var details: LinearLayout? = null
    private var bounds: WindowManager.LayoutParams? = null
    private var expanded = false
    private var miniStatus: TextView? = null
    private var bigStatus: TextView? = null
    private var caption: TextView? = null
    private var subtitle: TextView? = null
    private var setup: TextView? = null
    private var notes: TextView? = null
    private var statusTag: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (frame == null) build()
        return START_NOT_STICKY
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()
    private fun surface(bg: String, stroke: String = "#29525B", round: Int = 17) =
        GradientDrawable().apply {
            setColor(Color.parseColor(bg))
            cornerRadius = dp(round).toFloat()
            setStroke(dp(1), Color.parseColor(stroke))
        }

    private fun label(value: String, size: Float, color: String, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.parseColor(color))
            includeFontPadding = true
            gravity = Gravity.CENTER_VERTICAL
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun build() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = surface("#0B1824", "#2A5965")
            elevation = dp(12).toFloat()
            setPadding(dp(12), dp(7), dp(12), dp(9))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(39)
        }
        val emblem = label("◢", 21f, "#69D4E4", true).apply { gravity = Gravity.CENTER }
        header.addView(emblem, LinearLayout.LayoutParams(dp(25), dp(39)))
        val brand = label("LP  /  VISION", 12f, "#EBF8FC", true)
        header.addView(brand, LinearLayout.LayoutParams(0, dp(39), 1f))
        miniStatus = label("AGUARDAR", 10f, "#F4C276", true).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        header.addView(miniStatus, LinearLayout.LayoutParams(-2, dp(39)))
        root.addView(header, LinearLayout.LayoutParams(-1,dp(39)))

        val expandedContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(1), dp(5), dp(1), 0)
        }
        subtitle = label("ANÁLISE DO GRÁFICO", 10f, "#7AA5B6", true)
        expandedContent.addView(subtitle)
        bigStatus = label("AGUARDAR", 27f, "#F4C276", true).apply { setPadding(0,dp(4),0,dp(4)) }
        expandedContent.addView(bigStatus)
        caption = label("Nenhum gatilho confirmado.", 12f, "#E6F0F7")
        expandedContent.addView(caption)
        val rule = View(this).apply { setBackgroundColor(Color.parseColor("#244456")) }
        expandedContent.addView(rule,LinearLayout.LayoutParams(-1,dp(1)).apply {
            topMargin = dp(12); bottomMargin=dp(11)
        })
        setup = label("ATIVO  /  M1", 12f, "#8CE3DA", true)
        expandedContent.addView(setup)
        notes = label("Aguardando padrões reconhecidos.", 12f, "#A8BECB").apply {
            setPadding(0,dp(5),0,dp(7))
        }
        expandedContent.addView(notes)
        statusTag = label("SEM ENTRADA VALIDADA", 10f, "#F4C276", true)
        expandedContent.addView(statusTag)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0,dp(11),0,0)
        }
        fun action(text: String, color: String, fn: () -> Unit): TextView =
            label(text, 10f, color, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(6),0,dp(6),0)
                setOnClickListener { fn() }
                minHeight = dp(41)
            }
        actions.addView(action("DETALHES", "#69D4E4") {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        },LinearLayout.LayoutParams(0,dp(42),1f))
        actions.addView(action("RECOLHER", "#A9CBD0") {
            changeSize(false)
        },LinearLayout.LayoutParams(0,dp(42),1f))
        actions.addView(action("PARAR", "#F48C98") {
            startService(Intent(this, CaptureService::class.java).apply { action=CaptureService.ACTION_STOP })
            stopSelf()
        },LinearLayout.LayoutParams(0,dp(42),1f))
        expandedContent.addView(actions)
        root.addView(expandedContent)
        frame=root
        details=expandedContent

        val lp=WindowManager.LayoutParams(
            dp(174),WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity=Gravity.TOP or Gravity.START
            x=statePrefs.getInt("x",dp(12))
            y=statePrefs.getInt("y",dp(92))
        }
        bounds=lp
        var downX=0f
        var downY=0f
        var initialX=0
        var initialY=0
        var moved=false
        header.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX=event.rawX;downY=event.rawY
                    initialX=lp.x;initialY=lp.y
                    moved=false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx=event.rawX-downX; val dy=event.rawY-downY
                    if (abs(dx)>dp(6)||abs(dy)>dp(6)) moved=true
                    if (moved) {
                        lp.x=(initialX+dx).roundToInt().coerceIn(0,(resources.displayMetrics.widthPixels-lp.width).coerceAtLeast(0))
                        lp.y=(initialY+dy).roundToInt().coerceIn(0,(resources.displayMetrics.heightPixels-dp(70)).coerceAtLeast(0))
                        wm.updateViewLayout(root,lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) statePrefs.edit().putInt("x",lp.x).putInt("y",lp.y).apply()
                    else changeSize(!expanded)
                    true
                }
                else -> false
            }
        }
        wm.addView(root,lp)
        uiScope.launch { AnalysisStatus.status.collectLatest { render() } }
        uiScope.launch { CaptureStatus.status.collectLatest { render() } }
        render()
    }

    private fun changeSize(value: Boolean) {
        expanded=value
        details?.visibility=if(value) View.VISIBLE else View.GONE
        val lp=bounds?:return
        lp.width=dp(if(value)262 else 174).coerceAtMost((resources.displayMetrics.widthPixels-dp(12)).coerceAtLeast(dp(150)))
        lp.x=lp.x.coerceIn(0,(resources.displayMetrics.widthPixels-lp.width).coerceAtLeast(0))
        frame?.let { wm.updateViewLayout(it,lp) }
    }

    private fun render() {
        val state=IndicatorPresenter.show(
            AnalysisStatus.status.value,CaptureStatus.status.value,AnalysisStatus.ageMs()
        )
        miniStatus?.text=when(state.headline){
            "CAPTURA PAUSADA" -> "PAUSADA"
            else -> state.headline
        }
        miniStatus?.setTextColor(Color.parseColor(state.statusColor))
        bigStatus?.text=state.headline
        bigStatus?.setTextColor(Color.parseColor(state.statusColor))
        caption?.text=state.reason
        setup?.text=state.setup
        notes?.text=state.context
        statusTag?.text=state.badge
        statusTag?.setTextColor(Color.parseColor(state.statusColor))
    }

    override fun onDestroy() {
        uiScope.cancel()
        frame?.let {
            try { wm.removeView(it) } catch (_: Exception) { }
        }
        frame=null
        super.onDestroy()
    }
}
