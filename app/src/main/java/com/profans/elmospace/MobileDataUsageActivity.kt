package com.profans.elmospace

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MobileDataUsageActivity : ComponentActivity() {
    private lateinit var chart: MobileDataUsageChartView
    private lateinit var unitButton: TextView
    private lateinit var periodButtons: List<TextView>
    private var selectedPeriod = Period.TODAY
    private var selectedUnit = UsageUnit.MB

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppTheme.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowLayout.lockPhonePortrait(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_mobile_data_usage)
        applyInsets()

        chart = findViewById(R.id.mobileDataChart)
        unitButton = findViewById(R.id.usageUnitButton)
        periodButtons = listOf(
            findViewById(R.id.usageToday),
            findViewById(R.id.usageFiveDays),
            findViewById(R.id.usageMonth)
        )

        findViewById<View>(R.id.usageBack).setOnClickListener { finishWithTransition() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishWithTransition()
        })
        periodButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                selectedPeriod = Period.entries[index]
                refreshChart()
            }
        }
        unitButton.setOnClickListener {
            selectedUnit = UsageUnit.entries[(selectedUnit.ordinal + 1) % UsageUnit.entries.size]
            refreshChart()
        }
        refreshChart()
    }

    override fun onResume() {
        super.onResume()
        if (::chart.isInitialized) refreshChart()
    }

    private fun refreshChart() {
        val data = buildPeriodData(selectedPeriod)
        chart.submitData(data.values, data.labels, selectedUnit)
        unitButton.text = selectedUnit.label
        periodButtons.forEachIndexed { index, button ->
            val selected = index == selectedPeriod.ordinal
            button.setTextColor(
                if (selected) AppAccentColor.color(this)
                else ContextCompat.getColor(this, R.color.nav_unselected)
            )
            button.alpha = if (selected) 1f else 0.72f
        }
    }

    private fun buildPeriodData(period: Period): PeriodData {
        val now = Calendar.getInstance()
        return when (period) {
            Period.TODAY -> PeriodData(
                MobileDataUsageTracker.hourlyBytes(now),
                List(24) { hour -> String.format(Locale.getDefault(), "%02d时", hour) }
            )
            Period.FIVE_DAYS -> {
                val days = (4 downTo 0).map { offset ->
                    (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
                }
                PeriodData(
                    MobileDataUsageTracker.dailyBytes(days),
                    days.map { SimpleDateFormat("M/d", Locale.getDefault()).format(it.time) }
                )
            }
            Period.MONTH -> {
                val days = (1..now.get(Calendar.DAY_OF_MONTH)).map { day ->
                    (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
                }
                PeriodData(
                    MobileDataUsageTracker.dailyBytes(days),
                    days.map { "${it.get(Calendar.DAY_OF_MONTH)}日" }
                )
            }
        }
    }

    private fun applyInsets() {
        val root = findViewById<View>(R.id.mobileDataUsageRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    @Suppress("DEPRECATION")
    private fun finishWithTransition() {
        finish()
        overridePendingTransition(R.anim.activity_hold, R.anim.settings_exit)
    }

    private enum class Period { TODAY, FIVE_DAYS, MONTH }
    private data class PeriodData(val values: LongArray, val labels: List<String>)
}
