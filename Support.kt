package com.ubuntu.launcher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.NetworkCapabilities
import android.os.BatteryManager

object Prefs {
    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("dock", Context.MODE_PRIVATE)

    fun shape(c: Context): Int = sp(c).getInt("shape", IconShapes.SQUIRCLE)
    fun setShape(c: Context, v: Int) { sp(c).edit().putInt("shape", v).apply() }

    fun wallpaper(c: Context): Int = sp(c).getInt("wallpaper", 0)
    fun setWallpaper(c: Context, v: Int) { sp(c).edit().putInt("wallpaper", v).apply() }

    fun wallpaperRes(i: Int): Int = if (i == 1) R.drawable.bg_purple else R.drawable.bg_dragon

    /** বেছে নেওয়া ওয়ালপেপার সিস্টেম ওয়ালপেপার (লক স্ক্রিনসহ) হিসেবেও বসায়। */
    fun applySystemWallpaper(c: Context) {
        val app = c.applicationContext
        val res = wallpaperRes(wallpaper(c))
        Thread {
            try {
                val bmp = BitmapFactory.decodeResource(app.resources, res)
                WallpaperManager.getInstance(app).setBitmap(bmp)
            } catch (_: Exception) {}
        }.start()
    }
}

object StatusInfo {
    fun battery(i: Intent): String? {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val chg = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        if (level < 0 || scale <= 0) return null
        return (if (chg) "⚡ " else "🔋 ") + (level * 100 / scale) + "%"
    }

    fun net(c: NetworkCapabilities): String = when {
        c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "📶 Wi-Fi"
        c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "📱 Mobile"
        else -> "🌐 Online"
    }
}
