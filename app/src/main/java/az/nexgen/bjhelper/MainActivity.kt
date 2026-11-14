package az.nexgen.bjhelper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val reqCapture = 1001
    private val reqNotif = 1002
    private lateinit var overlayStatus: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun label(t: String, size: Float, bold: Boolean = false): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = size
        if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setPadding(0, 0, 0, dp(10))
        return tv
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("bj", Context.MODE_PRIVATE)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(32), dp(20), dp(32))

        col.addView(label("Blackjack Köməkçisi", 24f, true))
        col.addView(
            label(
                "Oyunun ekranını oxuyur, kartları tanıyır və oyunun üstündə üzən pəncərədə " +
                    "Hit, Stand, Double, Split məsləhəti göstərir. Hi-Lo sayını da özü aparır.",
                15f
            )
        )

        col.addView(label("Deste sayı", 14f, true))
        val decks = listOf(1, 2, 4, 6, 8)
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, decks.map { it.toString() }
        )
        spinner.setSelection(decks.indexOf(prefs.getInt("decks", 6)).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                prefs.edit().putInt("decks", decks[pos]).apply()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        col.addView(spinner)

        val countBox = CheckBox(this)
        countBox.text = "Say rejimi (say əsasında dəyişikliklər və sığorta)"
        countBox.isChecked = prefs.getBoolean("count", true)
        countBox.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("count", checked).apply()
        }
        col.addView(countBox)
        col.addView(
            label(
                "Oyun hər əldən sonra kartları təzədən qarışdırırsa say faydasızdır, onu söndür. " +
                    "Strategiya məsləhəti yenə işləyəcək.",
                12f
            )
        )

        overlayStatus = label("", 14f, true)
        col.addView(overlayStatus)

        val permBtn = Button(this)
        permBtn.text = "1. Üzən pəncərə icazəsi"
        permBtn.setOnClickListener { openOverlaySettings() }
        col.addView(permBtn)

        val startBtn = Button(this)
        startBtn.text = "2. Başla"
        startBtn.setOnClickListener { start() }
        col.addView(startBtn)

        val stopBtn = Button(this)
        stopBtn.text = "Dayandır"
        stopBtn.setOnClickListener {
            stopService(Intent(this, CaptureService::class.java))
            Toast.makeText(this, "Dayandırıldı", Toast.LENGTH_SHORT).show()
        }
        col.addView(stopBtn)

        val help = label(
            "\nİstifadə: Başla düyməsinə bas, ekran paylaşma sorğusunu təsdiqlə, sonra oyunu aç. " +
                "Android 14 və yuxarıda \"Bir tətbiq\" seçib oyunu seçsən üzən pəncərə görüntüyə düşməz. " +
                "\"Bütün ekran\" da işləyir.\n\n" +
                "Üzən pəncərə əvvəlcə ekranın yuxarısında balaca olur; toxunsan açılır və kartlardan özü kənara çəkilir. " +
                "Kartların üstünə düşərsə, proqram onu 0,2 saniyəlik şəffaflaşdırıb altını oxuyur. Sürüşdürsən yerini özün seçirsən. " +
                "Say ↺ sayı sıfırlayır, Yeni əl cari əli unudur.\n\n" +
                "Səhv oxuyursa Debug düyməsinə bas. Şəkil Pictures/BlackjackHelper qovluğuna düşür, " +
                "onu göndər ki, tanımanı düzəldim.",
            13f
        )
        col.addView(help)

        val scroll = ScrollView(this)
        scroll.addView(col)
        setContentView(scroll)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), reqNotif)
        }
    }

    override fun onResume() {
        super.onResume()
        overlayStatus.text = if (Settings.canDrawOverlays(this)) {
            "Üzən pəncərə icazəsi verilib"
        } else {
            "Üzən pəncərə icazəsi lazımdır"
        }
    }

    private fun openOverlaySettings() {
        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(i)
    }

    @Suppress("DEPRECATION")
    private fun start() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Əvvəl üzən pəncərə icazəsini ver", Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), reqCapture)
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
