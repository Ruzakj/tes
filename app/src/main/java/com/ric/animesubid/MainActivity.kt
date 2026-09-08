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

class MainActivity : AppCompatActivity() {
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
        progress.visibility = View.GONE
        status.text = "Cari langsung ke beberapa sumber Sub Indo tanpa server metadata."

        searchButton.setOnClickListener { runDirectSearch() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runDirectSearch(); true
            } else false
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
        typeSpinner.isEnabled = false
        statusSpinner.isEnabled = false
        typeSpinner.alpha = 0.6f
        statusSpinner.alpha = 0.6f
    }

    private fun refreshHistoryAdapter() {
        input.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, store.history()))
        input.threshold = 0
    }

    private fun runDirectSearch() {
        val query = input.text.toString().trim()
        if (query.length < 2) {
            input.error = "Masukkan minimal 2 karakter"
            return
        }

        store.addHistory(query)
        refreshHistoryAdapter()
        status.text = "Pilih sumber untuk mencari “$query”"
        showDirectSources(query)
    }

    private fun showDirectSources(query: String) {
        val sources = SourceProvider.directSearches(query)
        AlertDialog.Builder(this)
            .setTitle("Cari: $query")
            .setItems(sources.map { "${it.name}\n${it.note}" }.toTypedArray()) { _, which ->
                val source = sources[which]
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                }.onFailure {
                    Toast.makeText(this, "Tidak bisa membuka ${source.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showBookmarks() {
        val items = store.bookmarks()
        if (items.isEmpty()) {
            Toast.makeText(this, "Belum ada anime favorit", Toast.LENGTH_SHORT).show()
            return
        }
        adapter.submit(items, items.map { it.malId }.toSet())
        status.text = "Favorit • ${items.size} anime"
    }

    private fun showSources(anime: Anime) {
        showDirectSources(anime.displayTitle)
    }
}
