package com.german.haroldstream

import android.app.DownloadManager
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object LocalMusicManager {

    private const val PREFS_NAME = "HaroldSoundPrefs"
    private const val KEY_FAVORITES = "key_favorite_songs_json"

    fun guardarFavorito(context: Context, cancion: Cancion) {
        val favoritos = obtenerFavoritos(context).toMutableList()
        favoritos.removeAll { normalizar(it.titulo) == normalizar(cancion.titulo) || (it.id != null && it.id == cancion.id) }
        cancion.isFavorite = true
        favoritos.add(0, cancion)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(favoritos)
        prefs.edit().putString(KEY_FAVORITES, json).apply()
    }

    fun quitarFavorito(context: Context, cancion: Cancion) {
        val favoritos = obtenerFavoritos(context).toMutableList()
        favoritos.removeAll { normalizar(it.titulo) == normalizar(cancion.titulo) || (it.id != null && it.id == cancion.id) }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(favoritos)
        prefs.edit().putString(KEY_FAVORITES, json).apply()
    }

    fun esFavorito(context: Context, cancion: Cancion): Boolean {
        val favoritos = obtenerFavoritos(context)
        return favoritos.any { normalizar(it.titulo) == normalizar(cancion.titulo) || (it.id != null && it.id == cancion.id) }
    }

    fun esDescargadoLocalmente(context: Context, cancion: Cancion): Boolean {
        val titulo = cancion.titulo ?: return false
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$titulo.mp3")
        val fileSub = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "HaroldSound/$titulo.mp3")
        return (file.exists() && file.length() > 0) || (fileSub.exists() && fileSub.length() > 0) || (cancion.url != null && (cancion.url.startsWith("content://") || cancion.url.startsWith("file://")))
    }

    fun obtenerFavoritos(context: Context): List<Cancion> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val listType = object : TypeToken<List<Cancion>>() {}.type
            val lista: List<Cancion> = Gson().fromJson(json, listType) ?: emptyList()
            lista.distinctBy { normalizar(it.titulo) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun descargarMP3EnCelular(context: Context, url: String, titulo: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(titulo)
                .setDescription("Guardando canción MP3 en tu celular...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$titulo.mp3")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cargarCancionesLocalesMP3(context: Context): List<Cancion> {
        val listaLocal = mutableListOf<Cancion>()
        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE
            )

            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (cursor.moveToNext()) {
                    val size = cursor.getLong(sizeCol)
                    val durationMs = cursor.getLong(durationCol)

                    // Filtrar archivos corruptos de 0 bytes o sin duración
                    if (size < 1000 || durationMs < 2000) continue

                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val title = cursor.getString(titleCol) ?: "Pista MP3"
                    val artist = cursor.getString(artistCol) ?: "Local"

                    val mins = (durationMs / 1000) / 60
                    val secs = (durationMs / 1000) % 60
                    val duracionStr = String.format("%d:%02d", mins, secs)

                    listaLocal.add(
                        Cancion(
                            id = id.toString(),
                            titulo = title,
                            url = contentUri.toString(),
                            canal = if (artist == "<unknown>") "HaroldSound Offline" else artist,
                            duracion = duracionStr,
                            isDownloaded = true,
                            localPath = contentUri.toString()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return listaLocal.distinctBy { normalizar(it.titulo) }
    }

    private fun normalizar(texto: String?): String {
        return (texto ?: "").lowercase().trim().replace(Regex("[^a-z0-9]"), "")
    }
}
