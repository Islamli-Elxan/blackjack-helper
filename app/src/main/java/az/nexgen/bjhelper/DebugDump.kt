package az.nexgen.bjhelper

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.provider.MediaStore
import android.util.Log

/** Saves the last captured frame with the detected card areas and read ranks drawn on it, for tuning. */
object DebugDump {

    /** The frame exactly as captured, with nothing drawn on it. */
    fun saveRaw(ctx: Context, src: Bitmap): String? = store(ctx, src, "bj-clean-")

    fun save(ctx: Context, src: Bitmap, reading: CardReader.Reading?, note: String): String? {
        return try {
            val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
            val c = Canvas(bmp)
            val w = bmp.width.toFloat()
            val h = bmp.height.toFloat()

            val guide = Paint()
            guide.color = Color.YELLOW
            guide.strokeWidth = 3f
            c.drawLine(0f, h * CardReader.CROP_TOP, w, h * CardReader.CROP_TOP, guide)
            c.drawLine(0f, h * CardFinder.DEALER_LIMIT, w, h * CardFinder.DEALER_LIMIT, guide)
            c.drawLine(0f, h * CardReader.CROP_BOTTOM, w, h * CardReader.CROP_BOTTOM, guide)

            val stroke = Paint()
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 4f
            val txt = Paint()
            txt.textSize = 34f
            txt.isAntiAlias = true

            if (reading != null) {
                stroke.color = Color.CYAN
                for (b in reading.blobs) {
                    c.drawRect(Rect(b.l, b.t, b.r, b.b), stroke)
                }
                for (it in reading.items) {
                    val col = if (it.rank != null) Color.GREEN else Color.RED
                    stroke.color = col
                    c.drawRect(Rect(it.box.l, it.box.t, it.box.r, it.box.b), stroke)
                    txt.color = col
                    c.drawText(it.text + " " + Math.round(it.conf * 100) + "%", it.box.l.toFloat(), it.box.t - 8f, txt)
                }
            }
            txt.color = Color.WHITE
            c.drawText(note, 20f, h * CardReader.CROP_TOP - 12f, txt)

            val r = store(ctx, bmp, "bj-debug-")
            bmp.recycle()
            r
        } catch (t: Throwable) {
            Log.e("BJHelper", "debug dump failed", t)
            null
        }
    }

    private fun store(ctx: Context, bmp: Bitmap, prefix: String): String? {
        return try {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DISPLAY_NAME, prefix + System.currentTimeMillis() + ".png")
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BlackjackHelper")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                null
            } else {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                uri.toString()
            }
        } catch (t: Throwable) {
            Log.e("BJHelper", "store failed", t)
            null
        }
    }
}
