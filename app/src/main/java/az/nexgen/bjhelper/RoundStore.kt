package az.nexgen.bjhelper

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Round records on disk (one JSON line per round) and the report built from them. */
object RoundStore {

    private const val TAG = "BJHelper"

    class Recent(val time: String, val dealer: List<Int>, val hands: List<List<Int>>, val kinds: List<String>)

    fun fmt(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    private fun file(ctx: Context) = File(ctx.filesDir, "rounds.jsonl")

    @Synchronized
    fun append(ctx: Context, line: String) {
        try {
            file(ctx).appendText(line + "\n")
        } catch (t: Throwable) {
            Log.e(TAG, "append failed", t)
        }
    }

    @Synchronized
    fun clear(ctx: Context) {
        try {
            file(ctx).delete()
        } catch (t: Throwable) {
            Log.e(TAG, "clear failed", t)
        }
    }

    @Synchronized
    private fun lines(ctx: Context): List<String> {
        val f = file(ctx)
        return if (f.exists()) f.readLines().filter { it.isNotBlank() } else emptyList()
    }

    /** Totals over every saved round, and the id the next round should get. */
    fun load(ctx: Context): Pair<RoundLog.Stats, Int> {
        val st = RoundLog.Stats()
        var maxId = 0
        for (l in lines(ctx)) {
            try {
                val o = JSONObject(l)
                maxId = maxOf(maxId, o.optInt("id", 0))
                val res = o.optJSONArray("results") ?: continue
                st.rounds++
                for (i in 0 until res.length()) {
                    when (res.getString(i)) {
                        "WIN", "DEALER_BUST" -> st.win++
                        "BLACKJACK" -> {
                            st.win++
                            st.blackjack++
                        }
                        "LOSE", "BUST" -> st.lose++
                        "PUSH" -> st.push++
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "bad line", t)
            }
        }
        return Pair(st, maxId + 1)
    }

    /** The newest rounds first. */
    fun recent(ctx: Context, n: Int): List<Recent> {
        val out = ArrayList<Recent>()
        for (l in lines(ctx).asReversed()) {
            if (out.size >= n) break
            try {
                val o = JSONObject(l)
                val fin = o.getJSONObject("final")
                val dealer = ints(fin.getJSONArray("dealer").toString())
                val hands = ArrayList<List<Int>>()
                val ha = fin.getJSONArray("hands")
                for (i in 0 until ha.length()) hands.add(ints(ha.getJSONArray(i).toString()))
                val kinds = ArrayList<String>()
                val ra = o.optJSONArray("results")
                if (ra != null) for (i in 0 until ra.length()) kinds.add(ra.getString(i))
                out.add(Recent(o.optString("time", ""), dealer, hands, kinds))
            } catch (t: Throwable) {
                Log.w(TAG, "bad line", t)
            }
        }
        return out
    }

    private fun ints(json: String): List<Int> =
        json.trim('[', ']').split(',').filter { it.isNotBlank() }.map { it.trim().toInt() }

    fun report(ctx: Context): String {
        val (st, _) = load(ctx)
        val ver = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (t: Throwable) {
            "?"
        }
        val prefs = ctx.getSharedPreferences("bj", Context.MODE_PRIVATE)
        val dm = ctx.resources.displayMetrics
        val sb = StringBuilder()
        sb.append("BLACKJACK KOMEKCISI HESABATI\n")
        sb.append("versiya: ").append(ver).append('\n')
        sb.append("hazirlandi: ").append(fmt(System.currentTimeMillis())).append('\n')
        sb.append("cihaz: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(", Android ").append(Build.VERSION.RELEASE).append(", ekran ")
            .append(dm.widthPixels).append('x').append(dm.heightPixels).append('\n')
        sb.append("deste: ").append(prefs.getInt("decks", 6)).append(", say rejimi: ")
            .append(prefs.getBoolean("count", true)).append('\n')
        sb.append("raund: ").append(st.rounds).append(", el: ").append(st.hands)
            .append(", qazanc: ").append(st.win).append(", uduzma: ").append(st.lose)
            .append(", beraber: ").append(st.push).append(", blackjack: ").append(st.blackjack)
            .append(", qazanma faizi: ").append(String.format(Locale.US, "%.1f", st.winRate())).append("%\n")
        sb.append("\nQEYD: kartlar 2-9, 10 (10/J/Q/K), 11 (As). events: t=ms, d=diler, h=oyuncunun elleri,\n")
        sb.append("adv=tovsiye (H kart al, S dayan, D ikiqat, P bol), pos=kartlarin ekrandaki x yeri.\n")
        sb.append("missed=oxunmayan isaretin maskasi.\n\n")
        for (l in lines(ctx)) sb.append(l).append('\n')
        return sb.toString()
    }

    /** Saves the report into the Downloads folder; returns where, or null. */
    fun export(ctx: Context): Uri? {
        val name = "blackjack-hesabat-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".txt"
        val text = report(ctx)
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues()
                values.put(MediaStore.Downloads.DISPLAY_NAME, name)
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                uri
            } else {
                val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                val f = File(dir, name)
                f.writeText(text)
                Uri.fromFile(f)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "export failed", t)
            null
        }
    }
}
