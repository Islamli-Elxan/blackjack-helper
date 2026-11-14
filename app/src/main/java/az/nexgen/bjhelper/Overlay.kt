package az.nexgen.bjhelper

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/** Small draggable window that floats above the game. Tap the title to collapse it. */
class Overlay(
    private val ctx: Context,
    private val onResetShoe: () -> Unit,
    private val onNewHand: () -> Unit,
    private val onDebug: () -> Unit,
    private val onStop: () -> Unit
) {

    class UiState(
        val big: String,
        val sub: String,
        val color: Int,
        val hands: String,
        val count: String
    )

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val root = LinearLayout(ctx)
    private val details = LinearLayout(ctx)
    private val bigTv = TextView(ctx)
    private val subTv = TextView(ctx)
    private val handsTv = TextView(ctx)
    private val countTv = TextView(ctx)
    private var compact = false
    private var added = false

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    private val params = WindowManager.LayoutParams(
        dp(250),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    )

    init {
        params.gravity = Gravity.TOP or Gravity.START
        params.x = dp(12)
        params.y = (ctx.resources.displayMetrics.heightPixels * 0.37f).toInt()

        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(10), dp(12), dp(10))
        val bg = GradientDrawable()
        bg.setColor(0xEB0F1A14.toInt())
        bg.cornerRadius = dp(16).toFloat()
        root.background = bg

        bigTv.textSize = 26f
        bigTv.setTypeface(Typeface.DEFAULT_BOLD)
        bigTv.setTextColor(Color.WHITE)
        bigTv.text = "Hazırdır"

        subTv.textSize = 12f
        subTv.setTextColor(0xFFB9C9BF.toInt())

        handsTv.textSize = 13f
        handsTv.setTextColor(Color.WHITE)
        handsTv.setPadding(0, dp(8), 0, 0)

        countTv.textSize = 12f
        countTv.setTextColor(0xFFB9C9BF.toInt())
        countTv.setPadding(0, dp(2), 0, 0)

        val buttons = LinearLayout(ctx)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.setPadding(0, dp(10), 0, 0)
        buttons.addView(button("Say ↺") { onResetShoe() })
        buttons.addView(button("Yeni") { onNewHand() })
        buttons.addView(button("Debug") { onDebug() })
        buttons.addView(button("✕") { onStop() })

        details.orientation = LinearLayout.VERTICAL
        details.addView(handsTv)
        details.addView(countTv)
        details.addView(buttons)

        root.addView(bigTv)
        root.addView(subTv)
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
                        if (abs(dx) > 8 || abs(dy) > 8) moved = true
                        params.x = startX + dx
                        params.y = startY + dy
                        if (added) wm.updateViewLayout(root, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) toggleCompact()
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
        tv.setPadding(dp(4), dp(9), dp(4), dp(9))
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
        subTv.visibility = if (s.sub.isEmpty()) View.GONE else View.VISIBLE
        handsTv.text = s.hands
        countTv.text = s.count
        countTv.visibility = if (s.count.isEmpty()) View.GONE else View.VISIBLE
    }
}
