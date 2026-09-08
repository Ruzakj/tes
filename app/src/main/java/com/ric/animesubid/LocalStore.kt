package com.ric.animesubid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("anime_sub_id", Context.MODE_PRIVATE)

    fun addHistory(query: String) {
        val next = (listOf(query) + history()).distinctBy { it.lowercase() }.take(8)
        prefs.edit().putString("history", JSONArray(next).toString()).apply()
    }

    fun history(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString("history", "[]"))
        List(arr.length()) { arr.getString(it) }
    }.getOrDefault(emptyList())

    fun clearHistory() = prefs.edit().remove("history").apply()

    fun bookmarks(): List<Anime> = runCatching {
        val arr = JSONArray(prefs.getString("bookmarks", "[]"))
        List(arr.length()) { i -> arr.getJSONObject(i).toAnime() }
    }.getOrDefault(emptyList())

    fun toggleBookmark(anime: Anime): Boolean {
        val items = bookmarks().toMutableList()
        val index = items.indexOfFirst { it.malId == anime.malId }
        val added = if (index >= 0) { items.removeAt(index); false } else { items.add(0, anime); true }
        prefs.edit().putString("bookmarks", JSONArray(items.map { it.toJson() }).toString()).apply()
        return added
    }

    private fun Anime.toJson() = JSONObject().apply {
        put("malId", malId); put("title", title); put("titleEnglish", titleEnglish)
        put("year", year); put("type", type); put("status", status); put("episodes", episodes)
        put("score", score); put("imageUrl", imageUrl); put("genres", JSONArray(genres))
    }

    private fun JSONObject.toAnime(): Anime {
        val g = optJSONArray("genres") ?: JSONArray()
        return Anime(
            malId = optInt("malId"), title = optString("title"),
            titleEnglish = optString("titleEnglish").takeIf { it.isNotBlank() && it != "null" },
            year = optInt("year").takeIf { it > 0 }, type = optString("type").takeIf { it.isNotBlank() && it != "null" },
            status = optString("status").takeIf { it.isNotBlank() && it != "null" }, episodes = optInt("episodes").takeIf { it > 0 },
            score = optDouble("score").takeIf { !it.isNaN() && it > 0 }, imageUrl = optString("imageUrl").takeIf { it.isNotBlank() && it != "null" },
            genres = List(g.length()) { g.optString(it) }.filter { it.isNotBlank() }
        )
    }
}
