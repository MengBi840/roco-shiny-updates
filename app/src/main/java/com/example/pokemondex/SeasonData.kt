package com.example.pokemondex

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class TypeGroup(
    val type: String,
    val label: String,
    val color: String,
    val badge: String = ""
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put(KEY_TYPE, type)
        obj.put(KEY_LABEL, label)
        obj.put(KEY_COLOR, color)
        if (badge.isNotEmpty()) obj.put(KEY_BADGE, badge)
        return obj
    }

    companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_LABEL = "label"
        private const val KEY_COLOR = "color"
        private const val KEY_BADGE = "badge"

        fun fromJsonObject(obj: JSONObject): TypeGroup {
            val type = obj.optString(KEY_TYPE, "").trim()
            val label = obj.optString(KEY_LABEL, "").trim().ifEmpty { type }
            val color = obj.optString(KEY_COLOR, "").trim()
            val badge = obj.optString(KEY_BADGE, "").trim()
            return TypeGroup(type, label, color, badge)
        }
    }
}

data class SeasonData(
    val season: String,
    val pokemonList: List<Pokemon>,
    val seasonName: String = "",
    val themeColor: String = "",
    val groups: List<TypeGroup> = emptyList()
) {

    fun displayName(): String = if (seasonName.isNotEmpty()) "$season · $seasonName" else season

    fun effectiveGroups(): List<TypeGroup> {
        if (groups.isNotEmpty()) return groups
        val seen = LinkedHashSet<String>()
        for (p in pokemonList) seen.add(p.type)
        if (seen.isEmpty()) return emptyList()
        val result = ArrayList<TypeGroup>()
        for (t in seen) {
            result.add(TypeGroup(t, if (t.contains("异色")) t else "${t}异色", defaultColorFor(t), t))
        }
        return result
    }

    companion object {
        private const val KEY_SEASON = "season"
        private const val KEY_NAME = "name"
        private const val KEY_THEME_COLOR = "themeColor"
        private const val KEY_GROUPS = "groups"
        private const val KEY_POKEMON = "pokemon"
        private const val KEY_TYPE = "type"
        private const val KEY_ATTR1 = "attr1"
        private const val KEY_ATTR2 = "attr2"
        private const val KEY_CATEGORY = "category"
        private const val KEY_IMG = "img"
        private const val KEY_SEASONS = "seasons"

        fun defaultColorFor(type: String): String = when {
            type.contains("金") -> "#D4A843"
            type.contains("赤") -> "#C0392B"
            type == "赛季奇遇" -> "#e65100"
            type == "常驻异色" -> "#2e7d32"
            else -> "#607D8B"
        }

        fun parseSeasonData(text: String): SeasonData {
            val obj = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw IllegalArgumentException("文件内容不是合法的 JSON", e)
            }
            return parseSeasonData(obj)
        }

        fun parseSeasonData(obj: JSONObject): SeasonData {
            val season = obj.optString(KEY_SEASON, "").trim()
            if (season.isEmpty()) {
                throw IllegalArgumentException("缺少 season 字段或 season 为空")
            }
            val seasonName = obj.optString(KEY_NAME, "").trim()
            val themeColor = obj.optString(KEY_THEME_COLOR, "").trim()

            val groups = ArrayList<TypeGroup>()
            val groupsArray = obj.optJSONArray(KEY_GROUPS)
            if (groupsArray != null) {
                for (i in 0 until groupsArray.length()) {
                    val item = groupsArray.optJSONObject(i) ?: continue
                    try {
                        val group = TypeGroup.fromJsonObject(item)
                        if (group.type.isNotEmpty()) groups.add(group)
                    } catch (e: Exception) {
                    }
                }
            }

            val array = obj.optJSONArray(KEY_POKEMON)
                ?: throw IllegalArgumentException("缺少 pokemon 字段")
            val list = ArrayList<Pokemon>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                    ?: throw IllegalArgumentException("pokemon 第 ${i + 1} 项不是合法的对象")
                val name = item.optString(KEY_NAME, "").trim()
                if (name.isEmpty()) {
                    throw IllegalArgumentException("pokemon 第 ${i + 1} 项缺少 name")
                }
                val type = item.optString(KEY_TYPE, "").trim()
                if (type.isEmpty()) {
                    throw IllegalArgumentException("精灵 \"$name\" 缺少 type")
                }
                val attr1 = item.optString(KEY_ATTR1, "").trim()
                val attr2 = item.optString(KEY_ATTR2, "").trim()
                val category = item.optString(KEY_CATEGORY, "").trim()
                val img = item.optString(KEY_IMG, "").trim()
                list.add(Pokemon(name, type, attr1, attr2, category, img))
            }
            if (list.isEmpty()) {
                throw IllegalArgumentException("pokemon 列表为空")
            }
            return SeasonData(season, list, seasonName, themeColor, groups)
        }

        fun toJson(seasons: List<SeasonData>): String {
            val array = JSONArray()
            for (seasonData in seasons) {
                array.put(seasonData.toJsonObject())
            }
            return JSONObject().put(KEY_SEASONS, array).toString()
        }

        fun parseSeasonsJson(json: String): List<SeasonData> {
            val array = JSONObject(json).optJSONArray(KEY_SEASONS) ?: return emptyList()
            val result = ArrayList<SeasonData>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                try {
                    result.add(parseSeasonData(item))
                } catch (e: Exception) {
                }
            }
            return result
        }
    }

    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put(KEY_SEASON, season)
        if (seasonName.isNotEmpty()) obj.put(KEY_NAME, seasonName)
        if (themeColor.isNotEmpty()) obj.put(KEY_THEME_COLOR, themeColor)
        if (groups.isNotEmpty()) {
            val groupsArray = JSONArray()
            for (g in groups) groupsArray.put(g.toJsonObject())
            obj.put(KEY_GROUPS, groupsArray)
        }
        val array = JSONArray()
        for (p in pokemonList) {
            val item = JSONObject()
            item.put(KEY_NAME, p.name)
            item.put(KEY_TYPE, p.type)
            if (p.attr1.isNotEmpty()) item.put(KEY_ATTR1, p.attr1)
            if (p.attr2.isNotEmpty()) item.put(KEY_ATTR2, p.attr2)
            if (p.category.isNotEmpty()) item.put(KEY_CATEGORY, p.category)
            if (p.img.isNotEmpty()) item.put(KEY_IMG, p.img)
            array.put(item)
        }
        obj.put(KEY_POKEMON, array)
        return obj
    }
}
