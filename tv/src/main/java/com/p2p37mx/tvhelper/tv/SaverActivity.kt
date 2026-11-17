package com.p2p37mx.tvhelper.tv

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent

/** Our screensaver: the photo stage full screen. Any button closes it and you're back where you were. */
class SaverActivity : Activity() {
    companion object {
        @Volatile
        var showing = false
    }

    private lateinit var stage: Stage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        stage = Stage(this)
        stage.bottomShade.alpha = 0f
        setContentView(stage)
    }

    override fun onResume() {
        super.onResume()
        showing = true
        stage.start()
    }

    override fun onPause() {
        stage.stop()
        showing = false
        super.onPause()
        if (!isFinishing) finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val k = event.keyCode
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            Store.lastActivity = System.currentTimeMillis()
            finish()
        }
        return true
    }
}
