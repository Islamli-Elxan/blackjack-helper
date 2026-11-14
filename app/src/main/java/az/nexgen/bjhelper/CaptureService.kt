package az.nexgen.bjhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import java.util.Locale

/**
 * Foreground service: captures the screen, reads the cards twice a second, keeps the round and the
 * Hi-Lo count, and pushes the advice to the floating window.
 */
class CaptureService : Service() {

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "capture"
        private const val NOTIF_ID = 17
        private const val TAG = "BJHelper"
        private const val TICK_MS = 450L

        private val NEUTRAL = 0xFFE6F0E9.toInt()
        private val C_HIT = 0xFFFFB978.toInt()
        private val C_STAND = 0xFF7FE3D2.toInt()
        private val C_DOUBLE = 0xFFAAC2FF.toInt()
        private val C_SPLIT = 0xFFD8B6FF.toInt()
        private val C_BUST = 0xFFFF9F96.toInt()
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val ui = Handler(Looper.getMainLooper())
    private var overlay: Overlay? = null
    private val tracker = RoundTracker()
    private var cardReader: CardReader? = null
    private var lastBitmap: Bitmap? = null
    private var lastReading: CardReader.Reading? = null
    private var started = false
    private var decks = 6
    private var countMode = true

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    private fun readData(i: Intent?): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            i?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            i?.getParcelableExtra<Intent>(EXTRA_DATA)
        }
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            Pair(b.width(), b.height())
        } else {
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Blackjack Köməkçisi", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Blackjack Köməkçisi işləyir")
            .setContentText("Ekranı oxuyur. Açmaq üçün toxun.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) return START_NOT_STICKY
        val data = readData(intent)
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        goForeground()

        val prefs = getSharedPreferences("bj", Context.MODE_PRIVATE)
        decks = prefs.getInt("decks", 6)
        countMode = prefs.getBoolean("count", true)

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = try {
            mpm.getMediaProjection(code, data)
        } catch (t: Throwable) {
            Log.e(TAG, "getMediaProjection failed", t)
            stopSelf()
            return START_NOT_STICKY
        }
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                ui.post { stopSelf() }
            }
        }, ui)

        val size = screenSize()
        val w = size.first
        val h = size.second
        val dpi = resources.displayMetrics.densityDpi
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        virtualDisplay = proj.createVirtualDisplay(
            "bj-capture", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, null
        )

        val t = HandlerThread("bj-reader")
        t.start()
        thread = t
        val hd = Handler(t.looper)
        handler = hd

        overlay = Overlay(
            this,
            onResetShoe = { hd.post { tracker.resetShoe(); publish() } },
            onNewHand = { hd.post { tracker.clearRound(); publish() } },
            onDebug = { hd.post { dumpDebug() } },
            onStop = { stopSelf() }
        )
        overlay?.show()

        started = true
        hd.post {
            cardReader = CardReader()
            tick()
        }
        return START_NOT_STICKY
    }

    private fun toBitmap(img: Image): Bitmap {
        val plane = img.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * img.width
        val wide = Bitmap.createBitmap(
            img.width + rowPadding / pixelStride, img.height, Bitmap.Config.ARGB_8888
        )
        wide.copyPixelsFromBuffer(plane.buffer)
        if (rowPadding == 0) return wide
        val out = Bitmap.createBitmap(wide, 0, 0, img.width, img.height)
        wide.recycle()
        return out
    }

    private fun tick() {
        val hd = handler ?: return
        try {
            val img = reader?.acquireLatestImage()
            if (img != null) {
                val fresh = try {
                    toBitmap(img)
                } finally {
                    img.close()
                }
                lastBitmap?.recycle()
                lastBitmap = fresh
            }
            val bmp = lastBitmap
            val cr = cardReader
            if (bmp != null && cr != null) {
                val rd = cr.read(bmp)
                lastReading = rd
                if (rd.ok) {
                    val now = SystemClock.elapsedRealtime()
                    tracker.onReading(rd.dealer, rd.player, now)
                    tracker.tick(now)
                    publish()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "tick failed", t)
        }
        hd.postDelayed({ tick() }, TICK_MS)
    }

    private fun label(r: Int): String = if (r == 11) "A" else r.toString()

    private fun joinCards(l: List<Int>): String =
        if (l.isEmpty()) "-" else l.joinToString(" ") { label(it) }

    private fun betUnits(tc: Double): Int = when {
        tc < 2.0 -> 1
        tc < 3.0 -> 2
        tc < 4.0 -> 4
        tc < 5.0 -> 6
        else -> 8
    }

    private fun publish() {
        val d = tracker.dealer
        val p = tracker.player
        val rc = tracker.rc()
        val seen = tracker.seen()
        val left = maxOf(decks - seen / 52.0, 0.25)
        val tc = rc / maxOf(left, 0.5)

        var big = "Kart görünmür"
        var sub = "Oyun masasını aç"
        var color = NEUTRAL

        if (d.isNotEmpty() || p.isNotEmpty()) {
            if (d.isEmpty() || p.size < 2) {
                big = "Oxuyuram..."
                sub = ""
            } else {
                val a = Strategy.advise(p, d[0], false, countMode, tc)
                if (a.act == "BUST") {
                    big = "Keçdin"
                    sub = "BUST " + a.total
                    color = C_BUST
                } else if (a.act == "BJ") {
                    big = "Blackjack!"
                    sub = "21"
                    color = C_STAND
                } else if (d.size >= 2) {
                    big = "Diler oynayır"
                    sub = ""
                } else {
                    when (a.act) {
                        "H" -> { big = "Kart al"; color = C_HIT; sub = "HIT" }
                        "S" -> { big = "Dayan"; color = C_STAND; sub = "STAND" }
                        "P" -> { big = "Böl"; color = C_SPLIT; sub = "SPLIT" }
                        else -> { big = "İkiqat"; color = C_DOUBLE; sub = "DOUBLE" }
                    }
                    val kind = when {
                        a.twentyOne -> "21"
                        a.kind == "pair" -> "Cüt " + label(p[0]) + "-" + label(p[1])
                        a.kind == "soft" -> "Soft " + a.total
                        else -> "Hard " + a.total
                    }
                    sub = sub + " · " + kind + " · diler " + label(d[0])
                    if (a.fallback == "H") sub += " (olmasa kart al)"
                    if (a.fallback == "S") sub += " (olmasa dayan)"
                }
            }
        }

        var hands = ""
        if (d.isNotEmpty() || p.isNotEmpty()) {
            val e = Strategy.evalHand(p)
            hands = "Diler: " + joinCards(d) + "\nMən: " + joinCards(p)
            if (p.isNotEmpty()) hands += " = " + e.total + (if (e.soft) " soft" else "")
        }

        var count = ""
        if (countMode) {
            count = "RC " + (if (rc > 0) "+" + rc else rc.toString()) +
                " · TC " + String.format(Locale.US, "%+.1f", tc) +
                " · bahis " + betUnits(tc) + "×"
            if (d.isNotEmpty() && d[0] == 11 && p.size >= 2) {
                count += "\nSığorta: " + (if (tc >= 3.0) "götür" else "götürmə")
            }
        }

        val st = Overlay.UiState(big, sub, color, hands, count)
        ui.post { overlay?.render(st) }
    }

    private fun toast(msg: String) {
        ui.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    private fun dumpDebug() {
        val bmp = lastBitmap
        if (bmp == null) {
            toast("Hələ görüntü yoxdur")
            return
        }
        val note = "D: " + joinCards(tracker.dealer) + " | P: " + joinCards(tracker.player) + " | RC " + tracker.rc()
        val saved = DebugDump.save(this, bmp, lastReading, note)
        toast(if (saved != null) "Şəkil saxlandı: Pictures/BlackjackHelper" else "Şəkli saxlamaq alınmadı")
    }

    override fun onDestroy() {
        started = false
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        try { virtualDisplay?.release() } catch (t: Throwable) { Log.w(TAG, "release display", t) }
        try { reader?.close() } catch (t: Throwable) { Log.w(TAG, "close reader", t) }
        try { projection?.stop() } catch (t: Throwable) { Log.w(TAG, "stop projection", t) }
        overlay?.hide()
        lastBitmap = null
        super.onDestroy()
    }
}
