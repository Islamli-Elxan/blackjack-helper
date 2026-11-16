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
        @Volatile var resetStats = false
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "capture"
        private const val NOTIF_ID = 17
        private const val TAG = "BJHelper"
        private const val TICK_MS = 120L
        private const val PEEK_WAIT_MS = 190L
        private const val MEMORY_MS = 10_000L

        private val NEUTRAL = 0xFFE6F0E9.toInt()
        private val C_HIT = 0xFFFFB978.toInt()
        private val C_STAND = 0xFF7FE3D2.toInt()
        private val C_DOUBLE = 0xFFAAC2FF.toInt()
        private val C_SPLIT = 0xFFD8B6FF.toInt()
        private val C_BUST = 0xFFFF9F96.toInt()
        private val C_WIN = 0xFF7FE3A2.toInt()
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val ui = Handler(Looper.getMainLooper())
    private var overlay: Overlay? = null
    private val tracker = RoundTracker()
    private val log = RoundLog(formatTime = { RoundStore.fmt(it) })
    private var layout = ""
    private var lastMissedAt = 0L
    private var savedRc = 0
    private var savedCards = 0
    private var version = "?"
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
        val prior = RoundStore.load(this)
        log.start(prior.second, prior.first)
        tracker.restore(prefs.getInt("shoeRc", 0), prefs.getInt("shoeCards", 0))
        savedRc = tracker.baseRc
        savedCards = tracker.baseCards
        version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (t: Throwable) {
            "?"
        }

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
            onResetShoe = {
                hd.post {
                    val cards = tracker.seen()
                    var msg = "Dəstə sıfırlandı, say 0-dan başlayır"
                    if (cards >= 10) {
                        RoundStore.appendShuffle(this, cards, tracker.rc(), decks)
                        msg = "Dəstə sıfırlandı. Oxunan kart: " + cards + " (≈" + String.format(Locale.US, "%.1f", cards / 52.0) + " dəstə)"
                    }
                    tracker.resetShoe()
                    publish()
                    toast(msg)
                }
            },
            onNewHand = { hd.post { tracker.clearRound(); publish() } },
            onDebug = { hd.post { dumpDebug() } },
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
            if (fresh != null && bmp != null) {
                readFrame(bmp)
            } else if (bmp != null) {
                // nothing changed on screen: only let time pass for the tracker
                if (tracker.tick(SystemClock.elapsedRealtime())) publish()
            }
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
        layout = rd.layout
        val now = SystemClock.elapsedRealtime()
        if (rd.blobs.isNotEmpty()) {
            blobMemory = rd.blobs
            blobMemoryAt = now
        }
        if (rd.ok) {
            // a row that could not be read this frame keeps what was accepted before
            val d = if (rd.dealerSure) rd.dealer else tracker.dealer
            val h = if (rd.playerSure) rd.hands else tracker.hands
            val fl = RoundTracker.Flags(
                if (rd.dealerSure) rd.dealerVerified else false,
                if (rd.playerSure) rd.playerVerified else false,
                if (rd.dealerSure) rd.dealerApprox else tracker.dealerApprox,
                if (rd.playerSure) rd.playerApprox else tracker.playerApprox
            )
            tracker.onHands(d, h, now, fl)
        }
        tracker.tick(now)
        if (rd.missed.isNotEmpty() && now - lastMissedAt >= 2000L) {
            lastMissedAt = now
            log.note(rd.diag)
            for (m in rd.missed) log.missed(m)
        }
        publish()
    }

    private fun label(r: Int): String = if (r == 11) "A" else r.toString()

    private fun joinCards(l: List<Int>): String =
        if (l.isEmpty()) "-" else l.joinToString(" ") { label(it) }

    private fun word(act: String): String = when (act) {
        "H" -> "Kart al"
        "S" -> "Dayan"
        "P" -> "Böl"
        "BUST" -> "Keçdi"
        "BJ" -> "21"
        else -> "İkiqat"
    }

    private fun letter(act: String): String = when (act) {
        "H" -> "H"
        "S" -> "S"
        "P" -> "P"
        "BUST" -> "✕"
        "BJ" -> "★"
        else -> "D"
    }

    private fun tint(act: String): Int = when (act) {
        "H" -> C_HIT
        "S", "BJ" -> C_STAND
        "P" -> C_SPLIT
        "BUST" -> C_BUST
        else -> C_DOUBLE
    }

    private fun resultWord(k: Outcome.Kind): String = when (k) {
        Outcome.Kind.WIN, Outcome.Kind.DEALER_BUST -> "Qazandın"
        Outcome.Kind.BLACKJACK -> "Blackjack!"
        Outcome.Kind.LOSE, Outcome.Kind.BUST -> "Uduzdun"
        Outcome.Kind.PUSH -> "Bərabər"
    }

    private fun publish() {
        if (resetStats) {
            resetStats = false
            log.start(1, RoundLog.Stats())
        }
        val now = SystemClock.elapsedRealtime()
        val d = tracker.dealer
        val hands = tracker.hands
        val p = tracker.player
        val rc = tracker.rc()
        val seen = tracker.seen()
        val left = maxOf(decks - seen / 52.0, 0.25)
        val tc = (rc / maxOf(left, 1.0)).coerceIn(-12.0, 12.0)
        // far into the shoe the game has surely shuffled; a count nobody reset is not to be trusted
        val stale = seen > decks * 52 * 0.85
        val useCount = countMode && !stale

        var badge = "·"
        var big = "Kart görünmür"
        var sub = "Oyun masasını aç"
        var color = NEUTRAL
        val adviceCodes = ArrayList<String>()

        val outcomes = log.outcomes(d, hands)
        val decided = hands.isNotEmpty() && outcomes.all { it != null }
        val ready = d.isNotEmpty() && hands.isNotEmpty() && hands.all { it.size >= 2 }

        if (decided) {
            val kinds = outcomes.map { it!!.kind }
            val net = kinds.sumOf { k ->
                when (k) {
                    Outcome.Kind.WIN, Outcome.Kind.DEALER_BUST, Outcome.Kind.BLACKJACK -> 1
                    Outcome.Kind.LOSE, Outcome.Kind.BUST -> -1
                    Outcome.Kind.PUSH -> 0
                }.toInt()
            }
            color = if (net > 0) C_WIN else if (net < 0) C_BUST else NEUTRAL
            badge = if (net > 0) "✓" else if (net < 0) "✕" else "="
            big = kinds.joinToString(" · ") { resultWord(it) }
            val first = outcomes[0]!!
            sub = if (hands.size > 1) {
                outcomes.joinToString(" · ") { it!!.player.toString() } + " | diler " + first.dealer
            } else when (first.kind) {
                Outcome.Kind.DEALER_BUST -> "Diler keçdi (" + first.dealer + ")"
                Outcome.Kind.BLACKJACK -> "3:2 ilə"
                Outcome.Kind.BUST -> "Keçdin (" + first.player + ")"
                else -> first.player.toString() + " : " + first.dealer
            }
            for (h in hands) adviceCodes.add("-")
        } else if (d.isNotEmpty() || hands.isNotEmpty()) {
            if (!ready) {
                badge = "…"
                big = "Oxuyuram"
                sub = diag
                for (h in hands) adviceCodes.add("?")
            } else if (d.size >= 2) {
                badge = "…"
                big = "Diler oynayır"
                sub = ""
                for (h in hands) adviceCodes.add("-")
            } else {
                val advs = hands.map { Strategy.advise(it, d[0], false, useCount, tc) }
                val first = advs[0]
                badge = letter(first.act)
                color = tint(first.act)
                big = advs.joinToString(" · ") { word(it.act) }
                val parts = ArrayList<String>()
                for ((i, a) in advs.withIndex()) {
                    val hand = hands[i]
                    val kind = when {
                        a.act == "BUST" -> "Keçdi " + a.total
                        a.twentyOne -> "21"
                        a.kind == "pair" && !tracker.playerApprox -> "Cüt " + label(hand[0]) + "-" + label(hand[1])
                        a.kind == "soft" -> "Soft " + a.total
                        else -> "Hard " + a.total
                    }
                    var t = kind
                    if (a.fallback == "H") t += " (olmasa kart al)"
                    if (a.fallback == "S") t += " (olmasa dayan)"
                    parts.add(if (advs.size > 1) (i + 1).toString() + ") " + t else t)
                    adviceCodes.add(a.act + ":" + a.kind + a.total)
                }
                sub = parts.joinToString(" · ") + " · diler " + (if (tracker.dealerApprox) d[0].toString() else label(d[0])) +
                    (if (tracker.playerApprox || tracker.dealerApprox) " · ədədə görə" else "")
            }
        }

        val totals = hands.map {
            val e = Strategy.evalHand(it)
            e.total.toString() + (if (e.soft) "s" else "")
        }

        var rcText = ""
        var tcText = ""
        var tcColor = 0xFFE6F0E9.toInt()
        var note = ""
        if (countMode) {
            rcText = if (rc > 0) "+" + rc else rc.toString()
            tcText = if (stale) "?" else String.format(Locale.US, "%+.1f", tc)
            tcColor = when {
                stale -> 0xFFF0C14B.toInt()
                tc >= 2.0 -> 0xFF7FE3A2.toInt()
                tc <= -1.0 -> 0xFFFF9F96.toInt()
                else -> 0xFFE6F0E9.toInt()
            }
            if (stale) {
                note = "Say köhnədir: qarışdırmadan sonra ↺ bas"
            } else if (d.isNotEmpty() && d[0] == 11 && ready && !decided) {
                note = "Sığorta: " + (if (tc >= 3.0) "götür" else "götürmə")
            }
        }

        // the record of the round, and the counters
        val finished = log.update(
            now, System.currentTimeMillis(), d, hands, rc, tc, adviceCodes, layout, decks, countMode
        )
        if (finished != null) {
            RoundStore.append(this, log.toJson(finished, version, decks, countMode))
        }
        if (tracker.baseRc != savedRc || tracker.baseCards != savedCards) {
            savedRc = tracker.baseRc
            savedCards = tracker.baseCards
            getSharedPreferences("bj", Context.MODE_PRIVATE).edit()
                .putInt("shoeRc", savedRc).putInt("shoeCards", savedCards).apply()
        }

        val st = log.stats
        val statsLong = if (st.push > 0) "= " + st.push else ""
        val ui0 = Overlay.UiState(
            badge, big, sub, color, d, hands, totals, rcText, tcText, tcColor,
            st.short(), statsLong, note, diag, tracker.dealerApprox, tracker.playerApprox
        )
        ui.post { overlay?.render(ui0) }
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
        handler?.post {
            val r = log.update(0L, 0L, emptyList(), emptyList(), 0, 0.0, emptyList(), "", decks, countMode)
            if (r != null) RoundStore.append(applicationContext, log.toJson(r, version, decks, countMode))
        }
        thread?.quitSafely()
        try { virtualDisplay?.release() } catch (t: Throwable) { Log.w(TAG, "release display", t) }
        try { reader?.close() } catch (t: Throwable) { Log.w(TAG, "close reader", t) }
        try { projection?.stop() } catch (t: Throwable) { Log.w(TAG, "stop projection", t) }
        overlay?.hide()
        lastBitmap = null
        super.onDestroy()
    }
}
