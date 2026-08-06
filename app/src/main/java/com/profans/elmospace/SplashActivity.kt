package com.profans.elmospace

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout

class SplashActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppTheme.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_splash)
        positionSplashContent()
        applyAccentColor()
        if (AppPreferences.isSplashAnimationEnabled(this)) {
            playSplashAnimation()
            handler.postDelayed({ openMainActivity(useTransition = true) }, SPLASH_DURATION_MS)
        } else {
            openMainActivity(useTransition = false)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun positionSplashContent() {
        val root = findViewById<View>(R.id.splashRoot)
        val content = findViewById<View>(R.id.splashContent)
        root.doOnLayout {
            content.translationY = it.height * 0.3f
        }
    }

    private fun applyAccentColor() {
        val accent = AppAccentColor.color(this)
        findViewById<TextView>(R.id.splashCaption).setTextColor(accent)
        findViewById<View>(R.id.splashAccentLine).setBackgroundColor(accent)
    }

    private fun playSplashAnimation() {
        val caption = findViewById<View>(R.id.splashCaption)
        val line = findViewById<View>(R.id.splashAccentLine)

        caption.alpha = 0f
        caption.translationY = 16f
        caption.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(360L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        line.scaleX = 0f
        line.animate()
            .scaleX(1f)
            .setStartDelay(120L)
            .setDuration(420L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun openMainActivity(useTransition: Boolean) {
        if (isFinishing || isDestroyed) return
        val next = Intent(this, MainActivity::class.java).apply {
            action = intent.action
            data = intent.data
            if (intent.extras != null) putExtras(intent)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        if (useTransition) {
            val options = ActivityOptions.makeCustomAnimation(
                this,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            startActivity(next, options.toBundle())
        } else {
            startActivity(next)
            overridePendingTransition(0, 0)
        }
        finish()
    }

    companion object {
        private const val SPLASH_DURATION_MS = 720L
    }
}
