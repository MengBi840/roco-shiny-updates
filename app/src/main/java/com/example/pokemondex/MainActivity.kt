package com.example.pokemondex

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class MerchantItem(
    val name: String,
    val category: String,
    val price: String,
    val limit: String
)

private data class EncounterRow(
    val pet: String,
    val season: String,
    val start: String,
    val end: String,
    val ball: String
)

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "shiny_dex_prefs"
        private const val KEY_SEASONS_JSON = "seasons_json"
        private const val KEY_DATA_VERSION = "data_version"
        private const val KEY_MERCHANT_HISTORY = "merchant_history"
        private const val KEY_NIGHT = "night_mode"
        private const val DATA_VERSION = 3
        private const val ASSET_S4 = "default_s4_data.json"
        private const val ASSET_LEGACY = "default_legacy_seasons.json"
        private const val ASSET_SPECIAL = "default_special_seasons.json"
        private const val COLOR_BG_INACTIVE = "#EDF3FA"
        private const val COLOR_TEXT_ACTIVE = "#FFFFFF"
        private const val COLOR_TEXT_INACTIVE = "#7E94AB"
        private const val DEFAULT_THEME = "#CFE3F5"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var seasonSpinner: Spinner
    private lateinit var themeLine: View
    private lateinit var tvTypeProgress: TextView
    private lateinit var tvSeasonProgress: TextView
    private lateinit var tabContainer: LinearLayout
    private lateinit var recyclerPokemon: RecyclerView
    private lateinit var unlockSwitch: Switch
    private lateinit var encounterCard: View
    private lateinit var ivEncAvatar: ImageView
    private lateinit var tvEncPlaceholder: TextView
    private lateinit var tvEncName: TextView
    private lateinit var tvEncInfo: TextView
    private lateinit var tvEncExpand: TextView
    private lateinit var btnSettings: View
    private lateinit var settingsDot: View
    private lateinit var merchantCard: View
    private lateinit var tvMerchantStatus: TextView
    private lateinit var tvMerchantOpen: TextView
    private var hasUpdate = false
    private var latestVersion = ""
    private var updateUrl = ""
    private var updateNotes = ""
    private var merchantItems: List<MerchantItem> = emptyList()
    private var merchantRound = ""
    private var merchantNext = ""
    private lateinit var btnTheme: View
    private lateinit var btnThemeIcon: TextView
    private lateinit var seasonPanel: View
    private lateinit var bottomNav: View
    private var nightMode = false
    private val savedColors = HashMap<View, Int>()
    private var currentPage = 0
    private var merchantNextMs = 0L
    private val merchantHandler = Handler(Looper.getMainLooper())
    private val merchantTicker = object : Runnable {
        override fun run() {
            updateMerchantStatus()
            merchantHandler.postDelayed(this, 1000L)
        }
    }
    private lateinit var pageTracking: LinearLayout
    private lateinit var pageDex: android.widget.FrameLayout
    private lateinit var btnNav1: Button
    private lateinit var btnNav2: Button
    private var dexReady = false
    private var dexPage: DexPage? = null

    private val encCache = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private var encRows: List<EncounterRow> = emptyList()

    private val seasons: LinkedHashMap<String, SeasonData> = LinkedHashMap()
    private var seasonsOrder: List<SeasonData> = emptyList()
    private var currentSeasonId: String = ""
    private var currentGroupIndex: Int = 0
    private var uiReady = false

    private val pokemonAdapter = PokemonAdapter(this) { name, checked ->
        onUnlockChanged(name, checked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        seasonSpinner = findViewById(R.id.seasonSpinner)
        themeLine = findViewById(R.id.vThemeLine)
        tvTypeProgress = findViewById(R.id.tvTypeProgress)
        tvSeasonProgress = findViewById(R.id.tvSeasonProgress)
        tabContainer = findViewById(R.id.tabContainer)
        recyclerPokemon = findViewById(R.id.recyclerPokemon)
        unlockSwitch = findViewById(R.id.unlockSwitch)
        encounterCard = findViewById(R.id.encounterCard)
        ivEncAvatar = findViewById(R.id.ivEncAvatar)
        tvEncPlaceholder = findViewById(R.id.tvEncPlaceholder)
        tvEncName = findViewById(R.id.tvEncName)
        tvEncInfo = findViewById(R.id.tvEncInfo)
        tvEncExpand = findViewById(R.id.tvEncExpand)

        pokemonAdapter.unlockedProvider = { name -> isUnlocked(currentSeasonId, name) }
        recyclerPokemon.layoutManager = LinearLayoutManager(this)
        recyclerPokemon.adapter = pokemonAdapter

        unlockSwitch.setOnCheckedChangeListener { _, isChecked ->
            pokemonAdapter.unlockMode = isChecked
            pokemonAdapter.notifyDataSetChanged()
        }

        pageTracking = findViewById(R.id.pageTracking)
        pageDex = findViewById(R.id.pageDex)
        btnNav1 = findViewById(R.id.btnNav1)
        btnNav2 = findViewById(R.id.btnNav2)
        styleNav(btnNav1, true)
        styleNav(btnNav2, false)
        btnNav1.setOnClickListener { showPage(0) }
        btnNav2.setOnClickListener { showPage(1) }

        btnSettings = findViewById(R.id.btnSettings)
        settingsDot = findViewById(R.id.settingsDot)
        merchantCard = findViewById(R.id.merchantCard)
        tvMerchantStatus = findViewById(R.id.tvMerchantStatus)
        tvMerchantOpen = findViewById(R.id.tvMerchantOpen)
        btnSettings.setOnClickListener { showSettingsDialog() }
        tvMerchantOpen.setOnClickListener { openMerchantDialog() }
        merchantCard.setOnClickListener { openMerchantDialog() }
        fetchMerchant()

        btnTheme = findViewById(R.id.btnTheme)
        btnThemeIcon = findViewById(R.id.btnThemeIcon)
        seasonPanel = findViewById(R.id.seasonPanel)
        bottomNav = findViewById(R.id.bottomNav)
        nightMode = prefs.getBoolean(KEY_NIGHT, false)
        btnTheme.setOnClickListener { applyTheme(!nightMode) }
        applyTheme(nightMode)

        renderEncounter()

        loadSeasons()
        checkForUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        merchantHandler.removeCallbacks(merchantTicker)
    }

    private fun loadSeasons() {
        seasons.clear()
        val stored = prefs.getString(KEY_SEASONS_JSON, null)
        val version = prefs.getInt(KEY_DATA_VERSION, 0)
        if (stored.isNullOrEmpty() || version != DATA_VERSION) {
            seedDefaultSeasons()
            if (seasons.isEmpty()) {
                seedFallback()
            }
            saveSeasons()
            prefs.edit().putInt(KEY_DATA_VERSION, DATA_VERSION).apply()
        } else {
            try {
                SeasonData.parseSeasonsJson(stored).forEach { seasons[it.season] = it }
            } catch (e: Exception) {
                seasons.clear()
                seedDefaultSeasons()
                if (seasons.isEmpty()) {
                    seedFallback()
                }
                saveSeasons()
            }
        }
        if (seasons.isEmpty()) {
            seedFallback()
            saveSeasons()
        }

        seasonSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (!uiReady) return
                if (position in seasonsOrder.indices) selectSeason(seasonsOrder[position].season)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val defaultId = if (seasons.containsKey("S4")) "S4" else seasons.keys.first()
        refreshSpinnerAndSelect(defaultId)
        uiReady = true
        applySeasonUi()
    }

    private fun seedDefaultSeasons() {
        try {
            val text = assets.open(ASSET_S4).bufferedReader().use { it.readText() }
            val seasonData = SeasonData.parseSeasonData(text)
            seasons[seasonData.season] = seasonData
        } catch (e: Exception) {
        }
        try {
            val text = assets.open(ASSET_LEGACY).bufferedReader().use { it.readText() }
            SeasonData.parseSeasonsJson(text).forEach { seasons[it.season] = it }
        } catch (e: Exception) {
        }
        try {
            val text = assets.open(ASSET_SPECIAL).bufferedReader().use { it.readText() }
            SeasonData.parseSeasonsJson(text).forEach { seasons[it.season] = it }
        } catch (e: Exception) {
        }
    }

    private fun seedFallback() {
        seasons["S4"] = SeasonData(
            "S4",
            listOf(
                Pokemon("小灵菇", "金月", "幽", "", "常驻异色"),
                Pokemon("觅觅蝠", "赤月", "翼", "", "常驻异色")
            ),
            "月涌狂想",
            "#B2BFD3",
            listOf(
                TypeGroup("金月", "金月异色", "#D4A843", "金月"),
                TypeGroup("赤月", "赤月异色", "#C0392B", "赤月")
            )
        )
    }

    private fun refreshSpinnerAndSelect(seasonId: String) {
        seasonsOrder = ArrayList(seasons.values)
        val names = seasonsOrder.map { it.displayName() }
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            names
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        seasonSpinner.adapter = spinnerAdapter
        val index = seasonsOrder.indexOfFirst { it.season == seasonId }
        val target = if (index >= 0) index else 0
        currentSeasonId = seasonsOrder[target].season
        seasonSpinner.setSelection(target)
    }

    private fun selectSeason(seasonId: String) {
        if (seasonId == currentSeasonId) return
        currentSeasonId = seasonId
        currentGroupIndex = 0
        applySeasonUi()
    }

    private fun currentData(): SeasonData? = seasons[currentSeasonId]

    private fun currentGroups(): List<TypeGroup> = currentData()?.effectiveGroups() ?: emptyList()

    private fun currentGroup(): TypeGroup? {
        val groups = currentGroups()
        if (groups.isEmpty()) return null
        if (currentGroupIndex >= groups.size) currentGroupIndex = 0
        return groups[currentGroupIndex]
    }

    private fun applySeasonUi() {
        val data = currentData()
        if (data == null) return
        themeLine.setBackgroundColor(parseColor(data.themeColor, DEFAULT_THEME))
        buildTabButtons()
        applyTabStyles()
        refreshUi()
    }

    private fun buildTabButtons() {
        tabContainer.removeAllViews()
        val groups = currentGroups()
        groups.forEachIndexed { index, group ->
            val button = Button(this)
            button.text = group.label
            button.textSize = 15f
            button.setTypeface(null, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(0, dp(46), 1f)
            if (index < groups.size - 1) {
                lp.marginEnd = dp(8)
            }
            button.layoutParams = lp
            button.tag = index
            button.setOnClickListener { switchGroup(index) }
            tabContainer.addView(button)
        }
    }

    private fun switchGroup(index: Int) {
        if (currentGroupIndex == index) return
        currentGroupIndex = index
        applyTabStyles()
        refreshUi()
    }

    private fun applyTabStyles() {
        val groups = currentGroups()
        for (i in 0 until tabContainer.childCount) {
            val child = tabContainer.getChildAt(i)
            if (child !is Button) continue
            val active = i == currentGroupIndex
            val group = if (i < groups.size) groups[i] else null
            val colorHex = group?.color?.takeIf { it.isNotEmpty() }
                ?: if (active) DEFAULT_THEME else COLOR_BG_INACTIVE
            styleTabButton(child, active, colorHex)
        }
    }

    private fun inactiveBgHex(): String = if (nightMode) "#3B465E" else COLOR_BG_INACTIVE
    private fun inactiveTextHex(): String = if (nightMode) "#B6C0D4" else COLOR_TEXT_INACTIVE

    private fun styleTabButton(button: Button, active: Boolean, colorHex: String) {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(23).toFloat()
        val inactBg = inactiveBgHex()
        bg.setColor(if (active) parseColor(colorHex, DEFAULT_THEME) else parseColor(inactBg, inactBg))
        button.background = bg
        button.setTextColor(
            Color.parseColor(if (active) COLOR_TEXT_ACTIVE else inactiveTextHex())
        )
    }

    private fun styleNav(button: Button, active: Boolean) {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(22).toFloat()
        bg.setColor(Color.parseColor(if (active) "#4A90E2" else inactiveBgHex()))
        button.background = bg
        button.setTextColor(
            Color.parseColor(if (active) COLOR_TEXT_ACTIVE else inactiveTextHex())
        )
    }

    private fun refreshUi() {
        val data = currentData() ?: return
        val group = currentGroup() ?: return
        pokemonAdapter.setData(currentSeasonId, data.pokemonList, group.type)
        updateProgress()
    }

    private fun updateProgress() {
        val data = currentData() ?: return
        val group = currentGroup() ?: return
        val all = data.pokemonList
        val typed = all.filter { it.type == group.type }
        val typedUnlocked = typed.count { isUnlocked(currentSeasonId, it.name) }
        val allUnlocked = all.count { isUnlocked(currentSeasonId, it.name) }
        tvTypeProgress.text = "${group.label} · 已解锁 $typedUnlocked / ${typed.size}"
        val percent = if (all.isEmpty()) 0 else allUnlocked * 100 / all.size
        tvSeasonProgress.text = "${data.displayName()} 总进度: $allUnlocked / ${all.size} ($percent%)"
    }

    private fun onUnlockChanged(name: String, checked: Boolean) {
        prefs.edit().putBoolean(unlockKey(currentSeasonId, name), checked).apply()
        updateProgress()
    }

    private fun isUnlocked(season: String, name: String): Boolean =
        prefs.getBoolean(unlockKey(season, name), false)

    private fun unlockKey(season: String, name: String): String = "${season}_$name"

    private fun saveSeasons() {
        prefs.edit()
            .putString(KEY_SEASONS_JSON, SeasonData.toJson(seasons.values.toList()))
            .apply()
    }

    private fun currentVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName
    } catch (e: Exception) {
        ""
    }

    private fun checkForUpdate() {
        UpdateChecker.check { latest, url, notes ->
            if (UpdateChecker.isNewer(latest, currentVersion())) {
                hasUpdate = true
                latestVersion = latest
                updateUrl = url
                updateNotes = notes
                settingsDot.visibility = View.VISIBLE
            }
        }
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        view.findViewById<TextView>(R.id.tvCurVersion).text = "v${currentVersion()}"
        val latestRow = view.findViewById<View>(R.id.latestRow)
        val label = view.findViewById<TextView>(R.id.tvUpdateNotesLabel)
        val scroll = view.findViewById<View>(R.id.scrollUpdateNotes)
        val content = view.findViewById<TextView>(R.id.tvUpdateNotes)
        val status = view.findViewById<TextView>(R.id.tvUpdateStatus)
        val builder = AlertDialog.Builder(this).setView(view)
        if (hasUpdate) {
            latestRow.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvLatestVersion).text = "v$latestVersion"
            if (updateNotes.isNotEmpty()) {
                label.visibility = View.VISIBLE
                scroll.visibility = View.VISIBLE
                content.text = updateNotes
            }
            status.text = "检测到新版本，可点击下方按钮下载安装。"
            builder.setPositiveButton("下载更新") { _, _ -> startDownload(updateUrl) }
        } else {
            status.text = "当前已是最新版本。"
        }
        builder.setNegativeButton("关闭", null).show()
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun yesterdayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000L))

    private fun loadMerchantHistory(): List<JSONObject> {
        val raw = prefs.getString(KEY_MERCHANT_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<JSONObject>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(o)
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveMerchantToday(round: String, next: String, items: List<MerchantItem>) {
        val date = todayKey()
        val itemArr = JSONArray()
        for (it in items) {
            itemArr.put(
                JSONObject()
                    .put("name", it.name)
                    .put("category", it.category)
                    .put("price", it.price)
                    .put("limit", it.limit)
            )
        }
        val rec = JSONObject()
        rec.put("date", date)
        rec.put("round", round)
        rec.put("next", next)
        rec.put("items", itemArr)
        val list = loadMerchantHistory().filter { it.optString("date") != date }.toMutableList()
        list.add(0, rec)
        list.sortByDescending { it.optString("date") }
        val keep = list.take(2)
        val out = JSONArray()
        for (r in keep) out.put(r)
        prefs.edit().putString(KEY_MERCHANT_HISTORY, out.toString()).apply()
    }

    private fun fetchMerchant() {
        Thread {
            try {
                val url = URL("https://rocokingdomworld.org/data/merchant.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val round = json.optString("round", "")
                val next = json.optString("nextRefreshBeijing", "")
                val items = ArrayList<MerchantItem>()
                val arr = json.optJSONArray("items")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val name = o.optString("name", "").trim()
                        if (name.isEmpty()) continue
                        items.add(
                            MerchantItem(
                                name,
                                o.optString("category", ""),
                                o.optString("price", ""),
                                o.optString("limit", "")
                            )
                        )
                    }
                }
                runOnUiThread {
                    saveMerchantToday(round, next, items)
                    merchantItems = items
                    merchantRound = round
                    merchantNext = next
                    merchantNextMs = parseNextMs(next)
                    updateMerchantStatus()
                    merchantHandler.removeCallbacks(merchantTicker)
                    merchantHandler.post(merchantTicker)
                }
            } catch (e: Exception) {
                runOnUiThread { updateMerchantStatus() }
            }
        }.start()
    }

    private fun parseNextMs(s: String): Long {
        return try {
            val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            f.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
            f.parse(s)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun fmt(ms: Long): String {
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    private fun updateMerchantStatus() {
        val today = loadMerchantHistory().firstOrNull { it.optString("date") == todayKey() }
        if (today == null) {
            tvMerchantStatus.text = "今日未探索到"
            return
        }
        val round = today.optString("round", "")
        val items = today.optJSONArray("items")
        val cnt = if (items != null) items.length() else 0
        val next = today.optString("next", "")
        val remain = parseNextMs(next) - System.currentTimeMillis()
        tvMerchantStatus.text = if (remain > 0) {
            "第${round}轮 · ${cnt}件 · 商人离开 ${fmt(remain)}"
        } else {
            "第${round}轮 · ${cnt}件 · 数据日期 ${today.optString("date")}"
        }
    }

    private fun openMerchantDialog() {
        val history = loadMerchantHistory().take(2)
        if (history.isEmpty()) {
            Toast.makeText(this, "暂无商人记录，请联网获取后重试", Toast.LENGTH_SHORT).show()
            return
        }
        val today = todayKey()
        val yest = yesterdayKey()
        val sb = StringBuilder()
        if (history.none { it.optString("date") == today }) {
            sb.append("今日未探索到，以下为最近记录。\n\n")
        }
        for (rec in history) {
            val date = rec.optString("date", "")
            val label = when (date) {
                today -> "今天 ${date}"
                yest -> "昨天 ${date}"
                else -> date
            }
            val round = rec.optString("round", "")
            val next = rec.optString("next", "")
            sb.append("【").append(label).append("】第").append(round).append("轮")
            if (next.isNotEmpty() && parseNextMs(next) > System.currentTimeMillis()) {
                sb.append(" · 下次刷新 ").append(next)
            }
            sb.append("\n")
            val arr = rec.optJSONArray("items")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val it = arr.optJSONObject(i)
                    if (it == null) continue
                    val name = it.optString("name", "")
                    sb.append("  ▪ ").append(name)
                    val cat = it.optString("category", "")
                    if (cat.isNotEmpty()) sb.append("（").append(cat).append("）")
                    sb.append("  ").append(it.optString("price", "0")).append(" 洛克贝")
                    val lim = it.optString("limit", "")
                    if (lim.isNotEmpty()) sb.append(" · 限购 ").append(lim)
                    sb.append("\n")
                }
            }
            sb.append("\n")
        }
        AlertDialog.Builder(this)
            .setTitle("远行商人 · 近两日记录")
            .setMessage(sb.toString().trim())
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun startDownload(url: String) {
        try {
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url))
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            req.setTitle("洛克精灵册 更新包")
            req.setMimeType("application/vnd.android.package-archive")
            dm.enqueue(req)
        } catch (e: Exception) {
            Toast.makeText(this, "下载失败，请稍后再试", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderEncounter() {
        val rows = loadEncRows()
        encRows = rows
        if (rows.isEmpty()) {
            encounterCard.visibility = View.GONE
            return
        }
        encounterCard.visibility = View.VISIBLE
        val pick = pickEncounter(rows)
        tvEncName.text = "${pick.pet} · 本周大量出没"
        tvEncInfo.text = "${pick.start} – ${pick.end} · 球：${pick.ball}"
        bindEncAvatar(pick.pet)
        tvEncExpand.setOnClickListener {
            showEncountersDialog(rows)
        }
    }

    private fun loadEncRows(): List<EncounterRow> {
        return try {
            val text = assets.open("default_encounters.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(text).optJSONArray("encounters") ?: return emptyList()
            val result = ArrayList<EncounterRow>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pet = o.optString("pet", "").trim()
                if (pet.isEmpty()) continue
                result.add(
                    EncounterRow(
                        pet,
                        o.optString("season", ""),
                        o.optString("start", ""),
                        o.optString("end", ""),
                        o.optString("ball", "")
                    )
                )
            }
            result.sortedBy { mmdd(it.start) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun mmdd(s: String): Int {
        return try {
            val p = s.split("-")
            (p[0].trim().toInt()) * 100 + (p[1].trim().toInt())
        } catch (e: Exception) {
            0
        }
    }

    private fun pickEncounter(rows: List<EncounterRow>): EncounterRow {
        if (rows.isEmpty()) return EncounterRow("", "", "", "", "")
        val cal = java.util.Calendar.getInstance()
        val today = (cal.get(java.util.Calendar.MONTH) + 1) * 100 + cal.get(java.util.Calendar.DAY_OF_MONTH)
        val inRange = rows.firstOrNull { today >= mmdd(it.start) && today <= mmdd(it.end) }
        if (inRange != null) return inRange
        val upcoming = rows.firstOrNull { mmdd(it.start) > today }
        return upcoming ?: rows.last()
    }

    private fun showEncountersDialog(rows: List<EncounterRow>) {
        val sb = StringBuilder()
        for (r in rows) {
            sb.append("▪ ").append(r.pet)
                .append("   ").append(r.start).append(" – ").append(r.end)
                .append("   ").append(r.ball).append("\n")
        }
        AlertDialog.Builder(this)
            .setTitle("S4 大量出没时间表")
            .setMessage(sb.toString().trim())
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun bindEncAvatar(pet: String) {
        val bmp = encBitmap(pet)
        if (bmp != null) {
            ivEncAvatar.setImageBitmap(bmp)
            ivEncAvatar.visibility = View.VISIBLE
            tvEncPlaceholder.visibility = View.GONE
        } else {
            ivEncAvatar.visibility = View.GONE
            tvEncPlaceholder.text = pet.take(1)
            tvEncPlaceholder.visibility = View.VISIBLE
        }
    }

    private fun encBitmap(pet: String): Bitmap? {
        encCache.get(pet)?.let { return it }
        val bmp = try {
            assets.open("img/S4/$pet.webp").use { s ->
                decodeSampled(s.readBytes(), 160)
            }
        } catch (e: Exception) {
            null
        }
        if (bmp != null) encCache.put(pet, bmp)
        return bmp
    }

    private fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val bigger = maxOf(bounds.outWidth, bounds.outHeight)
        while (bigger > 0 && bigger / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun showPage(index: Int) {
        currentPage = index
        pageTracking.visibility = if (index == 0) View.VISIBLE else View.GONE
        pageDex.visibility = if (index == 1) View.VISIBLE else View.GONE
        styleNav(btnNav1, index == 0)
        styleNav(btnNav2, index == 1)
        if (index == 1 && !dexReady) {
            dexReady = true
            val context = this
            val view = layoutInflater.inflate(R.layout.dex_page, pageDex, false)
            pageDex.addView(view)
            dexPage = DexPage(context, view)
            dexPage?.init()
            dexPage?.setNight(nightMode)
        }
    }

    private fun applyTheme(night: Boolean) {
        nightMode = night
        prefs.edit().putBoolean(KEY_NIGHT, night).apply()
        findViewById<View>(R.id.rootMain).setBackgroundResource(if (night) R.drawable.bg_app_dark else R.drawable.bg_app)
        for (c in listOf<View?>(encounterCard, merchantCard, seasonPanel, bottomNav)) {
            c?.setBackgroundResource(if (night) R.drawable.bg_panel_dark else R.drawable.bg_panel)
        }
        btnThemeIcon.setTextColor(Color.parseColor(if (night) "#F2C94C" else "#1F6FD6"))
        btnThemeIcon.text = if (night) "🌙" else "☀️"
        tvEncPlaceholder.setBackgroundResource(if (night) R.drawable.bg_placeholder_dark else R.drawable.bg_placeholder)
        recolorTexts(findViewById(R.id.rootMain), night)
        pokemonAdapter.night = night
        pokemonAdapter.notifyDataSetChanged()
        dexPage?.setNight(night)
        applyTabStyles()
        styleNav(btnNav1, currentPage == 0)
        styleNav(btnNav2, currentPage == 1)
    }

    private fun recolorTexts(view: View, night: Boolean) {
        val light = Color.parseColor("#E6EAF2")
        val sub = Color.parseColor("#B6C0D4")
        fun walk(v: View) {
            if (v === btnThemeIcon) return
            if (v is TextView && v !is Button) {
                if (!savedColors.containsKey(v)) savedColors[v] = v.currentTextColor
                val e = v as? android.widget.EditText
                v.setTextColor(if (night) light else savedColors[v] ?: light)
                if (e != null) e.setHintTextColor(if (night) sub else e.currentHintTextColor)
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
        }
        walk(view)
    }

    private fun parseColor(hex: String, fallback: String): Int {
        if (hex.isNotEmpty()) {
            try {
                return Color.parseColor(hex)
            } catch (e: Exception) {
            }
        }
        return Color.parseColor(fallback)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
