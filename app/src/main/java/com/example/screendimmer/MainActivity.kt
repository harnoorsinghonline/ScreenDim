package com.example.screendimmer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var dimService: DimOverlayService? = null
    private var bound = false
    private lateinit var prefs: SharedPreferences

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DimOverlayService.LocalBinder
            dimService = binder.getService()
            bound = true
            dimService?.setDimLevel(prefs.getInt(KEY_LEVEL, DEFAULT_LEVEL))
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            dimService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val seekBar = findViewById<SeekBar>(R.id.seekBarDim)
        val levelText = findViewById<TextView>(R.id.textLevel)
        val savedLevel = prefs.getInt(KEY_LEVEL, DEFAULT_LEVEL)

        seekBar.max = MAX_DIM
        seekBar.progress = savedLevel
        levelText.text = getString(R.string.dim_level_fmt, savedLevel)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!hasOverlayPermission()) {
                requestOverlayPermission()
            } else {
                startDimService(seekBar.progress)
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopDimService()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                levelText.text = getString(R.string.dim_level_fmt, progress)
                prefs.edit().putInt(KEY_LEVEL, progress).apply()
                if (bound) dimService?.setDimLevel(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // If the service is already running (e.g. app reopened), bind to it
        // so the slider reflects/controls the live overlay.
        if (isServiceRunning()) {
            bindService(Intent(this, DimOverlayService::class.java), connection, 0)
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, getString(R.string.overlay_perm_needed), Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startDimService(level: Int) {
        val intent = Intent(this, DimOverlayService::class.java)
        intent.putExtra(EXTRA_LEVEL, level)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        if (!bound) {
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun stopDimService() {
        if (bound) {
            unbindService(connection)
            bound = false
            dimService = null
        }
        stopService(Intent(this, DimOverlayService::class.java))
    }

    private fun isServiceRunning(): Boolean {
        @Suppress("DEPRECATION")
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (DimOverlayService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unbind only — do NOT stop the service here.
        // This is what lets dimming survive after the activity/UI is closed.
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    companion object {
        const val PREFS_NAME = "dimmer_prefs"
        const val KEY_LEVEL = "dim_level"
        const val DEFAULT_LEVEL = 40
        const val MAX_DIM = 90
        const val EXTRA_LEVEL = "extra_level"
    }
}
