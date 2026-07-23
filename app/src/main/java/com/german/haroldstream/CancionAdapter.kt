package com.german.haroldstream

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class CancionAdapter(
    private var canciones: List<Cancion>,
    private val onClick: (Cancion) -> Unit,
    private val onFavoriteToggle: ((Cancion, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<CancionAdapter.CancionViewHolder>() {

    class CancionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitulo: TextView = itemView.findViewById(R.id.tv_titulo)
        val tvSubtitulo: TextView = itemView.findViewById(R.id.tv_subtitulo)
        val ivThumbnail: ImageView = itemView.findViewById(R.id.iv_thumbnail)
        val btnFavorito: ImageButton = itemView.findViewById(R.id.btn_favorito)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CancionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cancion, parent, false)
        return CancionViewHolder(view)
    }

    override fun onBindViewHolder(holder: CancionViewHolder, position: Int) {
        val cancion = canciones[position]
        val context = holder.itemView.context

        holder.tvTitulo.text = cancion.titulo ?: "Sin título"

        val canal = if (cancion.canal == "<unknown>") "HaroldSound MP3" else (cancion.canal ?: "HaroldSound MP3")
        val duracion = cancion.duracion ?: ""
        val subtitulo = when {
            canal.isNotEmpty() && duracion.isNotEmpty() -> "$canal • $duracion"
            canal.isNotEmpty() -> canal
            duracion.isNotEmpty() -> duracion
            else -> "HaroldSound MP3"
        }
        holder.tvSubtitulo.text = subtitulo

        val thumbUrl = cancion.thumbnail
        if (!thumbUrl.isNullOrEmpty()) {
            holder.ivThumbnail.load(thumbUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_media_play)
                error(android.R.drawable.ic_media_play)
            }
        } else if (cancion.url != null && (cancion.url.startsWith("content://") || cancion.url.startsWith("file://"))) {
            val artBytes = extraerCaratulaLocal(context, Uri.parse(cancion.url))
            if (artBytes != null) {
                holder.ivThumbnail.load(artBytes) {
                    crossfade(true)
                }
            } else {
                holder.ivThumbnail.setImageResource(android.R.drawable.ic_media_play)
            }
        } else {
            holder.ivThumbnail.setImageResource(android.R.drawable.ic_media_play)
        }

        val esFav = LocalMusicManager.esFavorito(context, cancion)
        if (esFav) {
            holder.btnFavorito.setImageResource(R.drawable.ic_spotify_check)
            holder.btnFavorito.clearColorFilter()
        } else {
            holder.btnFavorito.setImageResource(R.drawable.ic_spotify_plus)
            holder.btnFavorito.clearColorFilter()
        }

        holder.btnFavorito.setOnClickListener {
            val nuevoEstado = !LocalMusicManager.esFavorito(context, cancion)
            if (nuevoEstado) {
                holder.btnFavorito.setImageResource(R.drawable.ic_spotify_check)
            } else {
                holder.btnFavorito.setImageResource(R.drawable.ic_spotify_plus)
            }
            holder.btnFavorito.clearColorFilter()
            onFavoriteToggle?.invoke(cancion, nuevoEstado)
        }

        holder.itemView.setOnClickListener { onClick(cancion) }
    }

    private fun extraerCaratulaLocal(context: android.content.Context, uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    override fun getItemCount(): Int = canciones.size

    fun actualizarLista(nuevasCanciones: List<Cancion>) {
        this.canciones = nuevasCanciones
        notifyDataSetChanged()
    }
}
