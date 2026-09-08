package com.ric.animesubid

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class SearchFilters(val type: String? = null, val status: String? = null)

class AnimeSearchRepository {
    fun search(query: String, filters: SearchFilters = SearchFilters()): List<Anime> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val params = buildList {
            add("q=$encoded")
            add("limit=24")
            add("sfw=true")
            add("order_by=popularity")
            filters.type?.let { add("type=$it") }
            filters.status?.let { add("status=$it") }
        }.joinToString("&")
        val connection = (URL("https://api.jikan.moe/v4/anime?$params").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AnimeSubIDSearch/0.2")
        }
        return try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(raw).getJSONArray("data")
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val genresArray = item.optJSONArray("genres")
                    val genres = buildList {
                        if (genresArray != null) for (j in 0 until genresArray.length()) {
                            genresArray.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                    val image = item.optJSONObject("images")?.optJSONObject("jpg")?.optString("image_url")
                    add(
                        Anime(
                            malId = item.optInt("mal_id"),
                            title = item.optString("title").ifBlank { "Tanpa judul" },
                            titleEnglish = item.optString("title_english").takeIf { it.isNotBlank() && it != "null" },
                            year = item.optInt("year").takeIf { it > 0 },
                            type = item.optString("type").takeIf { it.isNotBlank() && it != "null" },
                            status = item.optString("status").takeIf { it.isNotBlank() && it != "null" },
                            episodes = item.optInt("episodes").takeIf { it > 0 },
                            score = item.optDouble("score").takeIf { !it.isNaN() && it > 0 },
                            imageUrl = image?.takeIf { it.isNotBlank() && it != "null" },
                            genres = genres
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
