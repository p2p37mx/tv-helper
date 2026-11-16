package com.p2p37mx.tvhelper.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder

/** Keeps the local server running in the background and announces the TV on the Wi-Fi. */
class ServerService : Service() {

    companion object {
        const val PORT = 8765
        const val SERVICE_TYPE = "_tvhelper._tcp."
        private var server: HttpServer? = null

        fun start(c: Context) {
            val i = Intent(c, ServerService::class.java)
            try {
                c.startForegroundService(i)
            } catch (e: Exception) {
                try { c.startService(i) } catch (_: Exception) {}
            }
        }
    }

    private var nsd: NsdManager? = null
    private var reg: NsdManager.RegistrationListener? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        goForeground()
        if (server == null) server = HttpServer(applicationContext, PORT).also { it.start() }
        announce()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        server?.stop()
        server = null
        try { reg?.let { nsd?.unregisterService(it) } } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = "tvhelper"
        nm.createNotificationChannel(NotificationChannel(ch, getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, ch)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("جاهز يستقبل من التلفون")
            .setSmallIcon(R.drawable.ic_app)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun announce() {
        try {
            val info = NsdServiceInfo().apply {
                serviceName = "TVHelper " + Build.MODEL
                serviceType = SERVICE_TYPE
                port = PORT
            }
            val l = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(s: NsdServiceInfo) {}
                override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) {}
                override fun onServiceUnregistered(s: NsdServiceInfo) {}
                override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
            }
            nsd = getSystemService(NsdManager::class.java)
            nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
            reg = l
        } catch (_: Exception) {}
    }
}
