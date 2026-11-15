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
    private lateinit var tileWin: TextView
    private lateinit var tileLose: TextView
    private lateinit var tilePush: TextView
    private lateinit var tileBj: TextView
    private lateinit var summary: TextView
    private lateinit var recent: TextView
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

        // --- report
        val rep = Ui.card(this)
        rep.addView(Ui.text(this, "HESABAT", 11f, Ui.MUTED, true))
        rep.addView(Ui.gap(this, 10))
        val tiles = LinearLayout(this)
        tiles.orientation = LinearLayout.HORIZONTAL
        tileWin = tile(tiles, "Qazanc", Ui.GREEN)
        tileLose = tile(tiles, "Uduzma", 0xFFFF9F96.toInt())
        tilePush = tile(tiles, "Bərabər", Ui.TEXT)
        tileBj = tile(tiles, "Blackjack", Ui.GOLD)
        rep.addView(tiles)
        rep.addView(Ui.gap(this, 8))
        summary = Ui.text(this, "", 13f, Ui.TEXT, true)
        rep.addView(summary)
        rep.addView(Ui.gap(this, 10))
        recent = Ui.text(this, "", 12.5f, Ui.MUTED)
        recent.setLineSpacing(0f, 1.2f)
        recent.setPadding(dp(12), dp(10), dp(12), dp(10))
        recent.background = Ui.rounded(this, Ui.FIELD, 12, Ui.CARD_LINE)
        rep.addView(recent)
        rep.addView(Ui.gap(this, 12))
        val repButtons = LinearLayout(this)
        repButtons.orientation = LinearLayout.HORIZONTAL
        val send = Ui.button(this, "Hesabatı göndər", Ui.GOLD, 0xFF2A1D00.toInt()) { sendReport() }
        send.textSize = 14f
        val wipe = Ui.button(this, "Sıfırla", Ui.FIELD, 0xFFFF9F96.toInt()) { confirmReset() }
        wipe.textSize = 14f
        val s1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f)
        s1.setMargins(0, 0, dp(6), 0)
        repButtons.addView(send, s1)
        repButtons.addView(wipe, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        rep.addView(repButtons)
        rep.addView(Ui.gap(this, 8))
        rep.addView(
            Ui.text(
                this,
                "Hər raundun kartları, tövsiyələri və nəticəsi saxlanır. Bir neçə raunddan sonra hesabatı Claude-a göndər, strategiyanı yoxlasın.",
                11.5f, Ui.MUTED
            )
        )
        col.addView(rep)

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
                    "•  Pəncərənin sağındakı 23W 17L qazanc və uduzma sayıdır.\n" +
                    "•  Əl bölünəndə hər əl üçün tövsiyə ardıcıl göstərilir.\n" +
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

    private fun tile(parent: LinearLayout, label: String, color: Int): TextView {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setPadding(0, dp(10), 0, dp(10))
        box.background = Ui.rounded(this, Ui.FIELD, 12, Ui.CARD_LINE)
        val v = Ui.text(this, "0", 24f, color, true)
        v.gravity = Gravity.CENTER
        box.addView(v)
        val l = Ui.text(this, label, 11f, Ui.MUTED)
        l.gravity = Gravity.CENTER
        box.addView(l)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(dp(3), 0, dp(3), 0)
        parent.addView(box, lp)
        return v
    }

    private fun kindWord(k: String): String = when (k) {
        "WIN", "DEALER_BUST" -> "Qazandı"
        "BLACKJACK" -> "Blackjack"
        "LOSE", "BUST" -> "Uduzdu"
        "PUSH" -> "Bərabər"
        else -> "?"
    }

    private fun cards(l: List<Int>): String = l.joinToString(" ") { if (it == 11) "A" else it.toString() }

    private fun refreshReport() {
        val (st, _) = RoundStore.load(this)
        tileWin.text = st.win.toString()
        tileLose.text = st.lose.toString()
        tilePush.text = st.push.toString()
        tileBj.text = st.blackjack.toString()
        summary.text = if (st.hands == 0) "Hələ nəticə yoxdur" else
            "Cəmi " + st.hands + " əl  ·  qazanma " + String.format(java.util.Locale.US, "%.1f", st.winRate()) + "%"
        val list = RoundStore.recent(this, 8)
        if (list.isEmpty()) {
            recent.text = "Son raundlar burada görünəcək."
        } else {
            val sb = StringBuilder()
            for (r in list) {
                val time = if (r.time.length >= 16) r.time.substring(11, 16) else r.time
                sb.append(time).append("  D: ").append(cards(r.dealer)).append("  |  M: ")
                    .append(r.hands.joinToString(" / ") { cards(it) })
                    .append("  →  ")
                    .append(if (r.kinds.isEmpty()) "bitməyib" else r.kinds.joinToString(" · ") { kindWord(it) })
                    .append('\n')
            }
            recent.text = sb.toString().trimEnd()
        }
    }

    private fun sendReport() {
        val (st, _) = RoundStore.load(this)
        val uri = RoundStore.export(this)
        if (uri == null) {
            Toast.makeText(this, "Hesabatı saxlamaq alınmadı", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Yükləmələr (Downloads) qovluğunda saxlandı", Toast.LENGTH_LONG).show()
        if (Build.VERSION.SDK_INT >= 29) {
            val send = Intent(Intent.ACTION_SEND)
            send.type = "text/plain"
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "Hesabatı göndər"))
        }
        if (st.rounds == 0) Toast.makeText(this, "Hələ raund yoxdur, hesabat boşdur", Toast.LENGTH_SHORT).show()
    }

    private fun confirmReset() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Sıfırlansın?")
            .setMessage("Bütün say və raund qeydləri silinəcək. Əvvəl hesabatı göndərmisənsə davam et.")
            .setPositiveButton("Sıfırla") { _, _ ->
                RoundStore.clear(this)
                CaptureService.resetStats = true
                refreshReport()
            }
            .setNegativeButton("Ləğv", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        ui.postDelayed({ refresh() }, 700)
    }

    private fun refresh() {
        refreshReport()
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
