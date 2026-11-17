package com.p2p37mx.tvhelper.tv

import android.service.dreams.DreamService

/**
 * Screensaver with our photos. Pick it on the TV:
 * Settings → Device Preferences → Screen saver → «صور مساعد الشاشة».
 */
class PhotoDream : DreamService() {
    private var stage: Stage? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Store.init(this)
        isInteractive = false
        isFullscreen = true
        val s = Stage(this)
        stage = s
        setContentView(s)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        stage?.start()
    }

    override fun onDreamingStopped() {
        stage?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        stage?.stop()
        stage = null
        super.onDetachedFromWindow()
    }
}
