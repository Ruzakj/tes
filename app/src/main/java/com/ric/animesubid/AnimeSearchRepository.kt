package com.ric.animesubid

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class SearchFilters(val type: String? = null, val status: String? = null)

class AnimeSearchRepository {
    fun search(query: String, filters: SearchFilters = SearchFilters()): List<Anime> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())

        val primaryParams = buildList {
            add("q=$encoded")
            add("limit=20")
            add("sfw=true")
            filters.type?.let { add("type=$it") }
            filters.status?.let { add("status=$it") }
        }.joinToString("&")

        // Fallback query sengaja dibuat lebih ringan. Beberapa 504 dari Jikan
        // terjadi ketika upstream sedang sibuk; query minimal biasanya pulih lebih cepat.
        val fallbackParams = buildList {
            add("q=$encoded")
            add("limit=12")
            add("sfw=true")
        }.joinToString("&")

        val attempts = listOf(primaryParams, primaryParams, fallbackParams)
        var lastError: Throwable? = null

        for ((index, params) in attempts.withIndex()) {
            try {
                return executeRequest(params)
            } catch (e: RetryableHttpException) {
                lastError = e
                if (index < attempts.lastIndex) Thread.sleep(650L * (index + 1))
            } catch (e: IOException) {
                lastError = e
                if (index < attempts.lastIndex) Thread.sleep(500L * (index + 1))
            }
        }

        throw IOException(
            when (lastError) {
                is RetryableHttpException -> "Server anime sedang sibuk. Coba lagi beberapa saat."
                else -> lastError?.message ?: "Koneksi ke server anime gagal."
            },
            lastError
        )
    }

    private fun executeRequest(params: String): List<Anime> {
        val connection = (URL("https://api.jikan.moe/v4/anime?$params").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AnimeSubIDSearch/0.2.1 (Android)")
            setRequestProperty("Cache-Control", "no-cache")
        }

        return try {
            val code = connection.responseCode
            if (code in setOf(429, 502, 503, 504)) throw RetryableHttpException(code)
            if (code !in 200..299) throw IOException("Server mengembalikan HTTP $code")

            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            parseAnime(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseAnime(raw: String): List<Anime> {
        val data = JSONObject(raw).getJSONArray("data")
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val genresArray = item.optJSONArray("genres")
                val genres = buildList {
                    if (genresArray != null) {
                        for (j in 0 until genresArray.length()) {
                            genresArray.optJSONObject(j)
                                ?.optString("name")
                                ?.takeIf { it.isNotBlank() }
                                ?.let(::add)
                        }
                    }
                }

                val image = item.optJSONObject("images")
                    ?.optJSONObject("jpg")
                    ?.optString("image_url")

                add(
                    Anime(
                        malId = item.optInt("mal_id"),
                        title = item.optString("title").ifBlank { "Tanpa judul" },
                        titleEnglish = item.optString("title_english")
                            .takeIf { it.isNotBlank() && it != "null" },
                        year = item.optInt("year").takeIf { it > 0 },
                        type = item.optString("type")
                            .takeIf { it.isNotBlank() && it != "null" },
                        status = item.optString("status")
                            .takeIf { it.isNotBlank() && it != "null" },
                        episodes = item.optInt("episodes").takeIf { it > 0 },
                        score = item.optDouble("score")
                            .takeIf { !it.isNaN() && it > 0 },
                        imageUrl = image?.takeIf { it.isNotBlank() && it != "null" },
                        genres = genres
                    )
                )
            }
        }
    }

    private class RetryableHttpException(val code: Int) : IOException("HTTP $code")
}
