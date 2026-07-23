package com.german.haroldstream

import android.content.Context
import android.provider.Settings
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object UserAuthManager {

    private const val PREFS_NAME = "HaroldSoundPrefs"
    private const val KEY_DEVICE_ID = "key_device_id"
    private const val KEY_USER_NAME = "key_user_name"
    private const val KEY_USER_PHONE = "key_user_phone"
    private const val KEY_IS_APPROVED = "key_is_approved"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun obtenerDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrEmpty()) {
            id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (id.isNullOrEmpty()) {
                id = "DEV-" + java.util.UUID.randomUUID().toString().take(12)
            }
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun esAprobadoLocalmente(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_APPROVED, false)
    }

    fun guardarDatosUsuarioLocal(context: Context, nombre: String, telefono: String, aprobado: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_USER_NAME, nombre)
            .putString(KEY_USER_PHONE, telefono)
            .putBoolean(KEY_IS_APPROVED, aprobado)
            .apply()
    }

    suspend fun solicitarCodigoPin(context: Context, serverUrl: String, nombre: String, telefono: String): Result<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val devId = obtenerDeviceId(context)
                val bodyMap = mapOf(
                    "deviceId" to devId,
                    "nombre" to nombre,
                    "telefono" to telefono
                )
                val jsonBody = Gson().toJson(bodyMap)

                var baseUrl = serverUrl.trim()
                if (!baseUrl.endsWith("/")) baseUrl += "/"
                val url = "${baseUrl}api/send-code"

                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody(JSON))
                    .build()

                val response = client.newCall(request).execute()
                val responseStr = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    guardarDatosUsuarioLocal(context, nombre, telefono, false)
                    val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                    val resMap: Map<String, Any> = Gson().fromJson(responseStr, mapType)
                    Result.success(resMap)
                } else {
                    Result.failure(Exception("Error en servidor: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun verificarCodigoPin(context: Context, serverUrl: String, code: String): Result<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val devId = obtenerDeviceId(context)
                val bodyMap = mapOf(
                    "deviceId" to devId,
                    "code" to code
                )
                val jsonBody = Gson().toJson(bodyMap)

                var baseUrl = serverUrl.trim()
                if (!baseUrl.endsWith("/")) baseUrl += "/"
                val url = "${baseUrl}api/verify-code"

                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody(JSON))
                    .build()

                val response = client.newCall(request).execute()
                val responseStr = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                    val resMap: Map<String, Any> = Gson().fromJson(responseStr, mapType)
                    Result.success(resMap)
                } else {
                    Result.failure(Exception("Error en servidor: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun verificarEstadoEnServidor(context: Context, serverUrl: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val devId = obtenerDeviceId(context)
                var baseUrl = serverUrl.trim()
                if (!baseUrl.endsWith("/")) baseUrl += "/"
                val url = "${baseUrl}api/check-status?deviceId=$devId"

                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val responseStr = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                    val resMap: Map<String, Any> = Gson().fromJson(responseStr, mapType)

                    val status = resMap["status"] as? String ?: "unregistered"
                    val esAprobado = status == "approved"
                    
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putBoolean(KEY_IS_APPROVED, esAprobado).apply()

                    Result.success(status)
                } else {
                    Result.failure(Exception("Error HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
