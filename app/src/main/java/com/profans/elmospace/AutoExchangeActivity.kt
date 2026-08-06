package com.profans.elmospace

import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoExchangeActivity : ComponentActivity() {
    private lateinit var scoreText: TextView
    private lateinit var lastSyncText: TextView
    private lateinit var syncButton: TextView
    private lateinit var list: LinearLayout
    private var items: List<ExchangeItem> = emptyList()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppTheme.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_auto_exchange)
        applyInsets()

        scoreText = findViewById(R.id.autoExchangeScore)
        lastSyncText = findViewById(R.id.autoExchangeLastSync)
        syncButton = findViewById(R.id.autoExchangeSyncButton)
        list = findViewById(R.id.autoExchangeList)

        findViewById<View>(R.id.autoExchangeBack).setOnClickListener { finishWithTransition() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishWithTransition()
        })
        syncButton.setOnClickListener { syncNow() }

        loadCachedItems()
        render()
    }

    private fun loadCachedItems() {
        items = NativeExchangeClient.itemsFromJson(
            AppPreferences.getAutoExchangeLastSyncItems(this)
        )
    }

    private fun syncNow() {
        syncButton.isEnabled = false
        syncButton.alpha = 0.56f
        syncButton.setText(R.string.auto_exchange_syncing)
        Thread {
            val result = NativeExchangeClient.sync(applicationContext)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                syncButton.isEnabled = true
                syncButton.alpha = 1f
                syncButton.setText(R.string.auto_exchange_sync_now)
                when (result) {
                    NativeExchangeClient.SyncResult.LoginInvalid -> {
                        Toast.makeText(
                            this,
                            R.string.auto_exchange_sync_login_invalid,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    NativeExchangeClient.SyncResult.InterfaceUnavailable -> {
                        Toast.makeText(
                            this,
                            R.string.auto_exchange_sync_interface_unavailable,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is NativeExchangeClient.SyncResult.Success -> {
                        items = result.data.items
                        AppPreferences.setAutoExchangeLastSyncItems(
                            this,
                            NativeExchangeClient.itemsToJson(items)
                        )
                        scoreText.text = getString(
                            R.string.auto_exchange_score_value,
                            result.data.score
                        )
                        Toast.makeText(
                            this,
                            R.string.auto_exchange_sync_success,
                            Toast.LENGTH_SHORT
                        ).show()
                        render()
                    }
                }
            }
        }.start()
    }

    private fun render() {
        updateLastSyncText()
        list.removeAllViews()
        if (items.isEmpty()) {
            scoreText.setText(R.string.auto_exchange_score_unknown)
            list.addView(createEmptyText())
            return
        }
        items.forEachIndexed { index, item ->
            if (index > 0) list.addView(createDivider())
            list.addView(createItemRow(item))
        }
    }

    private fun updateLastSyncText() {
        val lastSync = AppPreferences.getAutoExchangeLastSyncTime(this)
        lastSyncText.text = if (lastSync <= 0L) {
            getString(R.string.auto_exchange_never_sync)
        } else {
            getString(
                R.string.auto_exchange_last_sync_format,
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSync))
            )
        }
    }

    private fun createItemRow(item: ExchangeItem): View {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * density).toInt(),
                (14 * density).toInt(),
                (16 * density).toInt(),
                (14 * density).toInt()
            )
        }
        val selectedIds = AppPreferences.getAutoExchangeSelectedIds(this)
        val selected = item.exchangeId in selectedIds

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = item.displayName
            setTextColor(ContextCompat.getColor(this@AutoExchangeActivity, R.color.error_text))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        titleRow.addView(Switch(this).apply {
            isChecked = selected
            AppAccentColor.tintSwitch(this, this@AutoExchangeActivity)
            setOnCheckedChangeListener { button, checked ->
                if (checked && item.cycle in setOf("month", "life")) {
                    button.isChecked = false
                    confirmLongCycleSelection(item)
                } else {
                    updateSelection(item.exchangeId, checked)
                }
            }
        })
        row.addView(titleRow)

        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * density).toInt() }
            text = getString(
                R.string.auto_exchange_item_meta,
                item.useScore,
                item.cycleLabel,
                item.remainingCount,
                item.maxExchangeCount
            )
            setTextColor(ContextCompat.getColor(this@AutoExchangeActivity, R.color.nav_unselected))
            textSize = 12f
        })

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = if (selected) View.VISIBLE else View.GONE
        }
        actionRow.addView(TextView(this).apply {
            text = getString(
                R.string.auto_exchange_priority_value,
                selectedIds.indexOf(item.exchangeId) + 1
            )
            setTextColor(AppAccentColor.color(this@AutoExchangeActivity))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        actionRow.addView(createOrderButton("↑") { moveSelected(item.exchangeId, -1) })
        actionRow.addView(createOrderButton("↓") { moveSelected(item.exchangeId, 1) })
        row.addView(actionRow)

        return row
    }

    private fun createOrderButton(textValue: String, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            text = textValue
            gravity = android.view.Gravity.CENTER
            setTextColor(AppAccentColor.color(this@AutoExchangeActivity))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = ContextCompat.getDrawable(this@AutoExchangeActivity, R.drawable.bg_permission_action)
            layoutParams = LinearLayout.LayoutParams((42 * density).toInt(), (34 * density).toInt())
                .apply { leftMargin = (8 * density).toInt() }
            setOnClickListener { onClick() }
        }
    }

    private fun confirmLongCycleSelection(item: ExchangeItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.auto_exchange_long_cycle_title)
            .setMessage(getString(R.string.auto_exchange_long_cycle_message, item.displayName, item.cycleLabel))
            .setPositiveButton(R.string.permission_confirm) { _, _ ->
                updateSelection(item.exchangeId, true)
            }
            .setNegativeButton(R.string.permission_cancel, null)
            .show()
    }

    private fun updateSelection(exchangeId: Int, selected: Boolean) {
        val ids = AppPreferences.getAutoExchangeSelectedIds(this).toMutableList()
        if (selected) {
            if (exchangeId !in ids) ids += exchangeId
        } else {
            ids.remove(exchangeId)
        }
        AppPreferences.setAutoExchangeSelectedIds(this, ids)
        render()
    }

    private fun moveSelected(exchangeId: Int, offset: Int) {
        val ids = AppPreferences.getAutoExchangeSelectedIds(this).toMutableList()
        val index = ids.indexOf(exchangeId)
        val target = (index + offset).coerceIn(0, ids.lastIndex)
        if (index < 0 || index == target) return
        ids.removeAt(index)
        ids.add(target, exchangeId)
        AppPreferences.setAutoExchangeSelectedIds(this, ids)
        render()
    }

    private fun createEmptyText() =
        TextView(this).apply {
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
            setText(R.string.auto_exchange_empty)
            setTextColor(ContextCompat.getColor(this@AutoExchangeActivity, R.color.nav_unselected))
            textSize = 13f
        }

    private fun createDivider(): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply { leftMargin = 16.dp() }
            setBackgroundColor(ContextCompat.getColor(this@AutoExchangeActivity, R.color.nav_divider))
        }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    private fun applyInsets() {
        val root = findViewById<View>(R.id.autoExchangeRoot)
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
}
