package az.nexgen.bjhelper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Presses the screen where the auto player says. It reads nothing from other apps: it only exists
 * because Android lets an accessibility service perform taps.
 */
class TapService : AccessibilityService() {

    companion object {
        @Volatile var instance: TapService? = null
            private set

        /** True when the person has switched the service on in the phone's settings. */
        fun ready(): Boolean = instance != null

        fun tap(x: Int, y: Int): Boolean = instance?.tapAt(x, y) ?: false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun tapAt(x: Int, y: Int): Boolean {
        return try {
            val p = Path()
            p.moveTo(x.toFloat(), y.toFloat())
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(p, 0L, 70L))
                .build()
            dispatchGesture(g, null, null)
        } catch (t: Throwable) {
            Log.w("BJHelper", "tap failed", t)
            false
        }
    }
}
