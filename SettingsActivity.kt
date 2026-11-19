package com.ubuntu.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.math.min

/** আইকন আকার, ওয়ালপেপার ও পারমিশনের অবস্থা দেখানোর সেটিংস স্ক্রিন। */
class SettingsActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private val gray = 0xFFAEA79F.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(12), dp(16), dp(24))
        scroll.addView(content)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun tv(s: String, size: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun lp(bottom: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.bottomMargin = dp(bottom)
        return p
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setBackgroundResource(R.drawable.bg_card)
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        return c
    }

    private fun build() {
        content.removeAllViews()

        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val close = tv("✕", 22f, Color.WHITE, true)
        close.setPadding(0, 0, dp(16), 0)
        close.setOnClickListener { finish() }
        head.addView(close)
        head.addView(tv("Launcher settings", 20f, Color.WHITE, true))
        content.addView(head, lp(16))

        shapeCard()
        wallpaperCard()
        permissionCards()
    }

    private fun shapeCard() {
        val card = card()
        card.addView(tv("Icon shape", 16f, Color.WHITE, true))
        card.addView(tv("Choose the shape of app icons", 12f, gray, false))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(12), 0, 0)
        val cur = Prefs.shape(this)
        IconShapes.NAMES.forEachIndexed { i, name ->
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.setPadding(dp(4), dp(6), dp(4), dp(6))
            if (i == cur) col.setBackgroundResource(R.drawable.bg_selected)
            col.setOnClickListener {
                Prefs.setShape(this, i)
                build()
            }
            col.addView(ShapeView(this, i), LinearLayout.LayoutParams(dp(48), dp(48)))
            val label = tv(name, 10f, Color.WHITE, false)
            label.gravity = Gravity.CENTER
            col.addView(label)
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        card.addView(row)
        content.addView(card, lp(12))
    }

    private fun wallpaperCard() {
        val card = card()
        card.addView(tv("Wallpaper", 16f, Color.WHITE, true))
        card.addView(tv("Also applied to the system wallpaper", 12f, gray, false))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val cur = Prefs.wallpaper(this)
        listOf("Dragon", "Ubuntu purple").forEachIndexed { i, n ->
            val b = tv(n, 14f, Color.WHITE, true)
            b.gravity = Gravity.CENTER
            b.setPadding(dp(8), dp(12), dp(8), dp(12))
            b.setBackgroundResource(if (i == cur) R.drawable.bg_btn_orange else R.drawable.bg_quick)
            b.setOnClickListener {
                Prefs.setWallpaper(this, i)
                Prefs.applySystemWallpaper(this)
                build()
            }
            val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            p.setMargins(dp(4), dp(12), dp(4), 0)
            row.addView(b, p)
        }
        card.addView(row)
        content.addView(card, lp(12))
    }

    private fun isDefaultHome(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == packageName
    }

    private fun permissionCards() {
        val home = isDefaultHome()
        val acc = UbuntuShellService.instance != null
        val notif = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val write = Settings.System.canWrite(this)

        statusCard(
            "Default launcher", "Set Ubuntu Launcher as the home app", home, "SET AS DEFAULT"
        ) { Intent(Settings.ACTION_HOME_SETTINGS) }

        statusCard(
            "Restricted settings",
            "Apps installed from an APK need this. Open app settings, tap ⋮ and choose “Allow restricted settings”.",
            false, "OPEN APP SETTINGS"
        ) { Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")) }

        statusCard(
            "Accessibility service", "Needed for the Ubuntu top bar inside all apps", acc, "OPEN ACCESSIBILITY"
        ) { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }

        statusCard(
            "Notification access", "Needed to show notifications in the Control Panel", notif, "OPEN NOTIFICATION ACCESS"
        ) { Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }

        statusCard(
            "Modify system settings", "Needed for the brightness slider", write, "ALLOW"
        ) { Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")) }

        if (home && acc && notif && write) {
            val t = tv("All permissions granted!", 13f, 0xFF8BC34A.toInt(), false)
            t.gravity = Gravity.CENTER
            content.addView(t, lp(0))
        }
    }

    private fun statusCard(title: String, sub: String, ok: Boolean, btn: String, intent: () -> Intent) {
        val card = card()
        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        val mark = tv(if (ok) "✔" else "!", 20f, if (ok) 0xFF8BC34A.toInt() else 0xFFFFA000.toInt(), true)
        mark.setPadding(0, 0, dp(12), 0)
        top.addView(mark)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(tv(title, 15f, Color.WHITE, true))
        col.addView(tv(sub, 12f, gray, false))
        top.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(top)
        if (!ok) {
            val b = tv(btn, 13f, Color.WHITE, true)
            b.gravity = Gravity.CENTER
            b.setBackgroundResource(R.drawable.bg_btn_orange)
            b.setPadding(dp(8), dp(12), dp(8), dp(12))
            b.setOnClickListener {
                try { startActivity(intent()) } catch (_: Exception) {}
            }
            val p = lp(0)
            p.topMargin = dp(12)
            card.addView(b, p)
        }
        content.addView(card, lp(12))
    }
}

/** সেটিংসে আইকন আকারের প্রিভিউ। */
class ShapeView(ctx: Context, private val shape: Int) : View(ctx) {
    private val logo = ContextCompat.getDrawable(
        ctx, if (shape == IconShapes.ORIGINAL) R.drawable.ic_launcher else R.drawable.ic_ubuntu_btn
    )

    override fun onDraw(c: Canvas) {
        val s = min(width, height)
        c.save()
        if (shape != IconShapes.ORIGINAL) c.clipPath(IconShapes.path(shape, s.toFloat()))
        logo?.setBounds(0, 0, s, s)
        logo?.draw(c)
        c.restore()
    }
}
