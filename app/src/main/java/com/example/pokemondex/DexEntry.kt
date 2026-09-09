package com.example.pokemondex

import android.content.Context
import org.json.JSONObject

data class DexEntry(
    val no: Int,
    val name: String,
    val attr1: String = "",
    val attr2: String = "",
    val egg: String = "",
    val species: String = "",
    val height: String = "",
    val weight: String = "",
    val total: Int = 0,
    val obtain: String = "",
    val desc: String = "",
    val img: String = ""
)

object DexRepository {

    private const val ASSET = "default_dex.json"

    fun load(context: Context): List<DexEntry> {
        val result = ArrayList<DexEntry>()
        try {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val array = root.optJSONArray("dex") ?: return result
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name", "").trim()
                if (name.isEmpty()) continue
                result.add(
                    DexEntry(
                        no = item.optInt("no", 0),
                        name = name,
                        attr1 = item.optString("attr1", "").trim(),
                        attr2 = item.optString("attr2", "").trim(),
                        egg = item.optString("egg", "").trim(),
                        species = item.optString("species", "").trim(),
                        height = item.optString("height", "").trim(),
                        weight = item.optString("weight", "").trim(),
                        total = item.optInt("total", 0),
                        obtain = item.optString("obtain", "").trim(),
                        desc = item.optString("desc", "").trim(),
                        img = item.optString("img", "")
                            .ifEmpty { item.optString("slug", "") }
                            .trim()
                    )
                )
            }
        } catch (e: Exception) {
        }
        return result
    }
}
