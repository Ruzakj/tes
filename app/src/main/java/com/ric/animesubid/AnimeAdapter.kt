package com.ric.animesubid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class AnimeAdapter(
    private val onClick: (Anime) -> Unit,
    private val onBookmark: (Anime) -> Boolean
) : RecyclerView.Adapter<AnimeAdapter.VH>() {
    private val items = mutableListOf<Anime>()
    private var bookmarkedIds: Set<Int> = emptySet()

    fun submit(data: List<Anime>, bookmarked: Set<Int> = bookmarkedIds) {
        items.clear(); items.addAll(data); bookmarkedIds = bookmarked; notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_anime, parent, false), onClick, onBookmark
    )
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position], bookmarkedIds.contains(items[position].malId))
    override fun getItemCount() = items.size

    class VH(view: View, private val onClick: (Anime) -> Unit, private val onBookmark: (Anime) -> Boolean) : RecyclerView.ViewHolder(view) {
        private val poster: ImageView = view.findViewById(R.id.poster)
        private val title: TextView = view.findViewById(R.id.title)
        private val meta: TextView = view.findViewById(R.id.meta)
        private val genres: TextView = view.findViewById(R.id.genres)
        private val bookmark: ImageButton = view.findViewById(R.id.bookmarkButton)
        private var current: Anime? = null

        init {
            view.setOnClickListener { current?.let(onClick) }
            bookmark.setOnClickListener {
                current?.let { anime ->
                    val added = onBookmark(anime)
                    bookmark.setImageResource(if (added) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
                }
            }
        }

        fun bind(item: Anime, isBookmarked: Boolean) {
            current = item
            title.text = item.displayTitle
            meta.text = listOfNotNull(item.type, item.year?.toString(), item.episodes?.let { "$it eps" }, item.score?.let { "★ %.1f".format(it) }).joinToString("  •  ")
            genres.text = item.genres.take(3).joinToString(" • ").ifBlank { item.status.orEmpty() }
            bookmark.setImageResource(if (isBookmarked) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
            poster.load(item.imageUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
            }
        }
    }
}
