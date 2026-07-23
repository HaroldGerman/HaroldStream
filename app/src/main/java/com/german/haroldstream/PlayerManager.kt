package com.german.haroldstream

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

object PlayerManager {

    var player: ExoPlayer? = null
        private set

    var currentCancion: Cancion? = null
        private set

    var currentStreamUrl: String? = null
        private set

    var listaReproduccion: List<Cancion> = emptyList()
        private set

    var indiceActual: Int = -1
        private set

    var onAutoPlayNextListener: ((Cancion) -> Unit)? = null
    var onAutoPlayRelatedListener: ((Cancion?) -> Unit)? = null

    private val listeners = mutableListOf<PlayerStateListener>()

    interface PlayerStateListener {
        fun onSongChanged(cancion: Cancion?)
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onPlaybackReady(durationMs: Long)
    }

    fun addListener(listener: PlayerStateListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: PlayerStateListener) {
        listeners.remove(listener)
    }

    fun getOrCreatePlayer(context: Context): ExoPlayer {
        if (player == null) {
            val appContext = context.applicationContext
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()

                // Simular un navegador Chrome para saltar el firewall de Cloudflare
                val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setAllowCrossProtocolRedirects(true)

                val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
                    .setDataSourceFactory(httpDataSourceFactory)

                val newPlayer = ExoPlayer.Builder(appContext)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setAudioAttributes(audioAttributes, true)
                    .setHandleAudioBecomingNoisy(true)
                    .build()

                newPlayer.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        listeners.forEach { it.onIsPlayingChanged(isPlaying) }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            listeners.forEach { it.onPlaybackReady(newPlayer.duration) }
                        } else if (playbackState == Player.STATE_ENDED) {
                            // Reproducción automática de la siguiente canción al terminar la actual
                            siguienteCancion(appContext)
                        }
                    }
                })

                player = newPlayer
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        return player!!
    }

    fun establecerListaReproduccion(lista: List<Cancion>, indiceInicial: Int) {
        this.listaReproduccion = lista
        this.indiceActual = indiceInicial
    }

    fun playCancion(context: Context, cancion: Cancion, streamUrl: String) {
        try {
            val p = getOrCreatePlayer(context)
            currentCancion = cancion
            currentStreamUrl = streamUrl

            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(cancion.titulo ?: "Canción")
                .setArtist(cancion.canal ?: "Desconocido")
                .setArtworkUri(if (!cancion.thumbnail.isNullOrEmpty()) Uri.parse(cancion.thumbnail) else null)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaMetadata(mediaMetadata)
                .build()

            p.setMediaItem(mediaItem)
            p.prepare()
            p.play()

            listeners.forEach { it.onSongChanged(cancion) }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun siguienteCancion(context: Context) {
        if (listaReproduccion.isEmpty()) {
            onAutoPlayRelatedListener?.invoke(currentCancion)
            return
        }
        val nuevoIndice = (indiceActual + 1) % listaReproduccion.size
        
        // Si acabamos de dar la vuelta completa (estamos en el índice 0 otra vez) y no es el único,
        // podríamos buscar relacionados, pero por ahora solo seguimos la lista circularmente.
        // O si preferimos, que busque si llega al final. 
        if (nuevoIndice == 0 && listaReproduccion.size > 1) {
            onAutoPlayRelatedListener?.invoke(currentCancion)
            return
        }
        
        indiceActual = nuevoIndice
        val siguiente = listaReproduccion[nuevoIndice]
        onAutoPlayNextListener?.invoke(siguiente)
    }

    fun anteriorCancion(context: Context) {
        // Reiniciar la canción si: no hay lista, es la primera canción, o ya pasaron más de 3 segundos
        if (listaReproduccion.isEmpty() || indiceActual == 0 || (player?.currentPosition ?: 0L) > 3000) {
            seekTo(0)
            return
        }
        val nuevoIndice = if (indiceActual - 1 < 0) listaReproduccion.size - 1 else indiceActual - 1
        indiceActual = nuevoIndice
        val anterior = listaReproduccion[nuevoIndice]
        onAutoPlayNextListener?.invoke(anterior)
    }

    fun togglePlayPause() {
        try {
            player?.let {
                if (it.isPlaying) {
                    it.pause()
                } else {
                    it.play()
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            player?.seekTo(positionMs)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
