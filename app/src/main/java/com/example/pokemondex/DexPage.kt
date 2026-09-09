package com.example.pokemondex

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DexPage(private val context: Context, private val root: View) {

    companion object {
        private const val COLOR_ACTIVE = "#4A90E2"
        private const val COLOR_BG_INACTIVE = "#EDF3FA"
        private const val COLOR_TEXT_ACTIVE = "#FFFFFF"
        private const val COLOR_TEXT_INACTIVE = "#7E94AB"
        private val ATTR_ORDER = listOf(
            "普通", "火", "水", "草", "电", "冰", "地", "翼", "毒", "虫",
            "武", "萌", "幻", "幽", "恶", "机械", "龙", "光"
        )
    }

    private lateinit var etSearch: EditText
    private lateinit var llAttrChips: LinearLayout
    private lateinit var llEggChips: LinearLayout
    private lateinit var llSortChips: LinearLayout
    private lateinit var btnFilterToggle: Button
    private lateinit var filterPanel: View
    private lateinit var tvDexCount: TextView
    private lateinit var recyclerDex: RecyclerView

    private var all: List<DexEntry> = emptyList()
    private var attrFilter: String = ""
    private var eggFilter: String = ""
    private var ascending = true
    private val adapter = DexAdapter(context) { entry -> showDetail(entry) }

    fun init() {
        etSearch = root.findViewById(R.id.etDexSearch)
        llAttrChips = root.findViewById(R.id.llDAttr)
        llEggChips = root.findViewById(R.id.llDEgg)
        llSortChips = root.findViewById(R.id.llDSort)
        btnFilterToggle = root.findViewById(R.id.btnDFilter)
        filterPanel = root.findViewById(R.id.filterDPanel)
        tvDexCount = root.findViewById(R.id.tvDexCountD)
        recyclerDex = root.findViewById(R.id.recyclerDexD)

        recyclerDex.layoutManager = LinearLayoutManager(context)
        recyclerDex.adapter = adapter

        btnFilterToggle.setOnClickListener {
            val show = filterPanel.visibility != View.VISIBLE
            filterPanel.visibility = if (show) View.VISIBLE else View.GONE
            btnFilterToggle.text = if (show) "筛选 ▴" else "筛选 ▾"
        }

        all = DexRepository.load(context)
        buildAttrChips()
        buildEggChips()
        buildSortChips()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilters()
            }
        })

        applyFilters()
    }

    private fun buildAttrChips() {
        llAttrChips.removeAllViews()
        val present = HashSet<String>()
        for (e in all) {
            if (e.attr1.isNotEmpty()) present.add(e.attr1)
            if (e.attr2.isNotEmpty()) present.add(e.attr2)
        }
        val ordered = ATTR_ORDER.filter { present.contains(it) }
        addChip(llAttrChips, "全部", attrFilter == "") { attrFilter = ""; refreshChips(); applyFilters() }
        for (attr in ordered) {
            addChip(llAttrChips, attr, attrFilter == attr) { attrFilter = attr; refreshChips(); applyFilters() }
        }
    }

    private fun buildEggChips() {
        llEggChips.removeAllViews()
        val groups = HashSet<String>()
        for (e in all) {
            for (g in splitEggs(e.egg)) groups.add(g)
        }
        val ordered = groups.sorted()
        addChip(llEggChips, "全部", eggFilter == "") { eggFilter = ""; refreshChips(); applyFilters() }
        for (egg in ordered) {
            addChip(llEggChips, egg, eggFilter == egg) { eggFilter = egg; refreshChips(); applyFilters() }
        }
    }

    private fun buildSortChips() {
        llSortChips.removeAllViews()
        addChip(llSortChips, "编号 ↑", ascending) { ascending = true; refreshChips(); applyFilters() }
        addChip(llSortChips, "编号 ↓", !ascending) { ascending = false; refreshChips(); applyFilters() }
    }

    private fun refreshChips() {
        buildAttrChips()
        buildEggChips()
        buildSortChips()
    }

    private fun splitEggs(raw: String): List<String> {
        if (raw.isEmpty()) return emptyList()
        return raw.split(Regex("[、，,/&；;]")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun applyFilters() {
        val keyword = etSearch.text.toString().trim()
        val result = ArrayList<DexEntry>()
        for (e in all) {
            val attrOk = attrFilter.isEmpty() || e.attr1 == attrFilter || e.attr2 == attrFilter
            val eggOk = eggFilter.isEmpty() || splitEggs(e.egg).contains(eggFilter)
            val kwOk = keyword.isEmpty() ||
                e.name.contains(keyword, ignoreCase = true) ||
                e.no.toString().contains(keyword) ||
                e.species.contains(keyword, ignoreCase = true)
            if (attrOk && eggOk && kwOk) result.add(e)
        }
        result.sortWith(
            if (ascending) compareBy<DexEntry> { it.no }.thenBy { it.name }
            else compareByDescending<DexEntry> { it.no }.thenByDescending { it.name }
        )
        adapter.submit(result)
        tvDexCount.text = "${result.size} / ${all.size}"
    }

    private fun showDetail(entry: DexEntry) {
        val sb = StringBuilder()
        if (entry.egg.isNotEmpty()) sb.append("蛋组：").append(entry.egg).append('\n')
        if (entry.species.isNotEmpty()) sb.append("种类：").append(entry.species).append('\n')
        if (entry.total > 0) sb.append("总种族值：").append(entry.total).append('\n')
        if (entry.height.isNotEmpty()) sb.append("身高：").append(entry.height).append('\n')
        if (entry.weight.isNotEmpty()) sb.append("体重：").append(entry.weight).append('\n')
        if (entry.obtain.isNotEmpty()) sb.append("获取：").append(entry.obtain).append('\n')
        if (entry.desc.isNotEmpty()) sb.append("\n").append(entry.desc)
        AlertDialog.Builder(context)
            .setTitle("NO.${entry.no}  ${entry.name}")
            .setMessage(sb.toString())
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun addChip(container: LinearLayout, text: String, active: Boolean, action: () -> Unit) {
        val button = Button(context)
        button.text = text
        button.textSize = 12f
        button.setTypeface(null, Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34))
        lp.marginEnd = dp(6)
        button.layoutParams = lp
        styleChip(button, active)
        button.setOnClickListener { action() }
        container.addView(button)
    }

    private fun styleChip(button: Button, active: Boolean) {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(17).toFloat()
        bg.setColor(Color.parseColor(if (active) COLOR_ACTIVE else COLOR_BG_INACTIVE))
        button.background = bg
        button.setTextColor(
            Color.parseColor(if (active) COLOR_TEXT_ACTIVE else COLOR_TEXT_INACTIVE)
        )
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
