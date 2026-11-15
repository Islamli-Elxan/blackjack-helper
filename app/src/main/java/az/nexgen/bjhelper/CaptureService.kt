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
        @Volatile var running = false
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "capture"
        private const val NOTIF_ID = 17
        private const val TAG = "BJHelper"
        private const val TICK_MS = 450L
        private const val PEEK_WAIT_MS = 190L
        private const val MEMORY_MS = 10_000L

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
    private var screenW = 0
    private var screenH = 0
    @Volatile private var ovBounds: CardFinder.Box? = null
    private var blobMemory: List<CardFinder.Box> = emptyList()
    private var blobMemoryAt = 0L
    @Volatile private var diag = ""

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
        screenW = w
        screenH = h
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
            onBalance = { openBalance() },
            onStop = { stopSelf() },
            onLayoutChanged = { onOverlayLayout() }
        )
        overlay?.show()
        ui.postDelayed({ onOverlayLayout() }, 400)

        started = true
        running = true
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

    private fun freshBlobs(now: Long): List<CardFinder.Box> =
        if (now - blobMemoryAt < MEMORY_MS) blobMemory else emptyList()

    /** Runs on the main thread whenever the window changes size or content. */
    private fun onOverlayLayout() {
        val ov = overlay ?: return
        if (screenW == 0) return
        var b = ov.bounds()
        if (!ov.manual) {
            val blobs = freshBlobs(SystemClock.elapsedRealtime())
            if (Placer.covers(b, screenW, screenH, blobs)) {
                val pos = Placer.choose(screenW, screenH, b.w, b.h, blobs, ov.inset())
                if (pos.x != b.l || pos.y != b.t) {
                    ov.moveTo(pos.x, pos.y)
                    b = CardFinder.Box(pos.x, pos.y, pos.x + b.w, pos.y + b.h)
                }
            }
        }
        ovBounds = b
    }

    private fun drain() {
        try {
            reader?.acquireLatestImage()?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "drain", t)
        }
    }

    /** Newest frame, or null if the screen has not produced a new one. */
    private fun grab(): Bitmap? {
        val img = reader?.acquireLatestImage() ?: return null
        return try {
            toBitmap(img)
        } finally {
            img.close()
        }
    }

    private fun tick() {
        val hd = handler ?: return
        try {
            // a still screen produces no new frame: keep reading the last one so the tracker sees time pass
            val fresh = grab()
            if (fresh != null) {
                lastBitmap?.recycle()
                lastBitmap = fresh
            }
            val bmp = lastBitmap
            if (bmp != null) readFrame(bmp)
        } catch (t: Throwable) {
            Log.e(TAG, "tick failed", t)
        }
        hd.postDelayed({ tick() }, TICK_MS)
    }

    private fun readFrame(bmp: Bitmap) {
        val cr = cardReader ?: return
        val rd = cr.read(bmp, ovBounds)
        lastReading = rd
        diag = rd.diag
        val now = SystemClock.elapsedRealtime()
        if (rd.blobs.isNotEmpty()) {
            blobMemory = rd.blobs
            blobMemoryAt = now
        }
        if (rd.ok) {
            tracker.onReading(rd.dealer, rd.player, now)
            tracker.tick(now)
        }
        publish()
    }

    private fun label(r: Int): String = if (r == 11) "A" else r.toString()

    private fun joinCards(l: List<Int>): String =
        if (l.isEmpty()) "-" else l.joinToString(" ") { label(it) }

    private fun publish() {
        val d = tracker.dealer
        val p = tracker.player
        val rc = tracker.rc()
        val seen = tracker.seen()
        val left = maxOf(decks - seen / 52.0, 0.25)
        val tc = rc / maxOf(left, 0.5)

        var badge = "·"
        var big = "Kart görünmür"
        var sub = "Oyun masasını aç"
        var color = NEUTRAL

        if (d.isNotEmpty() || p.isNotEmpty()) {
            if (d.isEmpty() || p.size < 2) {
                badge = "…"
                big = "Oxuyuram"
                sub = ""
            } else {
                val a = Strategy.advise(p, d[0], false, countMode, tc)
                if (a.act == "BUST") {
                    badge = "✕"
                    big = "Keçdin"
                    sub = "BUST " + a.total
                    color = C_BUST
                } else if (a.act == "BJ") {
                    badge = "★"
                    big = "Blackjack!"
                    sub = "21"
                    color = C_STAND
                } else if (d.size >= 2) {
                    badge = "…"
                    big = "Diler oynayır"
                    sub = ""
                } else {
                    when (a.act) {
                        "H" -> { badge = "H"; big = "Kart al"; color = C_HIT; sub = "HIT" }
                        "S" -> { badge = "S"; big = "Dayan"; color = C_STAND; sub = "STAND" }
                        "P" -> { badge = "P"; big = "Böl"; color = C_SPLIT; sub = "SPLIT" }
                        else -> { badge = "D"; big = "İkiqat"; color = C_DOUBLE; sub = "DOUBLE" }
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

        var total = ""
        if (p.isNotEmpty()) {
            val e = Strategy.evalHand(p)
            total = e.total.toString() + (if (e.soft) " soft" else "")
        }

        var rcText = ""
        var tcText = ""
        var tcColor = 0xFFE6F0E9.toInt()
        var note = ""
        if (countMode) {
            rcText = if (rc > 0) "+" + rc else rc.toString()
            tcText = String.format(Locale.US, "%+.1f", tc)
            tcColor = when {
                tc >= 2.0 -> 0xFF7FE3A2.toInt()
                tc <= -1.0 -> 0xFFFF9F96.toInt()
                else -> 0xFFE6F0E9.toInt()
            }
            if (d.isNotEmpty() && d[0] == 11 && p.size >= 2) {
                note = "Sığorta: " + (if (tc >= 3.0) "götür" else "götürmə")
            }
        }

        val prefs = getSharedPreferences("bj", Context.MODE_PRIVATE)
        val balance = prefs.getLong("balance", 0L)
        var bet = ""
        if (balance > 0L) {
            val plan = Betting.recommend(
                balance, tc, countMode, prefs.getInt("risk", 1),
                prefs.getLong("minBet", 0L), prefs.getLong("maxBet", 0L)
            )
            bet = Betting.format(plan.amount)
        }

        val st = Overlay.UiState(
            badge, big, sub, color, d, p, total, rcText, tcText, tcColor, bet, note, diag
        )
        ui.post { overlay?.render(st) }
    }

    private fun openBalance() {
        val i = Intent(this, BalanceActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
        } catch (t: Throwable) {
            Log.w(TAG, "balance dialog", t)
        }
    }

    private fun toast(msg: String) {
        ui.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    private fun dumpDebug() {
        val hd = handler ?: return
        // look at the game without our own window in the way
        drain()
        ui.post { overlay?.setHidden(true) }
        hd.postDelayed({
            val fresh = grab()
            ui.post { overlay?.setHidden(false) }
            val bmp = fresh ?: lastBitmap
            if (bmp == null) {
                toast("Hələ görüntü yoxdur")
            } else {
                if (fresh != null) {
                    lastBitmap?.recycle()
                    lastBitmap = fresh
                }
                val rd = cardReader?.read(bmp)
                lastReading = rd
                val note = "D: " + joinCards(tracker.dealer) + " | P: " + joinCards(tracker.player) +
                    " | RC " + tracker.rc()
                val raw = DebugDump.saveRaw(this, bmp)
                val saved = DebugDump.save(this, bmp, rd, note)
                toast(if (saved != null && raw != null) "2 şəkil saxlandı: Pictures/BlackjackHelper" else "Şəkli saxlamaq alınmadı")
            }
        }, PEEK_WAIT_MS)
    }

    override fun onDestroy() {
        started = false
        running = false
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
