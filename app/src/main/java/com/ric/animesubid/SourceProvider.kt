package com.ric.animesubid

import android.net.Uri

data class SearchSource(val name: String, val url: String, val note: String)

object SourceProvider {
    fun legalSearches(title: String): List<SearchSource> {
        val generic = Uri.encode("$title anime subtitle Indonesia")
        val yt = Uri.encode("$title anime sub indo")
        return listOf(
            SearchSource("Bstation / Bilibili", "https://www.google.com/search?q=$generic+site%3Abilibili.tv", "Cari ketersediaan resmi untuk Indonesia"),
            SearchSource("YouTube", "https://www.youtube.com/results?search_query=$yt", "Prioritaskan kanal resmi seperti Muse Indonesia / Ani-One"),
            SearchSource("Crunchyroll", "https://www.google.com/search?q=$generic+site%3Acrunchyroll.com", "Ketersediaan subtitle berbeda tiap judul/wilayah"),
            SearchSource("Netflix Indonesia", "https://www.google.com/search?q=$generic+site%3Anetflix.com%2Fid", "Cari judul di katalog Indonesia"),
            SearchSource("Google", "https://www.google.com/search?q=$generic", "Pencarian web umum untuk sumber resmi")
        )
    }
}
