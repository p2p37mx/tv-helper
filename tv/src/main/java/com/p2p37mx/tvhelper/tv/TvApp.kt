package com.p2p37mx.tvhelper.tv

import android.app.Application

class TvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        ServerService.start(this)
    }
}
