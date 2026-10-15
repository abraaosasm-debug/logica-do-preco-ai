package br.com.logicadopreco.ai

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager

/**
 * Serviço de captura consentida. Processa pixels em memória para diagnósticos;
 * não transmite, persiste ou reconhece candles no protótipo v0.1.
 */
class CaptureService : Service() {
    companion object {
        const val ACTION_START = "br.com.logicadopreco.ai.START"
        const val ACTION_STOP = "br.com.logicadopreco.ai.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "resultData"
        private const val CHANNEL_ID = "lp_capture"
        private const val NOTIFICATION_ID = 771
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private lateinit var workerThread: HandlerThread
    private lateinit var workerHandler: Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stopped = false

    private val watchdog = object : Runnable {
        override fun run() {
            if (projection == null || stopped) return
            val snapshot = CaptureStatus.status.value
            if (snapshot.lastFrameAtMs != 0L &&
                (snapshot.lastFrameAgeMs() ?: 0L) > 5000L
            ) {
                CaptureStatus.setPhase(
                    "SEM QUADROS",
                    "Mais de 5 segundos sem imagem nova. Verifique o compartilhamento."
                )
            }
            mainHandler.postDelayed(this, 2000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        workerThread = HandlerThread("lp-capture-frames").also { it.start() }
        workerHandler = Handler(workerThread.looper)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (projection != null) return START_NOT_STICKY
                // Primeiro promove o serviço, depois utiliza o token MediaProjection.
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                    val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                    @Suppress("DEPRECATION")
                    val data = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                    } else intent.getParcelableExtra(EXTRA_DATA)

                    if (code != Activity.RESULT_OK || data == null) {
                        CaptureStatus.setPhase("ERRO", "Autorização de captura ausente.")
                        stopSelf()
                    } else {
                        startProjection(code, data)
                    }
                } catch (error: Exception) {
                    CaptureStatus.setPhase(
                        "ERRO",
                        "Não foi possível iniciar: ${error.javaClass.simpleName}."
                    )
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                shutdown()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val newProjection = manager.getMediaProjection(resultCode, resultData)
        projection = newProjection
        stopped = false

        newProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                mainHandler.post {
                    CaptureStatus.stop("Captura encerrada pelo Android.")
                    shutdown()
                    stopSelf()
                }
            }
        }, mainHandler)

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val bounds = if (Build.VERSION.SDK_INT >= 30) {
            windowManager.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            android.graphics.Point().also { display.getRealSize(it) }
                .let { android.graphics.Rect(0, 0, it.x, it.y) }
        }
        val width = bounds.width().coerceAtLeast(1)
        val height = bounds.height().coerceAtLeast(1)
        val densityDpi = resources.displayMetrics.densityDpi
        CaptureStatus.start(width, height)

        val imageReader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 2
        )
        reader = imageReader
        imageReader.setOnImageAvailableListener({ source ->
            var image: Image? = null
            try {
                image = source.acquireLatestImage()
                if (image != null) {
                    val brightness = sampleBrightness(image)
                    CaptureStatus.onFrame(image.width, image.height, brightness)
                }
            } catch (error: Exception) {
                CaptureStatus.setPhase(
                    "ERRO DE QUADRO",
                    "Falha no processamento: ${error.javaClass.simpleName}."
                )
            } finally {
                image?.close()
            }
        }, workerHandler)

        virtualDisplay = newProjection.createVirtualDisplay(
            "LP AI Preview",
            width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, workerHandler
        )
        mainHandler.postDelayed(watchdog, 2000L)
    }

    /**
     * Amostra somente uma pequena grade RGBA. Não guarda o frame,
     * nem usa captura para prever operações.
     */
    private fun sampleBrightness(image: Image): Int {
        val plane = image.planes.firstOrNull() ?: return 0
        val bytes = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var sum = 0L
        var samples = 0
        for (yi in 1..8) {
            val y = yi * image.height / 9
            for (xi in 1..8) {
                val x = xi * image.width / 9
                val offset = y * rowStride + x * pixelStride
                if (offset + 2 < bytes.limit()) {
                    val r = bytes.get(offset).toInt() and 255
                    val g = bytes.get(offset + 1).toInt() and 255
                    val b = bytes.get(offset + 2).toInt() and 255
                    sum += (r * 21 + g * 72 + b * 7) / 100
                    samples++
                }
            }
        }
        return if (samples == 0) 0 else (sum / samples).toInt()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Captura de tela", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Monitoramento de quadros enquanto autorizado." }
        )
    }

    private fun createNotification(): Notification {
        val stop = Intent(this, CaptureService::class.java).apply { action = ACTION_STOP }
        val stopAction = PendingIntent.getService(
            this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val launch = PendingIntent.getActivity(
            this, 3, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Lógica do Preço AI — Captura ativa")
            .setContentText("Diagnóstico de quadros. Nenhum sinal é emitido.")
            .setContentIntent(launch)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Parar captura", stopAction).build())
            .build()
    }

    private fun shutdown() {
        if (stopped) return
        stopped = true
        mainHandler.removeCallbacks(watchdog)
        reader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        reader?.close()
        reader = null
        val oldProjection = projection
        projection = null
        try { oldProjection?.stop() } catch (_: Exception) { }
        stopService(Intent(this, OverlayService::class.java))
        if (CaptureStatus.status.value.phase != "ERRO") {
            CaptureStatus.stop()
        }
    }

    override fun onDestroy() {
        shutdown()
        workerThread.quitSafely()
        super.onDestroy()
    }
}
