package az.nexgen.bjhelper

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Small view helpers shared by the screens, so they look alike. */
object Ui {
    val BG = 0xFF0A120E.toInt()
    val CARD = 0xFF132019.toInt()
    val CARD_LINE = 0xFF22362B.toInt()
    val TEXT = 0xFFE9F2EC.toInt()
    val MUTED = 0xFF93A99C.toInt()
    val GOLD = 0xFFF0C14B.toInt()
    val GREEN = 0xFF3DDC97.toInt()
    val FIELD = 0xFF0C1712.toInt()

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun rounded(ctx: Context, color: Int, radius: Int, line: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(ctx, radius).toFloat()
        if (line != 0) g.setStroke(dp(ctx, 1), line)
        return g
    }

    fun text(ctx: Context, t: String, size: Float, color: Int = TEXT, bold: Boolean = false): TextView {
        val tv = TextView(ctx)
        tv.text = t
        tv.textSize = size
        tv.setTextColor(color)
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD)
        return tv
    }

    fun card(ctx: Context): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(ctx, CARD, 18, CARD_LINE)
        c.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 16))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, dp(ctx, 12))
        c.layoutParams = lp
        return c
    }

    fun button(ctx: Context, label: String, fill: Int, fg: Int, click: () -> Unit): TextView {
        val b = text(ctx, label, 15f, fg, true)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        b.background = rounded(ctx, fill, 14)
        b.setOnClickListener { click() }
        return b
    }

    /** Row of choices where exactly one is lit. */
    class Segmented(private val ctx: Context, labels: List<String>, selected: Int, private val onPick: (Int) -> Unit) {
        val view = LinearLayout(ctx)
        private val items = ArrayList<TextView>()

        init {
            view.orientation = LinearLayout.HORIZONTAL
            view.background = rounded(ctx, FIELD, 12, CARD_LINE)
            view.setPadding(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3))
            for ((i, l) in labels.withIndex()) {
                val t = text(ctx, l, 13.5f, MUTED, true)
                t.gravity = Gravity.CENTER
                t.setPadding(0, dp(ctx, 9), 0, dp(ctx, 9))
                t.setOnClickListener {
                    select(i)
                    onPick(i)
                }
                view.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                items.add(t)
            }
            select(selected)
        }

        fun select(i: Int) {
            for ((k, t) in items.withIndex()) {
                if (k == i) {
                    t.setTextColor(0xFF10201A.toInt())
                    t.background = rounded(ctx, GREEN, 9)
                } else {
                    t.setTextColor(MUTED)
                    t.background = null
                }
            }
        }
    }

    fun gap(ctx: Context, h: Int): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(1, dp(ctx, h))
        return v
    }
}
