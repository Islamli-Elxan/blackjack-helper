package az.nexgen.bjhelper

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Small dialog opened from the floating window: set the current balance without leaving the game. */
class BalanceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("bj", Context.MODE_PRIVATE)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setBackgroundDrawable(Ui.rounded(this, Ui.CARD, 20, Ui.CARD_LINE))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18))

        col.addView(Ui.text(this, "Cari balans", 18f, Ui.TEXT, true))
        col.addView(Ui.text(this, "Oyundakı puanını yaz, mərc tövsiyəsi ona görə hesablanır.", 12.5f, Ui.MUTED))
        col.addView(Ui.gap(this, 10))

        val field = EditText(this)
        field.inputType = InputType.TYPE_CLASS_NUMBER
        field.setTextColor(Ui.TEXT)
        field.setHintTextColor(Ui.MUTED)
        field.hint = "məs. 25000"
        field.textSize = 20f
        field.gravity = Gravity.CENTER
        field.background = Ui.rounded(this, Ui.FIELD, 12, Ui.CARD_LINE)
        field.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12))
        val cur = prefs.getLong("balance", 0L)
        if (cur > 0L) field.setText(cur.toString())
        field.setSelection(field.text.length)
        col.addView(field)
        col.addView(Ui.gap(this, 12))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val cancel = Ui.button(this, "Bağla", Ui.FIELD, Ui.MUTED) { finish() }
        val save = Ui.button(this, "Saxla", Ui.GOLD, 0xFF2A1D00.toInt()) {
            val v = field.text.toString().trim().toLongOrNull() ?: 0L
            prefs.edit().putLong("balance", v.coerceAtLeast(0L)).apply()
            finish()
        }
        val lp1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp1.setMargins(0, 0, Ui.dp(this, 6), 0)
        val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f)
        row.addView(cancel, lp1)
        row.addView(save, lp2)
        col.addView(row)

        setContentView(col)
    }
}
