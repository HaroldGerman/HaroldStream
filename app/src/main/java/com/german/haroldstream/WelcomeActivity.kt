package com.german.haroldstream

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    private val PREFS_NAME = "HaroldSoundPrefs"
    private val KEY_FIRST_TIME = "is_first_time"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstTime = prefs.getBoolean(KEY_FIRST_TIME, true)

        if (!isFirstTime) {
            // Ya no es primera vez, ir directo a MainActivity sin mostrar splash
            irAMain()
            return
        }

        // Es primera vez, mostrar pantalla de bienvenida
        setContentView(R.layout.activity_welcome)

        // Marcar que ya se mostró
        prefs.edit().putBoolean(KEY_FIRST_TIME, false).apply()

        // Esperar 3.5 segundos y luego ir a MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            irAMain()
        }, 3500)
    }

    private fun irAMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
