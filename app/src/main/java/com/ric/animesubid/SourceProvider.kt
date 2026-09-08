package com.ric.animesubid

import android.net.Uri

data class SearchSource(val name: String, val url: String, val note: String)

object SourceProvider {
    fun directSearches(title: String): List<SearchSource> {
        val q = Uri.encode("$title anime subtitle Indonesia")
        val qShort = Uri.encode("$title sub indo")
        return listOf(
            SearchSource(
                "Otakudesu",
                "https://www.google.com/search?q=$qShort+site%3Aotakudesu.io",
                "Cari halaman judul/episode di Otakudesu"
            ),
            SearchSource(
                "Kuramanime",
                "https://www.google.com/search?q=$qShort+Kuramanime",
                "Pakai hasil domain terbaru karena alamat Kuramanime sering berubah"
            ),
            SearchSource(
                "LK21",
                "https://www.google.com/search?q=$qShort+LK21",
                "Sumber umum film/serial; hasil anime tergantung katalog"
            ),
            SearchSource(
                "Bstation / Bilibili",
                "https://www.google.com/search?q=$q+site%3Abilibili.tv",
                "Cari ketersediaan resmi untuk Indonesia"
            ),
            SearchSource(
                "YouTube",
                "https://www.youtube.com/results?search_query=$qShort",
                "Cek Muse Indonesia, Ani-One, dan kanal resmi lain"
            ),
            SearchSource(
                "Crunchyroll",
                "https://www.google.com/search?q=$q+site%3Acrunchyroll.com",
                "Ketersediaan subtitle bergantung judul dan wilayah"
            ),
            SearchSource(
                "Netflix Indonesia",
                "https://www.google.com/search?q=$q+site%3Anetflix.com%2Fid",
                "Cari judul di katalog Indonesia"
            ),
            SearchSource(
                "Web Sub Indo",
                "https://www.google.com/search?q=$q",
                "Cari sumber lain jika sumber utama tidak tersedia"
            )
        )
    }

    fun legalSearches(title: String): List<SearchSource> = directSearches(title)
}
