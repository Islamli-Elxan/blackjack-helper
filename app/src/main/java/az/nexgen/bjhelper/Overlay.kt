package az.nexgen.bjhelper

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Wide, low window along the top of the screen, above the dealer's cards, so it never covers a card.
 * Closed: a round action badge, the advice, and the win/lose count.
 * Tap to open: both hands as small cards, the count and the buttons. Drag to move it.
 */
class Overlay(
    private val ctx: Context,
    private val onResetShoe: () -> Unit,
    private val onNewHand: () -> Unit,
    private val onDebug: () -> Unit,
    private val onStop: () -> Unit,
    private val onLayoutChanged: () -> Unit = {}
) {

    class UiState(
        val badge: String,
        val big: String,
        val sub: String,
        val color: Int,
        val dealer: List<Int>,
        val hands: List<List<Int>>,
        val totals: List<String>,
        val rc: String,
        val tc: String,
        val tcColor: Int,
        /** Short win/lose text such as "23W 17L". */
        val stats: String,
        val statsLong: String,
        val note: String,
        val diag: String,
        /** The dealer's / the player's hand is known only by the total the game prints. */
        val dealerApprox: Boolean = false,
        val playerApprox: Boolean = false
    )

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val root = LinearLayout(ctx)
    private val rootBg = GradientDrawable()
    private val head = LinearLayout(ctx)
    private val badgeTv = TextView(ctx)
    private val bigTv = TextView(ctx)
    private val subTv = TextView(ctx)
    private val shuffleTv = TextView(ctx)
    private val betTv = TextView(ctx)
    private val details = LinearLayout(ctx)
    private val handsRow = LinearLayout(ctx)
    private val statsRow = LinearLayout(ctx)
    private val pills = LinearLayout(ctx)
    private val buttons = LinearLayout(ctx)
    private val diagTv = TextView(ctx)
    private var compact = true
    private var added = false

    /** True once the person has dragged the window: it then stays where they put it. */
    var manual = false
        private set

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    private val params = WindowManager.LayoutParams(
        ctx.resources.displayMetrics.widthPixels - dp(16),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    )

    init {
        params.gravity = Gravity.TOP or Gravity.START
        params.x = dp(8)
        params.y = inset()

        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(10), dp(6), dp(10), dp(6))
        rootBg.setColor(0xF2101814.toInt())
        rootBg.cornerRadius = dp(18).toFloat()
        rootBg.setStroke(dp(2), 0x66FFFFFF)
        root.background = rootBg

        // head: badge, advice, bet
        badgeTv.textSize = 15f
        badgeTv.setTypeface(Typeface.DEFAULT_BOLD)
        badgeTv.gravity = Gravity.CENTER
        badgeTv.setTextColor(0xFF10201A.toInt())
        badgeTv.layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))

        bigTv.textSize = 21f
        bigTv.setTypeface(Typeface.DEFAULT_BOLD)
        bigTv.setTextColor(Color.WHITE)
        bigTv.isSingleLine = true
        bigTv.setPadding(dp(10), 0, 0, 0)

        subTv.textSize = 11.5f
        subTv.setTextColor(0xFFA9BBB0.toInt())
        subTv.isSingleLine = true
        subTv.ellipsize = TextUtils.TruncateAt.END
        subTv.setPadding(dp(8), 0, dp(6), 0)

        betTv.textSize = 13f
        betTv.setTypeface(Typeface.DEFAULT_BOLD)
        betTv.setTextColor(0xFF2A1D00.toInt())
        betTv.isSingleLine = true
        betTv.gravity = Gravity.CENTER
        betTv.setPadding(dp(10), dp(4), dp(10), dp(4))
        betTv.background = pillBg(0xFFF0C14B.toInt())

        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(badgeTv)
        head.addView(bigTv)
        head.addView(subTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        // one tap after the game shuffles the cards: the count starts again from zero
        shuffleTv.text = "↺"
        shuffleTv.textSize = 17f
        shuffleTv.setTypeface(Typeface.DEFAULT_BOLD)
        shuffleTv.setTextColor(0xFFF0C14B.toInt())
        shuffleTv.gravity = Gravity.CENTER
        shuffleTv.setPadding(dp(10), dp(2), dp(10), dp(2))
        shuffleTv.background = pillBg(0x33F0C14B)
        shuffleTv.setOnClickListener { onResetShoe() }
        val slp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        slp.setMargins(dp(4), 0, dp(6), 0)
        head.addView(shuffleTv, slp)
        head.addView(betTv)

        // details
        handsRow.orientation = LinearLayout.HORIZONTAL
        handsRow.gravity = Gravity.CENTER_VERTICAL
        handsRow.setPadding(0, dp(6), 0, 0)

        pills.orientation = LinearLayout.HORIZONTAL
        pills.gravity = Gravity.CENTER_VERTICAL

        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.gravity = Gravity.CENTER_VERTICAL
        buttons.addView(button("Say ↺") { onResetShoe() })
        buttons.addView(button("Yeni") { onNewHand() })
        buttons.addView(button("Debug") { onDebug() })
        buttons.addView(button("✕") { onStop() })

        statsRow.orientation = LinearLayout.HORIZONTAL
        statsRow.gravity = Gravity.CENTER_VERTICAL
        statsRow.setPadding(0, dp(6), 0, 0)
        statsRow.addView(pills, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        statsRow.addView(buttons)

        diagTv.textSize = 9.5f
        diagTv.setTextColor(0xFF6F8378.toInt())
        diagTv.isSingleLine = true
        diagTv.ellipsize = TextUtils.TruncateAt.END
        diagTv.setPadding(0, dp(3), 0, 0)

        details.orientation = LinearLayout.VERTICAL
        details.addView(handsRow)
        details.addView(statsRow)
        details.addView(diagTv)
        details.visibility = View.GONE

        root.addView(head)
        root.addView(details)

        root.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = e.rawX
                        touchY = e.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - touchX).toInt()
                        val dy = (e.rawY - touchY).toInt()
                        if (abs(dx) > 8 || abs(dy) > 8) {
                            moved = true
                            manual = true
                        }
                        if (moved) {
                            params.x = startX + dx
                            params.y = startY + dy
                            if (added) wm.updateViewLayout(root, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) toggleCompact() else root.post { onLayoutChanged() }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun pillBg(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(14).toFloat()
        return g
    }

    private fun button(label: String, click: () -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = label
        tv.setTextColor(0xFFE6F0E9.toInt())
        tv.textSize = 10.5f
        tv.isSingleLine = true
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(7), dp(5), dp(7), dp(5))
        val bg = GradientDrawable()
        bg.setColor(0x26FFFFFF)
        bg.cornerRadius = dp(9).toFloat()
        tv.background = bg
        tv.setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(2), 0, 0, 0)
        tv.layoutParams = lp
        return tv
    }

    /** A small playing card showing a rank. */
    private fun cardChip(text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.textSize = 12.5f
        tv.setTypeface(Typeface.DEFAULT_BOLD)
        tv.setTextColor(0xFF16231C.toInt())
        tv.gravity = Gravity.CENTER
        tv.minWidth = dp(22)
        tv.setPadding(dp(3), dp(2), dp(3), dp(2))
        val bg = GradientDrawable()
        bg.setColor(0xFFFBF8EE.toInt())
        bg.cornerRadius = dp(5).toFloat()
        tv.background = bg
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, dp(3), 0)
        tv.layoutParams = lp
        return tv
    }

    private fun tag(text: String, color: Int = 0xFF9DB1A5.toInt(), size: Float = 11f): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.textSize = size
        tv.setTextColor(color)
        tv.setTypeface(Typeface.DEFAULT_BOLD)
        tv.setPadding(0, 0, dp(5), 0)
        return tv
    }

    private fun pill(text: String, fg: Int): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.textSize = 11f
        tv.setTypeface(Typeface.DEFAULT_BOLD)
        tv.setTextColor(fg)
        tv.isSingleLine = true
        tv.setPadding(dp(8), dp(3), dp(8), dp(3))
        tv.background = pillBg(0x22FFFFFF)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, dp(4), 0)
        tv.layoutParams = lp
        return tv
    }

    private fun rankLabel(r: Int): String = if (r == 11) "A" else r.toString()

    private fun toggleCompact() {
        compact = !compact
        details.visibility = if (compact) View.GONE else View.VISIBLE
        subTv.visibility = if (compact) View.VISIBLE else View.GONE
        root.post { onLayoutChanged() }
    }

    /** Top edge of the window: just under the status bar. */
    fun inset(): Int {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        val bar = if (id > 0) ctx.resources.getDimensionPixelSize(id) else dp(24)
        return bar + dp(2)
    }

    /** Where the window is right now, in screen pixels. */
    fun bounds(): CardFinder.Box {
        val w = if (root.width > 0) root.width else params.width
        val h = if (root.height > 0) root.height else dp(56)
        return CardFinder.Box(params.x, params.y, params.x + w, params.y + h)
    }

    fun moveTo(x: Int, y: Int) {
        params.x = x
        params.y = y
        if (added) {
            try { wm.updateViewLayout(root, params) } catch (t: Throwable) { /* gone */ }
        }
    }

    /** Fully see-through for a moment, so a screen capture shows the game underneath. */
    fun setHidden(hidden: Boolean) {
        params.alpha = if (hidden) 0f else 1f
        if (added) {
            try { wm.updateViewLayout(root, params) } catch (t: Throwable) { /* gone */ }
        }
    }

    fun show() {
        if (added) return
        try {
            wm.addView(root, params)
            added = true
        } catch (t: Throwable) {
            Toast.makeText(ctx, "Üzən pəncərə açıla bilmədi: icazəni yoxla", Toast.LENGTH_LONG).show()
        }
    }

    fun hide() {
        if (!added) return
        try {
            wm.removeView(root)
        } catch (t: Throwable) {
            // already gone
        }
        added = false
    }

    fun render(s: UiState) {
        val accent = s.color
        rootBg.setStroke(dp(2), (accent and 0x00FFFFFF) or 0x99000000.toInt())

        val badgeBg = GradientDrawable()
        badgeBg.shape = GradientDrawable.OVAL
        badgeBg.setColor(accent)
        badgeTv.background = badgeBg
        badgeTv.text = s.badge

        bigTv.text = s.big
        bigTv.setTextColor(accent)
        subTv.text = s.sub
        subTv.visibility = if (compact && s.sub.isNotEmpty()) View.VISIBLE else View.GONE
        betTv.text = s.stats
        betTv.visibility = if (s.stats.isEmpty()) View.GONE else View.VISIBLE

        handsRow.removeAllViews()
        if (s.dealer.isNotEmpty() || s.hands.isNotEmpty()) {
            handsRow.addView(tag("DİLER"))
            if (s.dealerApprox) {
                if (s.dealer.isNotEmpty()) handsRow.addView(tag("~" + Strategy.evalHand(s.dealer).total, 0xFFF0C14B.toInt(), 13f))
            } else {
                for (c in s.dealer) handsRow.addView(cardChip(rankLabel(c)))
            }
            handsRow.addView(tag("MƏN").also { it.setPadding(dp(10), 0, dp(5), 0) })
            for ((i, hand) in s.hands.withIndex()) {
                if (i > 0) handsRow.addView(tag("│", 0xFF5E7368.toInt(), 14f).also { it.setPadding(dp(2), 0, dp(5), 0) })
                if (!s.playerApprox) for (c in hand) handsRow.addView(cardChip(rankLabel(c)))
                if (i < s.totals.size) handsRow.addView(tag((if (s.playerApprox) "~" else "=") + s.totals[i], if (s.playerApprox) 0xFFF0C14B.toInt() else 0xFFE6F0E9.toInt(), 12.5f))
            }
        } else {
            handsRow.addView(tag("Masa boşdur, kart gözləyirəm"))
        }

        pills.removeAllViews()
        if (s.statsLong.isNotEmpty()) pills.addView(pill(s.statsLong, 0xFFF0C14B.toInt()))
        if (s.rc.isNotEmpty()) pills.addView(pill("RC " + s.rc, 0xFFE6F0E9.toInt()))
        if (s.tc.isNotEmpty()) pills.addView(pill("TC " + s.tc, s.tcColor))
        if (s.note.isNotEmpty()) pills.addView(pill(s.note, 0xFFF0C14B.toInt()))

        diagTv.text = s.diag
        diagTv.visibility = if (s.diag.isEmpty()) View.GONE else View.VISIBLE
    }
}
