package com.ubuntu.launcher

import android.app.Notification
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.RelativeSizeSpan
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

data class AppInfo(val label: String, val pkg: String, val icon: Drawable, val intent: Intent)

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var apps: List<AppInfo> = emptyList()
    private var shownShape = -1
    private var shownWallpaper = -1

    private lateinit var wallpaperView: ImageView
    private lateinit var dockList: LinearLayout
    private lateinit var drawer: View
    private lateinit var shade: View
    private lateinit var search: EditText
    private lateinit var batteryText: TextView
    private lateinit var netText: TextView
    private lateinit var notifCount: TextView
    private lateinit var notifEmpty: TextView
    private lateinit var notifAccess: TextView
    private lateinit var barAccess: TextView
    private lateinit var brightBar: SeekBar
    private lateinit var tWifi: TextView
    private lateinit var tBt: TextView
    private lateinit var tAir: TextView
    private lateinit var tTorch: TextView
    private lateinit var tCalc: TextView
    private lateinit var tLoc: TextView
    private lateinit var gridAdapter: AppAdapter
    private lateinit var notifAdapter: NotifAdapter
    private lateinit var gestures: GestureDetector

    private var torchId: String? = null
    private var torchOn = false
    private val cam by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }

    private val torchCb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                torchOn = enabled
                refreshTiles()
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            StatusInfo.battery(i)?.let { batteryText.text = it }
        }
    }
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = loadApps()
    }
    private val netCb = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
            val t = StatusInfo.net(c)
            runOnUiThread { netText.text = t }
        }
        override fun onLost(n: Network) {
            runOnUiThread { netText.text = "✖ Offline" }
        }
    }

    private fun dp(v: Int): Float = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs.sp(this)

        // সিস্টেম স্ট্যাটাস বার লুকিয়ে নিজস্ব টপ বার দেখাই
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }

        wallpaperView = findViewById(R.id.wallpaper)
        dockList = findViewById(R.id.dockList)
        drawer = findViewById(R.id.drawer)
        shade = findViewById(R.id.shade)
        search = findViewById(R.id.search)
        batteryText = findViewById(R.id.battery)
        netText = findViewById(R.id.netInfo)
        notifCount = findViewById(R.id.notifCount)
        notifEmpty = findViewById(R.id.notifEmpty)
        notifAccess = findViewById(R.id.notifAccess)
        barAccess = findViewById(R.id.barAccess)
        brightBar = findViewById(R.id.brightBar)
        tWifi = findViewById(R.id.tWifi)
        tBt = findViewById(R.id.tBt)
        tAir = findViewById(R.id.tAir)
        tTorch = findViewById(R.id.tTorch)
        tCalc = findViewById(R.id.tCalc)
        tLoc = findViewById(R.id.tLoc)

        applyWallpaper()
        if (!prefs.getBoolean("wp_v3", false)) {
            prefs.edit().putBoolean("wp_v3", true).apply()
            Prefs.applySystemWallpaper(this)
        }

        // অ্যাপ গ্রিড
        val dm = resources.displayMetrics
        val span = maxOf(4, ((dm.widthPixels / dm.density - 68) / 84).toInt())
        gridAdapter = AppAdapter()
        findViewById<RecyclerView>(R.id.appGrid).apply {
            layoutManager = GridLayoutManager(this@MainActivity, span)
            adapter = gridAdapter
            setHasFixedSize(true)
        }

        // নোটিফিকেশন তালিকা (সোয়াইপ করে সরানো যায়)
        notifAdapter = NotifAdapter()
        val notifList = findViewById<RecyclerView>(R.id.notifList)
        notifList.layoutManager = LinearLayoutManager(this)
        notifList.adapter = notifAdapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder
            ) = false

            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val pos = vh.bindingAdapterPosition
                val ok = pos in notifAdapter.data.indices && notifAdapter.data[pos].isClearable
                return if (ok) super.getSwipeDirs(rv, vh) else 0
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.bindingAdapterPosition
                if (pos !in notifAdapter.data.indices) return
                NotificationStore.cancel(notifAdapter.data[pos].key)
                notifAdapter.data = notifAdapter.data.toMutableList().also { it.removeAt(pos) }
                notifAdapter.notifyItemRemoved(pos)
            }
        }).attachToRecyclerView(notifList)

        // ক্লিক
        findViewById<View>(R.id.showApps).setOnClickListener { toggleDrawer() }
        findViewById<View>(R.id.topBar).setOnClickListener { toggleShade() }
        findViewById<View>(R.id.handle).setOnClickListener { hideShade() }
        findViewById<View>(R.id.clearAll).setOnClickListener { NotificationStore.cancelAll() }
        findViewById<View>(R.id.openSettings).setOnClickListener {
            hideShade()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.root).setOnLongClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        notifAccess.setOnClickListener {
            openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        barAccess.setOnClickListener { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        // কন্ট্রোল প্যানেলের টাইল
        tWifi.setOnClickListener {
            openSettings(
                if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                else Intent(Settings.ACTION_WIFI_SETTINGS)
            )
        }
        tBt.setOnClickListener { openSettings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        tAir.setOnClickListener { openSettings(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
        tTorch.setOnClickListener { toggleTorch() }
        tCalc.setOnClickListener { openCalculator() }
        tLoc.setOnClickListener { openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        setupTorch()

        brightBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser || !Settings.System.canWrite(this@MainActivity)) return
                Settings.System.putInt(
                    contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, maxOf(1, p))
            }

            override fun onStartTrackingTouch(sb: SeekBar) {
                if (!Settings.System.canWrite(this@MainActivity)) {
                    Toast.makeText(
                        this@MainActivity, "Allow “Modify system settings” to change brightness",
                        Toast.LENGTH_LONG
                    ).show()
                    openSettings(
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                    )
                }
            }

            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // সার্চ
        search.doAfterTextChanged { applyFilter(it?.toString().orEmpty()) }
        search.setOnEditorActionListener { _, _, _ ->
            gridAdapter.items.firstOrNull()?.let { launch(it) }
            true
        }

        // সোয়াইপ: উপর থেকে নিচে = প্যানেল, নিচ থেকে উপরে = অ্যাপ ড্রয়ার
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val start = e1 ?: return false
                val dy = e2.y - start.y
                if (abs(vy) < abs(vx) || abs(dy) < dp(60)) return false
                val h = resources.displayMetrics.heightPixels
                val shadeOpen = shade.visibility == View.VISIBLE
                val drawerOpen = drawer.visibility == View.VISIBLE
                if (dy > 0 && start.y < dp(160) && !shadeOpen && !drawerOpen) { showShade(); return true }
                if (dy < 0 && shadeOpen && start.y > h - dp(140)) { hideShade(); return true }
                if (dy < 0 && !shadeOpen && !drawerOpen && start.y > h - dp(160)) { showDrawer(); return true }
                return false
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeOverlays()
        })

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, pkgReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)

        loadApps()
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        when (i?.action) {
            ACTION_DRAWER -> showDrawer()
            ACTION_SHADE -> showShade()
            else -> closeOverlays()
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )?.let { batteryReceiver.onReceive(this, it) }
        try {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(netCb)
        } catch (_: Exception) {}
        try { cam.registerTorchCallback(torchCb, null) } catch (_: Exception) {}
        if (hasNotifAccess()) {
            try {
                NotificationListenerService.requestRebind(ComponentName(this, NotificationStore::class.java))
            } catch (_: Exception) {}
        }
        NotificationStore.listener = { refreshNotifs() }
        refreshNotifs()
    }

    override fun onResume() {
        super.onResume()
        applyWallpaper()
        if (Prefs.shape(this) != shownShape) loadApps()
        refreshTiles()
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        try { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(netCb) } catch (_: Exception) {}
        try { cam.unregisterTorchCallback(torchCb) } catch (_: Exception) {}
        NotificationStore.listener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ---------- ওয়ালপেপার ----------

    private fun applyWallpaper() {
        val w = Prefs.wallpaper(this)
        if (w == shownWallpaper) return
        shownWallpaper = w
        wallpaperView.setImageResource(Prefs.wallpaperRes(w))
    }

    // ---------- অ্যাপ লোড ----------

    private fun loadApps() {
        val shape = Prefs.shape(this)
        shownShape = shape
        val size = (56 * resources.displayMetrics.density).toInt()
        Thread {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(q, 0).map { ri ->
                val p = ri.activityInfo.packageName
                AppInfo(
                    ri.loadLabel(pm).toString(), p,
                    IconShapes.apply(this, ri.loadIcon(pm), shape, size),
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(p, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                )
            }.filter { it.pkg != packageName }.sortedBy { it.label.lowercase() }
            runOnUiThread {
                apps = list
                applyFilter(search.text.toString())
                renderDock()
            }
        }.start()
    }

    private fun applyFilter(q: String) {
        val t = q.trim()
        gridAdapter.submit(if (t.isEmpty()) apps else apps.filter { it.label.contains(t, ignoreCase = true) })
    }

    // ---------- ডক ----------

    private fun resolvePkg(i: Intent): String? =
        packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }

    private fun favorites(): MutableList<String> {
        val saved = prefs.getString("favs", null)
        if (saved != null) return saved.split(",").filter { it.isNotEmpty() }.toMutableList()
        return listOfNotNull(
            resolvePkg(Intent(Intent.ACTION_DIAL)),
            resolvePkg(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            resolvePkg(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
            resolvePkg(Intent(Settings.ACTION_SETTINGS))
        ).distinct().toMutableList()
    }

    private fun saveFavorites(l: List<String>) = prefs.edit().putString("favs", l.joinToString(",")).apply()

    private fun renderDock() {
        dockList.removeAllViews()
        val badge = NotificationStore.items.map { it.packageName }.toSet()
        favorites().mapNotNull { p -> apps.find { it.pkg == p } }.forEach { app ->
            val v = layoutInflater.inflate(R.layout.item_dock, dockList, false)
            v.findViewById<ImageView>(R.id.icon).setImageDrawable(app.icon)
            v.findViewById<View>(R.id.dot).visibility = if (app.pkg in badge) View.VISIBLE else View.INVISIBLE
            v.setOnClickListener { launch(app) }
            v.setOnLongClickListener { showMenu(v, app); true }
            dockList.addView(v)
        }
    }

    private fun showMenu(anchor: View, app: AppInfo) {
        val favs = favorites()
        val inDock = app.pkg in favs
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, if (inDock) "Remove from dock" else "Add to dock")
            menu.add(0, 2, 1, "App info")
            menu.add(0, 3, 2, "Uninstall")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> {
                        if (inDock) favs.remove(app.pkg) else favs.add(app.pkg)
                        saveFavorites(favs); renderDock()
                    }
                    2 -> openSettings(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                    )
                    3 -> openSettings(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
                }
                true
            }
            show()
        }
    }

    // ---------- কন্ট্রোল প্যানেলের টাইল ----------

    private fun setTile(v: TextView, emoji: String, label: String, on: Boolean) {
        val sb = SpannableStringBuilder(emoji)
        sb.setSpan(RelativeSizeSpan(1.9f), 0, emoji.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("\n").append(label)
        v.text = sb
        v.setBackgroundResource(if (on) R.drawable.bg_tile_on else R.drawable.bg_tile_off)
    }

    private fun refreshTiles() {
        val wifi = try {
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
        } catch (_: Exception) { false }
        val bt = try {
            (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true
        } catch (_: Exception) { false }
        val air = try {
            Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (_: Exception) { false }
        val loc = try {
            if (Build.VERSION.SDK_INT >= 28)
                (getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
            else Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
        } catch (_: Exception) { false }
        setTile(tWifi, "📶", "Wi-Fi", wifi)
        setTile(tBt, "🔷", "Bluetooth", bt)
        setTile(tAir, "✈️", "Airplane", air)
        setTile(tTorch, "🔦", "Flashlight", torchOn)
        setTile(tCalc, "🧮", "Calculator", false)
        setTile(tLoc, "📍", "Location", loc)
    }

    private fun setupTorch() {
        try {
            torchId = cam.cameraIdList.firstOrNull {
                cam.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {}
    }

    private fun toggleTorch() {
        val id = torchId
        if (id == null) {
            Toast.makeText(this, "No flashlight found", Toast.LENGTH_SHORT).show()
            return
        }
        try { cam.setTorchMode(id, !torchOn) } catch (_: Exception) {}
    }

    private fun openCalculator() {
        try {
            val i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            closeOverlays()
        } catch (_: Exception) {
            Toast.makeText(this, "No calculator app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncBrightness() {
        try {
            brightBar.progress = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        } catch (_: Exception) {}
    }

    // ---------- ওভারলে ----------

    private fun launch(app: AppInfo) {
        try { startActivity(app.intent) } catch (e: Exception) {
            Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
        closeOverlays()
    }

    private fun openSettings(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }

    private fun toggleDrawer() = if (drawer.visibility == View.VISIBLE) hideDrawer() else showDrawer()
    private fun toggleShade() = if (shade.visibility == View.VISIBLE) hideShade() else showShade()

    private fun showDrawer() {
        if (shade.visibility == View.VISIBLE) hideShade()
        drawer.alpha = 0f
        drawer.visibility = View.VISIBLE
        drawer.animate().alpha(1f).setDuration(160).start()
    }

    private fun hideDrawer() {
        if (drawer.visibility != View.VISIBLE) return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(search.windowToken, 0)
        search.setText("")
        search.clearFocus()
        drawer.animate().alpha(0f).setDuration(140).withEndAction { drawer.visibility = View.GONE }.start()
    }

    private fun showShade() {
        if (drawer.visibility == View.VISIBLE) hideDrawer()
        refreshNotifs()
        refreshTiles()
        syncBrightness()
        shade.alpha = 0f
        shade.translationY = -dp(40)
        shade.visibility = View.VISIBLE
        shade.animate().alpha(1f).translationY(0f).setDuration(200).start()
    }

    private fun hideShade() {
        if (shade.visibility != View.VISIBLE) return
        shade.animate().alpha(0f).translationY(-dp(40)).setDuration(160)
            .withEndAction { shade.visibility = View.GONE }.start()
    }

    private fun closeOverlays() { hideDrawer(); hideShade() }

    // ---------- নোটিফিকেশন ----------

    private fun hasNotifAccess() = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun refreshNotifs() {
        val list = NotificationStore.items
        notifAdapter.data = list
        notifAdapter.notifyDataSetChanged()
        notifCount.text = if (list.isEmpty()) "" else "🔔 ${list.size}"
        val access = hasNotifAccess()
        notifAccess.visibility = if (access) View.GONE else View.VISIBLE
        barAccess.visibility = if (UbuntuShellService.instance == null) View.VISIBLE else View.GONE
        notifEmpty.visibility = if (access && list.isEmpty()) View.VISIBLE else View.GONE
        renderDock()
    }

    companion object {
        const val ACTION_DRAWER = "com.ubuntu.launcher.DRAWER"
        const val ACTION_SHADE = "com.ubuntu.launcher.SHADE"
    }

    // ---------- অ্যাডাপ্টার ----------

    inner class AppAdapter : RecyclerView.Adapter<AppAdapter.VH>() {
        var items: List<AppInfo> = emptyList()

        fun submit(l: List<AppInfo>) { items = l; notifyDataSetChanged() }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.icon)
            val label: TextView = v.findViewById(R.id.label)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_app, p, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val app = items[i]
            h.icon.setImageDrawable(app.icon)
            h.label.text = app.label
            h.itemView.setOnClickListener { launch(app) }
            h.itemView.setOnLongClickListener { showMenu(h.itemView, app); true }
        }
    }

    inner class NotifAdapter : RecyclerView.Adapter<NotifAdapter.VH>() {
        var data: List<StatusBarNotification> = emptyList()

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.nIcon)
            val app: TextView = v.findViewById(R.id.nApp)
            val time: TextView = v.findViewById(R.id.nTime)
            val title: TextView = v.findViewById(R.id.nTitle)
            val text: TextView = v.findViewById(R.id.nText)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_notif, p, false))

        override fun getItemCount() = data.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val sbn = data[i]
            val n = sbn.notification
            val ex = n.extras
            h.title.text = ex.getCharSequence(Notification.EXTRA_TITLE) ?: ""
            h.text.text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: ex.getCharSequence(Notification.EXTRA_TEXT) ?: ""
            h.app.text = apps.find { it.pkg == sbn.packageName }?.label ?: sbn.packageName
            h.time.text = DateUtils.getRelativeTimeSpanString(
                sbn.postTime, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
            )
            try {
                h.icon.setImageDrawable(n.smallIcon?.loadDrawable(this@MainActivity))
                h.icon.setColorFilter(Color.WHITE)
            } catch (_: Exception) {
                h.icon.setImageDrawable(null)
            }
            h.itemView.setOnClickListener {
                try { n.contentIntent?.send() } catch (_: PendingIntent.CanceledException) {}
                if (n.flags and Notification.FLAG_AUTO_CANCEL != 0) NotificationStore.cancel(sbn.key)
                hideShade()
            }
        }
    }
}
