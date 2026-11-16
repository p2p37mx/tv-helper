package com.p2p37mx.tvhelper.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ServerService.start(context)
    }
}
