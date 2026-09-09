package com.example.pokemondex

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    // 更新源：GitHub Releases（公开仓库）
    const val OWNER = "MengBi840"
    const val REPO = "roco-shiny-updates"

    fun check(onResult: (latestVersion: String, downloadUrl: String) -> Unit) {
        Thread {
            try {
                val conn = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.requestMethod = "GET"
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "").removePrefix("v")
                var url = ""
                val assets = json.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    url = assets.getJSONObject(0).optString("browser_download_url", "")
                }
                if (tag.isNotEmpty() && url.isNotEmpty()) {
                    Handler(Looper.getMainLooper()).post { onResult(tag, url) }
                }
            } catch (e: Exception) {
            }
        }.start()
    }

    fun isNewer(latest: String, current: String): Boolean {
        if (latest == current) return false
        val l = tokenize(latest)
        val c = tokenize(current)
        val n = maxOf(l.size, c.size)
        for (i in 0 until n) {
            val a = if (i < l.size) l[i] else 0
            val b = if (i < c.size) c[i] else 0
            if (a > b) return true
            if (a < b) return false
        }
        return false
    }

    private fun tokenize(s: String): List<Int> = s.split(".").mapNotNull { it.toIntOrNull() }
}
