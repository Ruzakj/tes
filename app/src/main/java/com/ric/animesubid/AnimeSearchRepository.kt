package com.ric.animesubid

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class SearchFilters(val type: String? = null, val status: String? = null)

class AnimeSearchRepository {
    fun search(query: String, filters: SearchFilters = SearchFilters()): List<Anime> {
        var jikanError: Throwable? = null

        try {
            val jikanResults = searchJikan(query, filters)
            if (jikanResults.isNotEmpty()) return jikanResults
        } catch (e: Throwable) {
            jikanError = e
        }

        try {
            val anilistResults = searchAniList(query, filters)
            if (anilistResults.isNotEmpty()) return anilistResults
            return emptyList()
        } catch (anilistError: Throwable) {
            throw IOException(
                "Semua server pencarian sedang tidak tersedia. Coba lagi beberapa saat.",
                anilistError.takeIf { jikanError != null } ?: jikanError
            )
        }
    }

    private fun searchJikan(query: String, filters: SearchFilters): List<Anime> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val primaryParams = buildList {
            add("q=$encoded")
            add("limit=20")
            add("sfw=true")
            filters.type?.let { add("type=$it") }
            filters.status?.let { add("status=$it") }
        }.joinToString("&")

        val fallbackParams = "q=$encoded&limit=12&sfw=true"
        val attempts = listOf(primaryParams, primaryParams, fallbackParams)
        var lastError: Throwable? = null

        for ((index, params) in attempts.withIndex()) {
            try {
                return executeJikanRequest(params)
            } catch (e: RetryableHttpException) {
                lastError = e
                if (index < attempts.lastIndex) Thread.sleep(450L * (index + 1))
            } catch (e: IOException) {
                lastError = e
                if (index < attempts.lastIndex) Thread.sleep(350L * (index + 1))
            }
        }
        throw IOException(lastError?.message ?: "Jikan tidak tersedia", lastError)
    }

    private fun executeJikanRequest(params: String): List<Anime> {
        val connection = (URL("https://api.jikan.moe/v4/anime?$params").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7_000
            readTimeout = 10_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AnimeSubIDSearch/0.3 (Android)")
            setRequestProperty("Cache-Control", "no-cache")
        }

        return try {
            val code = connection.responseCode
            if (code in setOf(429, 502, 503, 504)) throw RetryableHttpException(code)
            if (code !in 200..299) throw IOException("Jikan HTTP $code")
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            parseJikan(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun searchAniList(query: String, filters: SearchFilters): List<Anime> {
        val graphQl = """
            query (\$search: String) {
              Page(page: 1, perPage: 20) {
                media(search: \$search, type: ANIME, sort: SEARCH_MATCH) {
                  id
                  title { romaji english }
                  seasonYear
                  format
                  status
                  episodes
                  averageScore
                  coverImage { large }
                  genres
                }
              }
            }
        """.trimIndent()

        val payload = JSONObject()
            .put("query", graphQl)
            .put("variables", JSONObject().put("search", query))
            .toString()

        val connection = (URL("https://graphql.anilist.co").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7_000
            readTimeout = 10_000
            doOutput = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", "AnimeSubIDSearch/0.3 (Android)")
        }

        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("AniList HTTP $code")
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            parseAniList(raw, filters)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseJikan(raw: String): List<Anime> {
        val data = JSONObject(raw).getJSONArray("data")
        return buildList {
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
    }

    private fun parseAniList(raw: String, filters: SearchFilters): List<Anime> {
        val media = JSONObject(raw)
            .getJSONObject("data")
            .getJSONObject("Page")
            .getJSONArray("media")

        return buildList {
            for (i in 0 until media.length()) {
                val item = media.getJSONObject(i)
                val mappedType = when (item.optString("format")) {
                    "TV", "TV_SHORT" -> "TV"
                    "MOVIE" -> "Movie"
                    "OVA" -> "OVA"
                    "ONA" -> "ONA"
                    "SPECIAL" -> "Special"
                    else -> item.optString("format").replace('_', ' ').ifBlank { null }
                }
                val mappedStatus = when (item.optString("status")) {
                    "RELEASING" -> "Currently Airing"
                    "FINISHED" -> "Finished Airing"
                    "NOT_YET_RELEASED" -> "Not yet aired"
                    else -> item.optString("status").replace('_', ' ').ifBlank { null }
                }

                val typeMatches = filters.type == null || when (filters.type) {
                    "tv" -> mappedType == "TV"
                    "movie" -> mappedType == "Movie"
                    "ova" -> mappedType == "OVA"
                    "ona" -> mappedType == "ONA"
                    "special" -> mappedType == "Special"
                    else -> true
                }
                val statusMatches = filters.status == null || when (filters.status) {
                    "airing" -> mappedStatus == "Currently Airing"
                    "complete" -> mappedStatus == "Finished Airing"
                    else -> true
                }
                if (!typeMatches || !statusMatches) continue

                val titles = item.optJSONObject("title")
                val genresArray = item.optJSONArray("genres")
                val genres = buildList {
                    if (genresArray != null) for (j in 0 until genresArray.length()) {
                        genresArray.optString(j).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                val averageScore = item.optDouble("averageScore").takeIf { !it.isNaN() && it > 0 }
                add(
                    Anime(
                        malId = -item.optInt("id"),
                        title = titles?.optString("romaji")?.takeIf { it.isNotBlank() } ?: "Tanpa judul",
                        titleEnglish = titles?.optString("english")?.takeIf { it.isNotBlank() && it != "null" },
                        year = item.optInt("seasonYear").takeIf { it > 0 },
                        type = mappedType,
                        status = mappedStatus,
                        episodes = item.optInt("episodes").takeIf { it > 0 },
                        score = averageScore?.div(10.0),
                        imageUrl = item.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() },
                        genres = genres
                    )
                )
            }
        }
    }

    private class RetryableHttpException(val code: Int) : IOException("HTTP $code")
}
