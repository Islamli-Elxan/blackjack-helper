package az.nexgen.bjhelper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val reqCapture = 1001
    private val reqNotif = 1002

    private lateinit var permDot: TextView
    private lateinit var permText: TextView
    private lateinit var permBtn: TextView
    private lateinit var runBtn: TextView
    private lateinit var preview: TextView
    private lateinit var balanceField: EditText
    private lateinit var minField: EditText
    private lateinit var maxField: EditText
    private val ui = Handler(Looper.getMainLooper())

    private fun dp(v: Int): Int = Ui.dp(this, v)

    private fun prefs() = getSharedPreferences("bj", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        val p = prefs()

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(28), dp(16), dp(28))

        col.addView(header())
        col.addView(Ui.gap(this, 18))

        // --- steps
        val steps = Ui.card(this)
        steps.addView(Ui.text(this, "BAŞLAMAQ ÜÇÜN", 11f, Ui.MUTED, true))
        steps.addView(Ui.gap(this, 10))

        val permRow = LinearLayout(this)
        permRow.orientation = LinearLayout.HORIZONTAL
        permRow.gravity = Gravity.CENTER_VERTICAL
        permDot = Ui.text(this, "1", 14f, 0xFF10201A.toInt(), true)
        permDot.gravity = Gravity.CENTER
        permRow.addView(permDot, LinearLayout.LayoutParams(dp(28), dp(28)))
        val permCol = LinearLayout(this)
        permCol.orientation = LinearLayout.VERTICAL
        permCol.setPadding(dp(12), 0, dp(8), 0)
        permCol.addView(Ui.text(this, "Üzən pəncərə icazəsi", 15f, Ui.TEXT, true))
        permText = Ui.text(this, "", 12f, Ui.MUTED)
        permCol.addView(permText)
        permRow.addView(permCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        permBtn = Ui.button(this, "Ver", Ui.FIELD, Ui.GREEN) { openOverlaySettings() }
        permBtn.textSize = 13f
        permBtn.setPadding(dp(16), dp(8), dp(16), dp(8))
        permRow.addView(permBtn)
        steps.addView(permRow)
        steps.addView(Ui.gap(this, 14))

        runBtn = Ui.button(this, "▶  Başla", Ui.GOLD, 0xFF2A1D00.toInt()) { toggleRun() }
        runBtn.textSize = 17f
        steps.addView(runBtn)
        col.addView(steps)

        // --- balance and bet
        val bank = Ui.card(this)
        bank.addView(Ui.text(this, "BALANS VƏ MƏRC", 11f, Ui.MUTED, true))
        bank.addView(Ui.gap(this, 10))
        bank.addView(Ui.text(this, "Oyundakı balansın (puan)", 13f, Ui.TEXT, true))
        balanceField = numberField("məs. 25000")
        val bal = p.getLong("balance", 0L)
        if (bal > 0L) balanceField.setText(bal.toString())
        bank.addView(balanceField)
        bank.addView(Ui.gap(this, 12))

        bank.addView(Ui.text(this, "Risk səviyyəsi", 13f, Ui.TEXT, true))
        bank.addView(Ui.gap(this, 6))
        bank.addView(Ui.Segmented(this, Betting.RISK_NAMES.toList(), p.getInt("risk", 1)) {
            prefs().edit().putInt("risk", it).apply()
            updatePreview()
        }.view)
        bank.addView(Ui.gap(this, 12))

        val limits = LinearLayout(this)
        limits.orientation = LinearLayout.HORIZONTAL
        val minCol = LinearLayout(this)
        minCol.orientation = LinearLayout.VERTICAL
        minCol.addView(Ui.text(this, "Ən az mərc", 12f, Ui.MUTED))
        minField = numberField("istəyə görə")
        minField.textSize = 15f
        val mn = p.getLong("minBet", 0L)
        if (mn > 0L) minField.setText(mn.toString())
        minCol.addView(minField)
        val maxCol = LinearLayout(this)
        maxCol.orientation = LinearLayout.VERTICAL
        maxCol.addView(Ui.text(this, "Ən çox mərc", 12f, Ui.MUTED))
        maxField = numberField("istəyə görə")
        maxField.textSize = 15f
        val mx = p.getLong("maxBet", 0L)
        if (mx > 0L) maxField.setText(mx.toString())
        maxCol.addView(maxField)
        val l1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        l1.setMargins(0, 0, dp(6), 0)
        val l2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        l2.setMargins(dp(6), 0, 0, 0)
        limits.addView(minCol, l1)
        limits.addView(maxCol, l2)
        bank.addView(limits)
        bank.addView(Ui.gap(this, 12))

        preview = Ui.text(this, "", 12.5f, Ui.MUTED)
        preview.setPadding(dp(12), dp(10), dp(12), dp(10))
        preview.background = Ui.rounded(this, Ui.FIELD, 12, Ui.CARD_LINE)
        bank.addView(preview)
        col.addView(bank)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs().edit()
                    .putLong("balance", balanceField.text.toString().toLongOrNull() ?: 0L)
                    .putLong("minBet", minField.text.toString().toLongOrNull() ?: 0L)
                    .putLong("maxBet", maxField.text.toString().toLongOrNull() ?: 0L)
                    .apply()
                updatePreview()
            }
        }
        balanceField.addTextChangedListener(watcher)
        minField.addTextChangedListener(watcher)
        maxField.addTextChangedListener(watcher)

        // --- game
        val game = Ui.card(this)
        game.addView(Ui.text(this, "OYUN", 11f, Ui.MUTED, true))
        game.addView(Ui.gap(this, 10))
        game.addView(Ui.text(this, "Dəstə sayı", 13f, Ui.TEXT, true))
        game.addView(Ui.gap(this, 6))
        val decks = listOf(1, 2, 4, 6, 8)
        game.addView(Ui.Segmented(this, decks.map { it.toString() }, decks.indexOf(p.getInt("decks", 6)).coerceAtLeast(0)) {
            prefs().edit().putInt("decks", decks[it]).apply()
        }.view)
        game.addView(Ui.gap(this, 14))

        val countRow = LinearLayout(this)
        countRow.orientation = LinearLayout.HORIZONTAL
        countRow.gravity = Gravity.CENTER_VERTICAL
        val countCol = LinearLayout(this)
        countCol.orientation = LinearLayout.VERTICAL
        countCol.addView(Ui.text(this, "Kart sayma (Hi-Lo)", 14f, Ui.TEXT, true))
        countCol.addView(
            Ui.text(
                this,
                "Say əsasında strategiya dəyişir, mərc artır və sığorta deyilir. Oyun hər əldən sonra " +
                    "qarışdırırsa söndür.",
                12f, Ui.MUTED
            )
        )
        countRow.addView(countCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this)
        sw.isChecked = p.getBoolean("count", true)
        sw.setOnCheckedChangeListener { _, on ->
            prefs().edit().putBoolean("count", on).apply()
        }
        countRow.addView(sw)
        game.addView(countRow)
        col.addView(game)

        // --- tips
        val tips = Ui.card(this)
        tips.addView(Ui.text(this, "İPUCLARI", 11f, Ui.MUTED, true))
        tips.addView(Ui.gap(this, 8))
        tips.addView(
            Ui.text(
                this,
                "•  Pəncərəyə toxun: əllər, say və düymələr açılır. Sürüşdürsən yerini dəyişir.\n" +
                    "•  ◉ işarəli rəqəm növbəti əl üçün tövsiyə olunan mərcdir.\n" +
                    "•  Balans dəyişəndə pəncərədəki Balans düyməsi ilə yenilə.\n" +
                    "•  Android 14 və yuxarıda ekran paylaşanda \"Bir tətbiq\" seçib oyunu göstər, " +
                    "pəncərə görüntüyə düşməz.\n" +
                    "•  Səhv oxuyursa pəncərədə Debug düyməsinə bas və Pictures/BlackjackHelper şəkillərini göndər.",
                12.5f, Ui.MUTED
            ).also { it.setLineSpacing(0f, 1.15f) }
        )
        col.addView(tips)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(col)
        setContentView(scroll)

        updatePreview()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), reqNotif)
        }
    }

    private fun header(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val logo = ImageView(this)
        val layers = LayerDrawable(
            arrayOf(
                resources.getDrawable(R.drawable.ic_launcher_background, theme),
                resources.getDrawable(R.drawable.ic_launcher_foreground, theme)
            )
        )
        logo.setImageDrawable(layers)
        logo.scaleType = ImageView.ScaleType.CENTER_CROP
        logo.clipToOutline = true
        logo.background = Ui.rounded(this, Ui.CARD, 20)
        logo.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        row.addView(logo, LinearLayout.LayoutParams(dp(72), dp(72)))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), 0, 0, 0)
        col.addView(Ui.text(this, "Blackjack Köməkçisi", 22f, Ui.TEXT, true))
        col.addView(Ui.text(this, "Strategiya  ·  Hi-Lo say  ·  Mərc", 13f, Ui.GOLD, true))
        col.addView(Ui.text(this, "Ekranı oxuyur, kartları tanıyır, nə etməli olduğunu deyir.", 12f, Ui.MUTED))
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun numberField(hint: String): EditText {
        val f = EditText(this)
        f.inputType = InputType.TYPE_CLASS_NUMBER
        f.setTextColor(Ui.TEXT)
        f.setHintTextColor(0xFF5E7368.toInt())
        f.hint = hint
        f.textSize = 18f
        f.isSingleLine = true
        f.background = Ui.rounded(this, Ui.FIELD, 12, Ui.CARD_LINE)
        f.setPadding(dp(14), dp(11), dp(14), dp(11))
        return f
    }

    private fun updatePreview() {
        val p = prefs()
        val bal = p.getLong("balance", 0L)
        if (bal <= 0L) {
            preview.text = "Balansı yaz, hər əl üçün neçə puanla oynamalı olduğunu göstərim."
            return
        }
        val risk = p.getInt("risk", 1)
        val mn = p.getLong("minBet", 0L)
        val mx = p.getLong("maxBet", 0L)
        fun bet(tc: Double) = Betting.format(Betting.recommend(bal, tc, true, risk, mn, mx).amount)
        val flat = Betting.format(Betting.recommend(bal, 0.0, false, risk, mn, mx).amount)
        preview.text = "Say yoxdur / aşağıdırsa: " + flat + " puan\n" +
            "TC +3: " + bet(3.0) + "   ·   TC +5: " + bet(5.0) + "   ·   TC +7: " + bet(7.0)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        ui.postDelayed({ refresh() }, 700)
    }

    private fun refresh() {
        val ok = Settings.canDrawOverlays(this)
        permDot.background = Ui.rounded(this, if (ok) Ui.GREEN else Ui.GOLD, 14)
        permDot.text = if (ok) "✓" else "1"
        permText.text = if (ok) "Verilib" else "Pəncərənin oyunun üstündə görünməsi üçün lazımdır"
        permBtn.visibility = if (ok) android.view.View.GONE else android.view.View.VISIBLE
        if (CaptureService.running) {
            runBtn.text = "■  Dayandır"
            runBtn.background = Ui.rounded(this, 0xFF3A1F1C.toInt(), 14)
            runBtn.setTextColor(0xFFFF9F96.toInt())
        } else {
            runBtn.text = "▶  Başla"
            runBtn.background = Ui.rounded(this, Ui.GOLD, 14)
            runBtn.setTextColor(0xFF2A1D00.toInt())
        }
    }

    private fun toggleRun() {
        if (CaptureService.running) {
            stopService(Intent(this, CaptureService::class.java))
            ui.postDelayed({ refresh() }, 300)
            Toast.makeText(this, "Dayandırıldı", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Əvvəl üzən pəncərə icazəsini ver", Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), reqCapture)
    }

    private fun openOverlaySettings() {
        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(i)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != reqCapture) return
        if (resultCode == RESULT_OK && data != null) {
            val i = Intent(this, CaptureService::class.java)
            i.putExtra(CaptureService.EXTRA_CODE, resultCode)
            i.putExtra(CaptureService.EXTRA_DATA, data)
            startForegroundService(i)
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "Ekran paylaşma icazəsi verilmədi", Toast.LENGTH_LONG).show()
        }
    }
}
