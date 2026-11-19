package com.ubuntu.launcher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.core.content.ContextCompat

/** সব অ্যাপের উপরে Ubuntu-স্টাইল টপ বার বসায় (সিস্টেম স্ট্যাটাস বারের জায়গায়)। */
class UbuntuShellService : AccessibilityService() {

    private var bar: View? = null
    private var netText: TextView? = null
    private var batText: TextView? = null
    private lateinit var wm: WindowManager

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            StatusInfo.battery(i)?.let { batText?.text = it }
        }
    }

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
            val t = StatusInfo.net(c)
            bar?.post { netText?.text = t }
        }
        override fun onLost(n: Network) { bar?.post { netText?.text = "✖ Offline" } }
    }

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildBar()
        ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )?.let { batteryReceiver.onReceive(this, it) }
        try {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(netCallback)
        } catch (_: Exception) {}
    }

    private fun buildBar() {
        val d = resources.displayMetrics.density
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val h = if (id > 0) resources.getDimensionPixelSize(id) else (28 * d).toInt()

        fun tv(pl: Int, pr: Int) = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding((pl * d).toInt(), 0, (pr * d).toInt(), 0)
        }

        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * d).toInt(), 0, (8 * d).toInt(), 0)
        }
        netText = tv(6, 6)
        batText = tv(6, 16)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(clock, LinearLayout.LayoutParams(-2, -1))
            addView(View(context), LinearLayout.LayoutParams(0, -1, 1f))
            addView(netText, LinearLayout.LayoutParams(-2, -1))
            addView(batText, LinearLayout.LayoutParams(-2, -1))
            setOnClickListener { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) }
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }

        bar = root
        wm.addView(root, lp)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == "com.android.systemui") return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.enabledInputMethodList.any { it.packageName == pkg }) return
        // লঞ্চারের হোম স্ক্রিনে নিজস্ব টপ বার আছে, তাই শুধু সেখানেই ওভারলে লুকাই
        val onHome = pkg == packageName && e.className?.toString() == MainActivity::class.java.name
        bar?.visibility = if (onHome) View.GONE else View.VISIBLE
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try { bar?.let { wm.removeView(it) } } catch (_: Exception) {}
        bar = null
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        try { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(netCallback) } catch (_: Exception) {}
    }

    companion object {
        var instance: UbuntuShellService? = null
    }
}
