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
import org.json.JSONObject

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

        renderEncounter()

        loadSeasons()
        checkForUpdate()
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

    private fun styleTabButton(button: Button, active: Boolean, colorHex: String) {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(23).toFloat()
        bg.setColor(if (active) parseColor(colorHex, DEFAULT_THEME) else parseColor(COLOR_BG_INACTIVE, COLOR_BG_INACTIVE))
        button.background = bg
        button.setTextColor(
            Color.parseColor(if (active) COLOR_TEXT_ACTIVE else COLOR_TEXT_INACTIVE)
        )
    }

    private fun styleNav(button: Button, active: Boolean) {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(22).toFloat()
        bg.setColor(Color.parseColor(if (active) "#4A90E2" else COLOR_BG_INACTIVE))
        button.background = bg
        button.setTextColor(
            Color.parseColor(if (active) COLOR_TEXT_ACTIVE else COLOR_TEXT_INACTIVE)
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

    private fun checkForUpdate() {
        UpdateChecker.check { latest, url ->
            val current = try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: Exception) {
                ""
            }
            if (UpdateChecker.isNewer(latest, current)) {
                showUpdateDialog(latest, url)
            }
        }
    }

    private fun showUpdateDialog(latest: String, url: String) {
        AlertDialog.Builder(this)
            .setTitle("发现新版本 v$latest")
            .setMessage("洛克精灵册 v$latest 已发布，是否立即下载更新安装？")
            .setPositiveButton("下载") { _, _ -> startDownload(url) }
            .setNegativeButton("稍后", null)
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
        }
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
