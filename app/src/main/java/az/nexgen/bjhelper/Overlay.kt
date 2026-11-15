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
 * Tap the title row to open or close the details; drag to move it somewhere else.
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
        val big: String,
        val sub: String,
        val color: Int,
        val hands: String,
        val count: String,
        val diag: String = ""
    )

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val root = LinearLayout(ctx)
    private val head = LinearLayout(ctx)
    private val details = LinearLayout(ctx)
    private val bigTv = TextView(ctx)
    private val subTv = TextView(ctx)
    private val handsTv = TextView(ctx)
    private val countTv = TextView(ctx)
    private val diagTv = TextView(ctx)
    private val buttons = LinearLayout(ctx)
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
        root.setPadding(dp(12), dp(6), dp(12), dp(6))
        val bg = GradientDrawable()
        bg.setColor(0xEB0F1A14.toInt())
        bg.cornerRadius = dp(14).toFloat()
        root.background = bg

        bigTv.textSize = 22f
        bigTv.setTypeface(Typeface.DEFAULT_BOLD)
        bigTv.setTextColor(Color.WHITE)
        bigTv.text = "Hazırdır"
        bigTv.isSingleLine = true

        subTv.textSize = 12f
        subTv.setTextColor(0xFFB9C9BF.toInt())
        subTv.isSingleLine = true
        subTv.ellipsize = TextUtils.TruncateAt.END
        subTv.gravity = Gravity.CENTER_VERTICAL
        subTv.setPadding(dp(10), 0, 0, 0)

        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(bigTv)
        head.addView(subTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        handsTv.textSize = 13f
        handsTv.setTextColor(Color.WHITE)
        handsTv.isSingleLine = true
        handsTv.ellipsize = TextUtils.TruncateAt.END

        countTv.textSize = 12f
        countTv.setTextColor(0xFFB9C9BF.toInt())
        countTv.isSingleLine = true
        countTv.ellipsize = TextUtils.TruncateAt.END

        diagTv.textSize = 10f
        diagTv.setTextColor(0xFF7F9488.toInt())
        diagTv.isSingleLine = true
        diagTv.ellipsize = TextUtils.TruncateAt.END

        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.setPadding(0, dp(4), 0, 0)
        buttons.addView(button("Say ↺") { onResetShoe() })
        buttons.addView(button("Yeni əl") { onNewHand() })
        buttons.addView(button("Debug") { onDebug() })
        buttons.addView(button("✕") { onStop() })

        details.orientation = LinearLayout.VERTICAL
        details.addView(handsTv)
        details.addView(countTv)
        details.addView(diagTv)
        details.addView(buttons)
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
                        if (!moved) toggleCompact()
                        else root.post { onLayoutChanged() }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun button(label: String, click: () -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = label
        tv.setTextColor(Color.WHITE)
        tv.textSize = 11f
        tv.isSingleLine = true
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(4), dp(5), dp(4), dp(5))
        val bg = GradientDrawable()
        bg.setColor(0x33FFFFFF)
        bg.cornerRadius = dp(8).toFloat()
        tv.background = bg
        tv.setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(dp(2), 0, dp(2), 0)
        tv.layoutParams = lp
        return tv
    }

    private fun toggleCompact() {
        compact = !compact
        details.visibility = if (compact) View.GONE else View.VISIBLE
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
        val h = if (root.height > 0) root.height else dp(60)
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
        bigTv.text = s.big
        bigTv.setTextColor(s.color)
        subTv.text = s.sub
        handsTv.text = s.hands.replace("\n", "   ")
        handsTv.visibility = if (s.hands.isEmpty()) View.GONE else View.VISIBLE
        countTv.text = s.count.replace("\n", "   ")
        countTv.visibility = if (s.count.isEmpty()) View.GONE else View.VISIBLE
        diagTv.text = s.diag
        diagTv.visibility = if (s.diag.isEmpty()) View.GONE else View.VISIBLE
    }
}
