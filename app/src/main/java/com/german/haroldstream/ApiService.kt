package com.german.haroldstream

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Headers
import retrofit2.http.DELETE
import retrofit2.http.Path

interface ApiService {

    @Headers("ngrok-skip-browser-warning: any_value")
    @GET("descargar")
    suspend fun descargarCancion(@Query("url") youtubeUrl: String): ResponseData

    @Headers("ngrok-skip-browser-warning: any_value")
    @GET("buscar")
    suspend fun buscarCancion(@Query("termino") termino: String): SearchResponse

    @Headers("ngrok-skip-browser-warning: any_value")
    @GET("canciones")
    suspend fun obtenerCancionesDescargadas(): SearchResponse

    @Headers("ngrok-skip-browser-warning: any_value")
    @DELETE("canciones/{archivo}")
    suspend fun eliminarCancion(@Path("archivo") archivo: String): DeleteResponse
}

data class ResponseData(
    val status: String?,
    val url: String?,
    val titulo: String?,
    val archivo: String?,
    val thumbnail: String?,
    val canal: String?,
    val duracion: String?,
    val message: String?
)

data class SearchResponse(val canciones: List<Cancion>)

data class DeleteResponse(val status: String?, val message: String?)

data class Cancion(
    val id: String? = null,
    val titulo: String? = null,
    val url: String? = null,
    val thumbnail: String? = null,
    val duracion: String? = null,
    val canal: String? = null,
    val archivo: String? = null,
    var isFavorite: Boolean = false,
    var isDownloaded: Boolean = false,
    var localPath: String? = null
)
