package az.nexgen.bjhelper

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.provider.MediaStore
import android.util.Log

/** Saves the last captured frame with every recognised text box drawn on it, for tuning. */
object DebugDump {

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
            c.drawLine(0f, h * CardReader.DEALER_LIMIT, w, h * CardReader.DEALER_LIMIT, guide)
            c.drawLine(0f, h * CardReader.CROP_BOTTOM, w, h * CardReader.CROP_BOTTOM, guide)

            val stroke = Paint()
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = 4f
            val txt = Paint()
            txt.textSize = 30f
            txt.isAntiAlias = true

            if (reading != null) {
                for (cd in reading.cands) {
                    val col = when {
                        cd.accepted -> Color.GREEN
                        cd.rank != null -> Color.RED
                        else -> Color.LTGRAY
                    }
                    stroke.color = col
                    c.drawRect(cd.box, stroke)
                    txt.color = col
                    val label = cd.text + " c" + (cd.cream * 100).toInt() + " " + cd.reason
                    c.drawText(label, cd.box.left.toFloat(), cd.box.top - 6f, txt)
                }
            }
            txt.color = Color.WHITE
            c.drawText(note, 20f, h * CardReader.CROP_TOP - 12f, txt)

            val values = ContentValues()
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "bj-debug-" + System.currentTimeMillis() + ".png")
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BlackjackHelper")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                bmp.recycle()
                null
            } else {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bmp.recycle()
                uri.toString()
            }
        } catch (t: Throwable) {
            Log.e("BJHelper", "debug dump failed", t)
            null
        }
    }
}
