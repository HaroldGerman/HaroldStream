package com.german.haroldstream

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), PlayerManager.PlayerStateListener {

    private lateinit var adapter: CancionAdapter
    private lateinit var tvSeccionTitulo: TextView
    private lateinit var tvOfflineBanner: TextView
    private lateinit var progressBarMain: ProgressBar

    private lateinit var btnTabNube: Button
    private lateinit var btnTabFavoritas: Button
    private lateinit var btnTabDescargadas: Button

    // Vistas del Mini Reproductor Flotante
    private lateinit var layoutMiniPlayer: View
    private lateinit var ivMiniThumb: ImageView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnMiniPlayPause: ImageButton

    // Vistas del Sistema de Autenticación y Registro con PIN de 4 dígitos
    private lateinit var layoutAuthOverlay: View
    private lateinit var layoutRegistroForm: View
    private lateinit var layoutCodigoForm: View
    private lateinit var layoutPendienteForm: View
    private lateinit var etRegNombre: EditText
    private lateinit var etRegTelefono: EditText
    private lateinit var etRegPin: EditText
    private lateinit var tvCodigoInstruccion: TextView
    private lateinit var btnRegEnviar: Button
    private lateinit var btnVerificarPin: Button
    private lateinit var btnVerificarEstado: Button

    private var mediaController: MediaController? = null
    private var tabActual = TAB_NUBE
    private var listaCancionesActuales: List<Cancion> = emptyList()

    companion object {
        const val TAB_NUBE = 0
        const val TAB_FAVORITAS = 1
        const val TAB_DESCARGADAS = 2
    }

    private val PREFS_NAME = "HaroldSoundPrefs"
    private val KEY_HISTORY_JSON = "history_songs_json"
    
    // URL del Túnel Cloudflare corriendo en el celular (Termux) o PC
    private val DEFAULT_URL = "https://desirable-ind-stud-happy.trycloudflare.com/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        verificarPermisosLecturaAudio()

        // 1. Vincular vistas principales
        val etBusqueda = findViewById<EditText>(R.id.et_busqueda)
        val btnBuscar = findViewById<Button>(R.id.btn_buscar)
        progressBarMain = findViewById(R.id.progressBar)
        val rvResultados = findViewById<RecyclerView>(R.id.rv_resultados)
        tvSeccionTitulo = findViewById(R.id.tv_seccion_titulo)
        tvOfflineBanner = findViewById(R.id.tv_offline_banner)

        btnTabNube = findViewById(R.id.btn_tab_nube)
        btnTabFavoritas = findViewById(R.id.btn_tab_favoritas)
        btnTabDescargadas = findViewById(R.id.btn_tab_descargadas)

        // Mini Reproductor
        layoutMiniPlayer = findViewById(R.id.layout_mini_player)
        ivMiniThumb = findViewById(R.id.iv_mini_thumb)
        tvMiniTitle = findViewById(R.id.tv_mini_title)
        tvMiniArtist = findViewById(R.id.tv_mini_artist)
        btnMiniPlayPause = findViewById(R.id.btn_mini_play_pause)

        // Vistas Auth Overlay
        layoutAuthOverlay = findViewById(R.id.layout_auth_overlay)
        layoutRegistroForm = findViewById(R.id.layout_registro_form)
        layoutCodigoForm = findViewById(R.id.layout_codigo_form)
        layoutPendienteForm = findViewById(R.id.layout_pendiente_form)
        etRegNombre = findViewById(R.id.et_reg_nombre)
        etRegTelefono = findViewById(R.id.et_reg_telefono)
        etRegPin = findViewById(R.id.et_reg_pin)
        tvCodigoInstruccion = findViewById(R.id.tv_codigo_instruccion)
        btnRegEnviar = findViewById(R.id.btn_reg_enviar)
        btnVerificarPin = findViewById(R.id.btn_verificar_pin)
        btnVerificarEstado = findViewById(R.id.btn_verificar_estado)

        // 2. Comprobar autorización de usuario
        comprobarEstadoAutorizacion()

        // 3. Conexión con PlaybackService
        conectarConServicio()

        PlayerManager.onAutoPlayNextListener = { cancion ->
            runOnUiThread {
                reproducirCancionSeleccionada(cancion)
            }
        }

        PlayerManager.onAutoPlayRelatedListener = { cancionAnterior ->
            val query = cancionAnterior?.canal ?: cancionAnterior?.titulo ?: ""
            if (query.isNotEmpty()) {
                val api = obtenerApiService()
                if (api != null) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Buscando relacionados de: $query...", Toast.LENGTH_SHORT).show()
                    }
                    lifecycleScope.launch {
                        try {
                            val respuesta = api.buscarCancion(query)
                            if (respuesta.canciones.isNotEmpty()) {
                                val sugerencia = respuesta.canciones.firstOrNull { it.titulo != cancionAnterior?.titulo } ?: respuesta.canciones.first()
                                // Al reproducir esta canción, establecemos una nueva lista para que Siguiente siga funcionando
                                runOnUiThread {
                                    PlayerManager.establecerListaReproduccion(respuesta.canciones, respuesta.canciones.indexOf(sugerencia))
                                    reproducirCancionSeleccionada(sugerencia)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        // 4. Configurar el Adapter
        adapter = CancionAdapter(emptyList(), onClick = { cancionSeleccionada ->
            val posicion = listaCancionesActuales.indexOf(cancionSeleccionada)
            PlayerManager.establecerListaReproduccion(listaCancionesActuales, if (posicion != -1) posicion else 0)
            reproducirCancionSeleccionada(cancionSeleccionada)
        }, onFavoriteToggle = { cancion, nuevoEstado ->
            if (nuevoEstado) {
                marcarFavoritaYDescargar(cancion)
            } else {
                LocalMusicManager.quitarFavorito(this, cancion)
                Toast.makeText(this, "Removida de Favoritas", Toast.LENGTH_SHORT).show()
                if (tabActual == TAB_FAVORITAS) cargarPestañaFavoritas()
            }
        })

        rvResultados.layoutManager = LinearLayoutManager(this)
        rvResultados.adapter = adapter

        // 5. Listeners de Pestañas
        btnTabNube.setOnClickListener { cambiarPestaña(TAB_NUBE, etBusqueda) }
        btnTabFavoritas.setOnClickListener { cambiarPestaña(TAB_FAVORITAS, etBusqueda) }
        btnTabDescargadas.setOnClickListener { cambiarPestaña(TAB_DESCARGADAS, etBusqueda) }

        // Mini reproductor listener
        layoutMiniPlayer.setOnClickListener {
            PlayerManager.currentCancion?.let { cancion ->
                PlayerManager.currentStreamUrl?.let { streamUrl ->
                    abrirPlayerActivity(cancion, streamUrl)
                }
            }
        }

        btnMiniPlayPause.setOnClickListener {
            PlayerManager.togglePlayPause()
        }

        btnBuscar.setOnClickListener {
            if (tabActual != TAB_NUBE) cambiarPestaña(TAB_NUBE, etBusqueda)
            ejecutarBusqueda(etBusqueda, progressBarMain)
        }

        etBusqueda.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (tabActual != TAB_NUBE) cambiarPestaña(TAB_NUBE, etBusqueda)
                ejecutarBusqueda(etBusqueda, progressBarMain)
                true
            } else {
                false
            }
        }

        etBusqueda.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty() && tabActual == TAB_NUBE) {
                    cargarHistorialYRecomendaciones()
                }
            }
        })

        // --- LISTENERS DE REGISTRO Y VERIFICACIÓN PIN VIA WHATSAPP ---

        // PASO 1: Enviar Registro y Solicitar PIN por WhatsApp
        btnRegEnviar.setOnClickListener {
            val nombre = etRegNombre.text.toString().trim()
            val telefono = etRegTelefono.text.toString().trim()
            if (nombre.isEmpty() || telefono.isEmpty()) {
                Toast.makeText(this, "Por favor completa tu Nombre y Teléfono", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBarMain.visibility = View.VISIBLE
            lifecycleScope.launch {
                val result = UserAuthManager.solicitarCodigoPin(this@MainActivity, DEFAULT_URL, nombre, telefono)
                progressBarMain.visibility = View.GONE
                if (result.isSuccess) {
                    val resMap = result.getOrNull()
                    val status = resMap?.get("status") as? String
                    if (status == "error") {
                        val msg = resMap["message"] as? String ?: "Error al procesar el registro"
                        Toast.makeText(this@MainActivity, "❌ $msg", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "📱 Solicitud recibida. Revisa tu número de celular o WhatsApp para recibir tu PIN.", Toast.LENGTH_LONG).show()
                        mostrarPantallaCodigoPin()
                    }
                } else {
                    val ex = result.exceptionOrNull()
                    Toast.makeText(this@MainActivity, "Error de conexión: ${ex?.message ?: "No se pudo conectar al servidor"}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // PASO 2: Verificar Código PIN de 4 dígitos
        btnVerificarPin.setOnClickListener {
            val pin = etRegPin.text.toString().trim()
            if (pin.length != 4) {
                Toast.makeText(this, "Por favor ingresa tu código PIN de 4 dígitos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBarMain.visibility = View.VISIBLE
            lifecycleScope.launch {
                val result = UserAuthManager.verificarCodigoPin(this@MainActivity, DEFAULT_URL, pin)
                progressBarMain.visibility = View.GONE
                if (result.isSuccess) {
                    val resMap = result.getOrNull()
                    val status = resMap?.get("status") as? String
                    if (status == "success" || resMap?.get("user_status") == "pending") {
                        Toast.makeText(this@MainActivity, "✅ ¡Número verificado! Esperando aprobación del admin.", Toast.LENGTH_SHORT).show()
                        mostrarPantallaPendiente()
                    } else {
                        val msg = resMap?.get("message") as? String ?: "Código PIN incorrecto"
                        Toast.makeText(this@MainActivity, "❌ $msg", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val ex = result.exceptionOrNull()
                    Toast.makeText(this@MainActivity, "Error de conexión: ${ex?.message ?: "Servidor no disponible"}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // PASO 3: Verificar Aprobación del Admin (/admin)
        btnVerificarEstado.setOnClickListener {
            progressBarMain.visibility = View.VISIBLE
            lifecycleScope.launch {
                val result = UserAuthManager.verificarEstadoEnServidor(this@MainActivity, DEFAULT_URL)
                progressBarMain.visibility = View.GONE
                if (result.isSuccess) {
                    val status = result.getOrNull()
                    if (status == "approved") {
                        Toast.makeText(this@MainActivity, "¡Acceso Aprobado por Harold! 🎉", Toast.LENGTH_SHORT).show()
                        desbloquearApp()
                    } else if (status == "blocked") {
                        Toast.makeText(this@MainActivity, "🚫 Acceso Bloqueado por el Administrador", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Aún en espera de aprobación en el panel /admin ⏳", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val ex = result.exceptionOrNull()
                    Toast.makeText(this@MainActivity, "Error de conexión con AWS: ${ex?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        cargarHistorialYRecomendaciones()
    }

    private fun comprobarEstadoAutorizacion() {
        val aprobadoLocalmente = UserAuthManager.esAprobadoLocalmente(this)
        
        // Desbloquear inmediatamente si ya estaba aprobado localmente, 
        // pero NO hacer return, seguir comprobando con el servidor en segundo plano.
        if (aprobadoLocalmente) {
            desbloquearApp()
        }

        lifecycleScope.launch {
            val res = UserAuthManager.verificarEstadoEnServidor(this@MainActivity, DEFAULT_URL)
            if (res.isSuccess) {
                val status = res.getOrNull()
                when (status) {
                    "approved" -> desbloquearApp()
                    "code_sent" -> mostrarPantallaCodigoPin()
                    "pending" -> mostrarPantallaPendiente()
                    "blocked" -> {
                        mostrarPantallaPendiente()
                        Toast.makeText(this@MainActivity, "🚫 Acceso Bloqueado por el Administrador", Toast.LENGTH_LONG).show()
                    }
                    "unregistered" -> {
                        mostrarPantallaRegistro()
                        if (aprobadoLocalmente) {
                            Toast.makeText(this@MainActivity, "🚫 Acceso revocado (Usuario eliminado)", Toast.LENGTH_LONG).show()
                        }
                    }
                    else -> mostrarPantallaRegistro()
                }
            } else {
                // Si falla el servidor pero estaba aprobado localmente, lo dejamos pasar por ahora (modo offline).
                if (!aprobadoLocalmente) {
                    mostrarPantallaRegistro()
                }
            }
        }
    }

    private fun mostrarPantallaRegistro() {
        layoutAuthOverlay.visibility = View.VISIBLE
        layoutRegistroForm.visibility = View.VISIBLE
        layoutCodigoForm.visibility = View.GONE
        layoutPendienteForm.visibility = View.GONE
    }

    private fun mostrarPantallaCodigoPin() {
        layoutAuthOverlay.visibility = View.VISIBLE
        layoutRegistroForm.visibility = View.GONE
        layoutCodigoForm.visibility = View.VISIBLE
        layoutPendienteForm.visibility = View.GONE
        tvCodigoInstruccion.text = "Ingresa el código PIN de 4 dígitos enviado a tu número de WhatsApp / Celular:"
    }

    private fun mostrarPantallaPendiente() {
        layoutAuthOverlay.visibility = View.VISIBLE
        layoutRegistroForm.visibility = View.GONE
        layoutCodigoForm.visibility = View.GONE
        layoutPendienteForm.visibility = View.VISIBLE
    }

    private fun desbloquearApp() {
        layoutAuthOverlay.visibility = View.GONE
    }

    private fun reproducirCancionSeleccionada(cancion: Cancion) {
        val url = cancion.url
        if (!url.isNullOrEmpty()) {
            guardarEnHistorial(cancion)
            if (cancion.isDownloaded || url.startsWith("content://") || url.startsWith("file://") || url.contains("/descargas/")) {
                PlayerManager.playCancion(this, cancion, url)
            } else {
                reproducirStreamingDirecto(cancion, progressBarMain)
            }
        } else {
            Toast.makeText(this, "URL no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun marcarFavoritaYDescargar(cancion: Cancion) {
        val api = obtenerApiService()
        val urlOriginal = cancion.url

        if (api != null && !urlOriginal.isNullOrEmpty() && urlOriginal.contains("youtube")) {
            progressBarMain.visibility = View.VISIBLE
            Toast.makeText(this, "⭐ Guardando en Favoritas y Descargando MP3...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                try {
                    val respuesta = api.descargarCancion(youtubeUrl = urlOriginal)
                    if (respuesta.status == "success" && respuesta.url != null) {
                        val cancionCompleta = cancion.copy(
                            url = respuesta.url,
                            titulo = respuesta.titulo ?: cancion.titulo,
                            thumbnail = respuesta.thumbnail ?: cancion.thumbnail,
                            canal = respuesta.canal ?: cancion.canal,
                            duracion = respuesta.duracion ?: cancion.duracion,
                            isFavorite = true
                        )
                        LocalMusicManager.guardarFavorito(this@MainActivity, cancionCompleta)
                        LocalMusicManager.descargarMP3EnCelular(this@MainActivity, respuesta.url, cancionCompleta.titulo ?: "Canción")
                        Toast.makeText(this@MainActivity, "⭐ Canción guardada en Favoritas y descargada 📥", Toast.LENGTH_SHORT).show()
                    } else {
                        LocalMusicManager.guardarFavorito(this@MainActivity, cancion)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    LocalMusicManager.guardarFavorito(this@MainActivity, cancion)
                } finally {
                    progressBarMain.visibility = View.GONE
                    if (tabActual == TAB_FAVORITAS) cargarPestañaFavoritas()
                }
            }
        } else {
            LocalMusicManager.guardarFavorito(this, cancion)
            if (!urlOriginal.isNullOrEmpty()) {
                LocalMusicManager.descargarMP3EnCelular(this, urlOriginal, cancion.titulo ?: "Canción")
            }
            Toast.makeText(this, "⭐ Guardada en Favoritas", Toast.LENGTH_SHORT).show()
            if (tabActual == TAB_FAVORITAS) cargarPestañaFavoritas()
        }
    }

    private fun verificarPermisosLecturaAudio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 200)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 201)
            }
        }
    }

    private fun cambiarPestaña(nuevaPestaña: Int, etBusqueda: EditText) {
        tabActual = nuevaPestaña
        btnTabNube.setBackgroundResource(if (tabActual == TAB_NUBE) R.drawable.bg_button_gradient else R.drawable.bg_card_glowing)
        btnTabFavoritas.setBackgroundResource(if (tabActual == TAB_FAVORITAS) R.drawable.bg_button_gradient else R.drawable.bg_card_glowing)
        btnTabDescargadas.setBackgroundResource(if (tabActual == TAB_DESCARGADAS) R.drawable.bg_button_gradient else R.drawable.bg_card_glowing)

        btnTabNube.setTextColor(if (tabActual == TAB_NUBE) Color.parseColor("#FFFFFF") else Color.parseColor("#666666"))
        btnTabFavoritas.setTextColor(if (tabActual == TAB_FAVORITAS) Color.parseColor("#FFFFFF") else Color.parseColor("#666666"))
        btnTabDescargadas.setTextColor(if (tabActual == TAB_DESCARGADAS) Color.parseColor("#FFFFFF") else Color.parseColor("#666666"))

        when (tabActual) {
            TAB_NUBE -> {
                tvOfflineBanner.visibility = View.GONE
                if (etBusqueda.text.toString().trim().isNotEmpty()) {
                    ejecutarBusqueda(etBusqueda, progressBarMain)
                } else {
                    cargarHistorialYRecomendaciones()
                }
            }
            TAB_FAVORITAS -> {
                cargarPestañaFavoritas()
            }
            TAB_DESCARGADAS -> {
                cargarPestañaDescargadas()
            }
        }
    }

    private fun cargarPestañaFavoritas() {
        val favs = LocalMusicManager.obtenerFavoritos(this)
        listaCancionesActuales = favs
        tvSeccionTitulo.text = "⭐ Mis Canciones Favoritas (${favs.size})"
        adapter.actualizarLista(favs)
    }

    private fun cargarPestañaDescargadas() {
        val locales = LocalMusicManager.cargarCancionesLocalesMP3(this)
        listaCancionesActuales = locales
        tvSeccionTitulo.text = "📥 Canciones en Celular / Offline (${locales.size})"
        adapter.actualizarLista(locales)
    }

    private fun activarModoOffline() {
        tvOfflineBanner.visibility = View.VISIBLE
        cambiarPestaña(TAB_FAVORITAS, findViewById(R.id.et_busqueda))
    }

    private fun conectarConServicio() {
        try {
            val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
            controllerFuture.addListener(
                {
                    try {
                        mediaController = controllerFuture.get()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                ContextCompat.getMainExecutor(this)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        PlayerManager.addListener(this)
        actualizarEstadoMiniPlayer()
        if (tabActual == TAB_FAVORITAS) cargarPestañaFavoritas()
        else if (tabActual == TAB_DESCARGADAS) cargarPestañaDescargadas()
    }

    override fun onPause() {
        super.onPause()
        PlayerManager.removeListener(this)
    }

    private fun actualizarEstadoMiniPlayer() {
        val cancion = PlayerManager.currentCancion
        val player = PlayerManager.player

        if (cancion != null && player != null) {
            layoutMiniPlayer.visibility = View.VISIBLE
            tvMiniTitle.text = cancion.titulo ?: "Canción"
            tvMiniArtist.text = cancion.canal ?: "HaroldSound"

            ivMiniThumb.load(cancion.thumbnail) {
                crossfade(true)
                placeholder(android.R.drawable.ic_media_play)
            }

            if (player.isPlaying) {
                btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
        } else {
            layoutMiniPlayer.visibility = View.GONE
        }
    }

    override fun onSongChanged(cancion: Cancion?) {
        actualizarEstadoMiniPlayer()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    override fun onPlaybackReady(durationMs: Long) {}

    private fun ejecutarBusqueda(etBusqueda: EditText, progressBar: ProgressBar) {
        val termino = etBusqueda.text.toString().trim()
        if (termino.isEmpty()) {
            cargarHistorialYRecomendaciones()
            return
        }

        val api = obtenerApiService()
        if (api == null) {
            activarModoOffline()
            return
        }

        buscarEnYouTube(termino, api, progressBar)
    }

    private fun obtenerApiService(): ApiService? {
        var rawUrl = DEFAULT_URL
        if (!rawUrl.endsWith("/")) {
            rawUrl += "/"
        }

        return try {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(rawUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            retrofit.create(ApiService::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buscarEnYouTube(termino: String, api: ApiService, progressBar: ProgressBar) {
        progressBar.visibility = View.VISIBLE
        tvSeccionTitulo.text = "Resultados de búsqueda"
        lifecycleScope.launch {
            try {
                val respuesta = api.buscarCancion(termino)
                if (respuesta.canciones.isNotEmpty()) {
                    tvOfflineBanner.visibility = View.GONE
                    listaCancionesActuales = respuesta.canciones
                    adapter.actualizarLista(respuesta.canciones)
                } else {
                    Toast.makeText(this@MainActivity, "No se encontraron resultados", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activarModoOffline()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun reproducirStreamingDirecto(cancion: Cancion, progressBar: ProgressBar) {
        val urlOriginal = cancion.url
        if (!urlOriginal.isNullOrEmpty()) {
            val encodedUrl = Uri.encode(urlOriginal)
            // Asegurarnos de que DEFAULT_URL termina en /
            val baseUrl = if (DEFAULT_URL.endsWith("/")) DEFAULT_URL else "$DEFAULT_URL/"
            val streamUrl = "${baseUrl}stream?url=$encodedUrl"
            
            val cancionStream = cancion.copy(
                url = urlOriginal // Guardamos la original para poder descargarla después si la hace favorita
            )
            
            Toast.makeText(this, "Conectando al stream...", Toast.LENGTH_SHORT).show()
            guardarEnHistorial(cancionStream)
            PlayerManager.playCancion(this@MainActivity, cancionStream, streamUrl)
            abrirPlayerActivity(cancionStream, streamUrl)
        } else {
            Toast.makeText(this@MainActivity, "URL de canción inválida", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirPlayerActivity(cancion: Cancion, streamUrl: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
            putExtra(PlayerActivity.EXTRA_TITLE, cancion.titulo ?: "Canción")
            putExtra(PlayerActivity.EXTRA_THUMBNAIL, cancion.thumbnail)
            putExtra(PlayerActivity.EXTRA_CANAL, cancion.canal)
        }
        startActivity(intent)
    }

    private fun guardarEnHistorial(cancion: Cancion) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val historyJson = prefs.getString(KEY_HISTORY_JSON, null)

        val listType = object : TypeToken<MutableList<Cancion>>() {}.type
        val historial: MutableList<Cancion> = if (historyJson != null) {
            try {
                gson.fromJson(historyJson, listType)
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }

        historial.removeAll { it.titulo == cancion.titulo || (it.id != null && it.id == cancion.id) }
        historial.add(0, cancion)

        if (historial.size > 20) {
            historial.removeAt(historial.size - 1)
        }

        val updatedJson = gson.toJson(historial)
        prefs.edit().putString(KEY_HISTORY_JSON, updatedJson).apply()
    }

    private fun cargarHistorialYRecomendaciones() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val historyJson = prefs.getString(KEY_HISTORY_JSON, null)

        if (historyJson != null) {
            val listType = object : TypeToken<List<Cancion>>() {}.type
            try {
                val historial: List<Cancion> = gson.fromJson(historyJson, listType)
                if (historial.isNotEmpty()) {
                    listaCancionesActuales = historial
                    tvSeccionTitulo.text = "Escuchadas recientemente"
                    adapter.actualizarLista(historial)
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        tvSeccionTitulo.text = "🔍 ¿Qué quieres escuchar?"
        adapter.actualizarLista(emptyList())
    }
}
