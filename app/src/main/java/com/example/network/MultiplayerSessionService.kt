package com.example.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.*

/** Keeps an explicitly opened Wi-Fi room alive when the player minimizes the game. */
class MultiplayerSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var multicast: WifiManager.MulticastLock? = null
    override fun onCreate() {
        super.onCreate()
        val channel = "multiplayer_room"
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(channel, "Wi-Fi multiplayer", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val ru = getSharedPreferences("durak_prefs", MODE_PRIVATE).getString("lang", "RU") == "RU"
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle(if (ru) "Дурачок · комната открыта" else "Dyrachok · room is open")
            .setContentText(if (ru) "Нажмите, чтобы вернуться в игру" else "Tap to return to the game")
            .setContentIntent(pending).setOngoing(true).setSilent(true).build()
        ServiceCompat.startForeground(this, 32, notification, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Dyrachok:Room").apply { setReferenceCounted(false) }
        val manager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wifi = manager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Dyrachok:WiFi").apply { setReferenceCounted(false); acquire() }
        multicast = manager.createMulticastLock("Dyrachok:NSD").apply { setReferenceCounted(false); acquire() }
        scope.launch { while (isActive) { wake?.acquire(600_000); delay(300_000) } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        wake?.let { if (it.isHeld) it.release() }
        wifi?.let { if (it.isHeld) it.release() }
        multicast?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
    companion object {
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, MultiplayerSessionService::class.java)) }
        fun stop(context: Context) { context.stopService(Intent(context, MultiplayerSessionService::class.java)) }
    }
}
