package com.example.screendimmer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat

/**
 * A foreground Service that owns its own always-on-top overlay window.
 * Because the window belongs to the Service (not the Activity), it keeps
 * running — and keeps dimming the screen — even after MainActivity is
 * closed or the user presses Home to switch to another app.
 */
class DimOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: FrameLayout? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): DimOverlayService = this@DimOverlayService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        addOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val level = intent?.getIntExtra(MainActivity.EXTRA_LEVEL, MainActivity.DEFAULT_LEVEL)
            ?: MainActivity.DEFAULT_LEVEL
        setDimLevel(level)
        // START_STICKY: ask the system to recreate the service if it gets
        // killed under memory pressure, so dimming resumes automatically.
        return START_STICKY
    }

    private fun addOverlay() {
        if (overlayView != null) return

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // NOT_FOCUSABLE + NOT_TOUCHABLE = purely visual layer.
            // The remote's D-pad / OK button still control whatever app is underneath.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        val view = FrameLayout(this)
        view.setBackgroundColor(Color.BLACK)
        view.alpha = 0f

        windowManager.addView(view, params)
        overlayView = view
    }

    /** level is 0-90 (percent black overlay opacity). Callable while running. */
    fun setDimLevel(level: Int) {
        val clamped = level.coerceIn(0, MainActivity.MAX_DIM)
        overlayView?.alpha = clamped / 100f
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Dimmer active")
            .setContentText("Tap to reopen and adjust")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        android.app.PendingIntent.FLAG_IMMUTABLE
                    else 0
                )
            )
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Dimmer Service", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    companion object {
        const val CHANNEL_ID = "dimmer_channel"
        const val NOTIF_ID = 1001
    }
}
