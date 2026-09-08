package com.ric.animesubid

data class Anime(
    val malId: Int,
    val title: String,
    val titleEnglish: String?,
    val year: Int?,
    val type: String?,
    val status: String?,
    val episodes: Int?,
    val score: Double?,
    val imageUrl: String?,
    val genres: List<String>
) {
    val displayTitle: String get() = titleEnglish?.takeIf { it.isNotBlank() } ?: title
}
