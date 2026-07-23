package com.german.haroldstream

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService(), PlayerManager.PlayerStateListener {
    private var mediaSession: MediaSession? = null
    
    private val ACTION_TOGGLE_FAVORITE = "ACTION_TOGGLE_FAVORITE"
    private val commandToggleFavorite = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        try {
            val exoPlayer = PlayerManager.getOrCreatePlayer(this)
            
            // ForwardingPlayer engaña al sistema para forzar botones Siguiente/Anterior
            val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
                override fun getAvailableCommands(): Player.Commands {
                    return super.getAvailableCommands().buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .build()
                }

                override fun hasNextMediaItem(): Boolean = true
                override fun hasPreviousMediaItem(): Boolean = true

                override fun seekToNext() {
                    PlayerManager.siguienteCancion(this@PlaybackService)
                }

                override fun seekToPrevious() {
                    PlayerManager.anteriorCancion(this@PlaybackService)
                }
            }

            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val callback = object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val connectionResult = super.onConnect(session, controller)
                    val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                        .add(commandToggleFavorite)
                        .build()
                    return MediaSession.ConnectionResult.accept(
                        availableSessionCommands,
                        connectionResult.availablePlayerCommands
                    )
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == ACTION_TOGGLE_FAVORITE) {
                        PlayerManager.currentCancion?.let { cancion ->
                            val esFav = LocalMusicManager.esFavorito(this@PlaybackService, cancion)
                            if (esFav) {
                                LocalMusicManager.quitarFavorito(this@PlaybackService, cancion)
                            } else {
                                LocalMusicManager.guardarFavorito(this@PlaybackService, cancion)
                                val url = PlayerManager.currentStreamUrl ?: cancion.url
                                if (!url.isNullOrEmpty()) {
                                    LocalMusicManager.descargarMP3EnCelular(this@PlaybackService, url, cancion.titulo ?: "Canción")
                                }
                            }
                            actualizarCustomLayout()
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            }

            mediaSession = MediaSession.Builder(this, forwardingPlayer)
                .setSessionActivity(pendingIntent)
                .setCallback(callback)
                .build()

            PlayerManager.addListener(this)
            actualizarCustomLayout()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun actualizarCustomLayout() {
        val esFav = PlayerManager.currentCancion?.let { LocalMusicManager.esFavorito(this, it) } ?: false
        
        val iconResId = if (esFav) R.drawable.ic_spotify_check else R.drawable.ic_spotify_plus
        val displayName = if (esFav) "Favorito" else "Agregar a Favoritos"

        val favoriteButton = CommandButton.Builder()
            .setDisplayName(displayName)
            .setSessionCommand(commandToggleFavorite)
            .setIconResId(iconResId)
            .build()

        mediaSession?.setCustomLayout(listOf(favoriteButton))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onSongChanged(cancion: Cancion?) {
        actualizarCustomLayout()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {}

    override fun onPlaybackReady(durationMs: Long) {}

    override fun onDestroy() {
        try {
            PlayerManager.removeListener(this)
            mediaSession?.player?.release()
            mediaSession?.release()
            mediaSession = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}
