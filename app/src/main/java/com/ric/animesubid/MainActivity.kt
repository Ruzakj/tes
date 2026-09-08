package com.ric.animesubid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val repository = AnimeSearchRepository()
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var store: LocalStore
    private lateinit var adapter: AnimeAdapter
    private lateinit var input: AutoCompleteTextView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var searchButton: Button
    private lateinit var typeSpinner: Spinner
    private lateinit var statusSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = LocalStore(this)

        input = findViewById(R.id.searchInput)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.statusText)
        searchButton = findViewById(R.id.searchButton)
        typeSpinner = findViewById(R.id.typeSpinner)
        statusSpinner = findViewById(R.id.statusSpinner)
        val list = findViewById<RecyclerView>(R.id.resultsList)
        val bookmarksButton = findViewById<Button>(R.id.bookmarksButton)
        val clearHistory = findViewById<TextView>(R.id.clearHistory)

        adapter = AnimeAdapter(::showSources) { anime ->
            val added = store.toggleBookmark(anime)
            Toast.makeText(this, if (added) "Ditambahkan ke favorit" else "Dihapus dari favorit", Toast.LENGTH_SHORT).show()
            added
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        setupSpinners()
        refreshHistoryAdapter()
        searchButton.setOnClickListener { runSearch() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { runSearch(); true } else false
        }
        bookmarksButton.setOnClickListener { showBookmarks() }
        clearHistory.setOnClickListener {
            store.clearHistory(); refreshHistoryAdapter()
            Toast.makeText(this, "Riwayat dibersihkan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSpinners() {
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Semua tipe", "TV", "Movie", "OVA", "ONA", "Special"))
        statusSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Semua status", "Sedang tayang", "Selesai"))
    }

    private fun selectedFilters(): SearchFilters {
        val type = when (typeSpinner.selectedItemPosition) { 1 -> "tv"; 2 -> "movie"; 3 -> "ova"; 4 -> "ona"; 5 -> "special"; else -> null }
        val state = when (statusSpinner.selectedItemPosition) { 1 -> "airing"; 2 -> "complete"; else -> null }
        return SearchFilters(type, state)
    }

    private fun refreshHistoryAdapter() {
        input.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, store.history()))
        input.threshold = 0
    }

    private fun runSearch() {
        val query = input.text.toString().trim()
        if (query.length < 2) { input.error = "Masukkan minimal 2 karakter"; return }
        val filters = selectedFilters()
        store.addHistory(query); refreshHistoryAdapter()
        progress.visibility = View.VISIBLE; status.text = "Mencari anime…"; searchButton.isEnabled = false
        executor.execute {
            runCatching { repository.search(query, filters) }
                .onSuccess { results -> runOnUiThread {
                    adapter.submit(results, store.bookmarks().map { it.malId }.toSet())
                    status.text = if (results.isEmpty()) "Tidak ada hasil." else "${results.size} hasil • tap anime untuk cari Sub Indo"
                    progress.visibility = View.GONE; searchButton.isEnabled = true
                }}
                .onFailure { error -> runOnUiThread {
                    status.text = "Pencarian gagal: ${error.message ?: "koneksi bermasalah"}"
                    progress.visibility = View.GONE; searchButton.isEnabled = true
                }}
        }
    }

    private fun showBookmarks() {
        val items = store.bookmarks()
        if (items.isEmpty()) { Toast.makeText(this, "Belum ada anime favorit", Toast.LENGTH_SHORT).show(); return }
        adapter.submit(items, items.map { it.malId }.toSet())
        status.text = "Favorit • ${items.size} anime"
    }

    private fun showSources(anime: Anime) {
        val sources = SourceProvider.legalSearches(anime.displayTitle)
        AlertDialog.Builder(this)
            .setTitle(anime.displayTitle)
            .setItems(sources.map { "${it.name}\n${it.note}" }.toTypedArray()) { _, which ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sources[which].url)))
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
