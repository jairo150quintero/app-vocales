package com.example.myapplication

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class StartActivity : AppCompatActivity() {
    
    private var introPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_start)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. Audio de introducción automático
        playIntro()

        findViewById<Button>(R.id.startButton).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        findViewById<TextView>(R.id.creditsButton).setOnClickListener {
            showCreditsDialog()
        }
    }

    private fun playIntro() {
        try {
            introPlayer = MediaPlayer.create(this, R.raw.introduccion)
            introPlayer?.isLooping = true
            introPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showCreditsDialog() {
        val message = """
            ${getString(R.string.credits_owner)}
            
            ${getString(R.string.credits_designer)}
            
            ${getString(R.string.credits_authors)}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(R.string.credits_title)
            .setMessage(message)
            .setPositiveButton(R.string.btn_close, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (introPlayer == null) playIntro() else introPlayer?.start()
    }

    override fun onPause() {
        super.onPause()
        introPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        introPlayer?.release()
        introPlayer = null
    }
}
