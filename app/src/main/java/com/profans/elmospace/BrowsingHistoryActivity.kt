package com.profans.elmospace

import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class BrowsingHistoryActivity : ComponentActivity() {
    private lateinit var filterValue: TextView
    private lateinit var resultCount: TextView
    private lateinit var emptyState: TextView
    private lateinit var historyList: ListView
    private lateinit var adapter: HistoryAdapter

    private val databaseExecutor = Executors.newSingleThreadExecutor()
    private var selectedFilter = HistoryFilter.ALL
    private var customStart = 0L
    private var customEndExclusive = 0L
    private var activeDialog: Dialog? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppTheme.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_browsing_history)
        applyInsets()

        filterValue = findViewById(R.id.historyFilterValue)
        resultCount = findViewById(R.id.historyResultCount)
        emptyState = findViewById(R.id.historyEmpty)
        historyList = findViewById(R.id.historyList)
        adapter = HistoryAdapter(this)
        historyList.adapter = adapter

        findViewById<View>(R.id.historyBack).setOnClickListener { finishWithTransition() }
        findViewById<View>(R.id.historyFilterRow).setOnClickListener { showFilterPicker() }
        findViewById<View>(R.id.historyClear).setOnClickListener { confirmClearHistory() }
        historyList.setOnItemClickListener { _, _, position, _ ->
            openHistoryEntry(adapter.getItem(position))
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishWithTransition()
        })

        updateFilterLabel()
        loadHistory()
    }

    override fun onDestroy() {
        activeDialog?.dismiss()
        activeDialog = null
        databaseExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun showFilterPicker() {
        val options = arrayOf(
            getString(R.string.history_filter_all),
            getString(R.string.history_filter_today),
            getString(R.string.history_filter_last_seven_days),
            getString(R.string.history_filter_custom),
            getString(R.string.history_filter_liked)
        )
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.history_filter_title)
            .setSingleChoiceItems(options, selectedFilter.ordinal) { dialog, index ->
                dialog.dismiss()
                val filter = HistoryFilter.entries[index]
                if (filter == HistoryFilter.CUSTOM) {
                    showCustomStartDatePicker()
                } else {
                    selectedFilter = filter
                    updateFilterLabel()
                    loadHistory()
                }
            }
            .setNegativeButton(R.string.permission_cancel, null)
            .create()
        showManagedDialog(dialog)
    }

    private fun showCustomStartDatePicker() {
        val initial = Calendar.getInstance().apply {
            if (customStart > 0L) timeInMillis = customStart
        }
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val start = startOfDay(year, month, day)
                showCustomEndDatePicker(start)
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        )
        showManagedDialog(dialog)
    }

    private fun showCustomEndDatePicker(start: Long) {
        val initial = Calendar.getInstance().apply {
            timeInMillis = if (customEndExclusive > start) {
                customEndExclusive - 1L
            } else {
                start
            }
        }
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                customStart = start
                val selectedEnd = startOfDay(year, month, day).coerceAtLeast(start)
                customEndExclusive = nextDay(selectedEnd)
                selectedFilter = HistoryFilter.CUSTOM
                updateFilterLabel()
                loadHistory()
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = start
        }
        showManagedDialog(dialog)
    }

    private fun loadHistory() {
        val range = selectedFilter.range(customStart, customEndExclusive)
        databaseExecutor.execute {
            val entries = BrowsingHistoryRepository.query(
                this,
                range.first,
                range.second,
                likedOnly = selectedFilter == HistoryFilter.LIKED
            )
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                adapter.submit(entries)
                resultCount.text = getString(R.string.history_result_count, entries.size)
                emptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
                historyList.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun confirmClearHistory() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.history_clear_title)
            .setMessage(R.string.history_clear_message)
            .setPositiveButton(R.string.history_clear_confirm) { _, _ ->
                databaseExecutor.execute {
                    BrowsingHistoryRepository.clear(this)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) loadHistory()
                    }
                }
            }
            .setNegativeButton(R.string.permission_cancel, null)
            .create()
        showManagedDialog(dialog)
    }

    private fun showManagedDialog(dialog: Dialog) {
        activeDialog?.dismiss()
        activeDialog = dialog
        dialog.setOnDismissListener {
            if (activeDialog === dialog) activeDialog = null
        }
        dialog.show()
    }

    private fun openHistoryEntry(entry: BrowsingHistoryEntry) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_URL, entry.url)
        val options = ActivityOptions.makeCustomAnimation(
            this,
            R.anim.settings_exit,
            R.anim.activity_hold
        )
        startActivity(intent, options.toBundle())
        finish()
    }

    private fun updateFilterLabel() {
        filterValue.text = when (selectedFilter) {
            HistoryFilter.ALL -> getString(R.string.history_filter_all)
            HistoryFilter.TODAY -> getString(R.string.history_filter_today)
            HistoryFilter.LAST_SEVEN_DAYS -> getString(R.string.history_filter_last_seven_days)
            HistoryFilter.LIKED -> getString(R.string.history_filter_liked)
            HistoryFilter.CUSTOM -> {
                val formatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                getString(
                    R.string.history_filter_custom_value,
                    formatter.format(Date(customStart)),
                    formatter.format(Date(customEndExclusive - 1L))
                )
            }
        }
    }

    private fun startOfDay(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun nextDay(dayStart: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

    private fun applyInsets() {
        val root = findViewById<View>(R.id.historyRoot)
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

    private enum class HistoryFilter {
        ALL,
        TODAY,
        LAST_SEVEN_DAYS,
        CUSTOM,
        LIKED;

        fun range(customStart: Long, customEndExclusive: Long): Pair<Long?, Long?> {
            val todayCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val today = todayCalendar.timeInMillis
            val tomorrow = (todayCalendar.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
            val sevenDayStart = (todayCalendar.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -6)
            }.timeInMillis
            return when (this) {
                ALL -> null to null
                TODAY -> today to tomorrow
                LAST_SEVEN_DAYS -> sevenDayStart to tomorrow
                CUSTOM -> customStart to customEndExclusive
                LIKED -> null to null
            }
        }
    }

    private class HistoryAdapter(private val context: Context) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)
        private val timeFormatter =
            SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault())
        private var entries = emptyList<BrowsingHistoryEntry>()

        fun submit(newEntries: List<BrowsingHistoryEntry>) {
            entries = newEntries
            notifyDataSetChanged()
        }

        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun getItemId(position: Int) = entries[position].topicId

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: View
            val holder: ViewHolder
            if (convertView == null) {
                view = inflater.inflate(R.layout.item_browsing_history, parent, false)
                holder = ViewHolder(
                    time = view.findViewById(R.id.historyItemTime),
                    title = view.findViewById(R.id.historyItemTitle),
                    metadata = view.findViewById(R.id.historyItemMetadata)
                )
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as ViewHolder
            }

            val entry = getItem(position)
            holder.time.text = timeFormatter.format(Date(entry.lastReadAt))
            holder.title.text = entry.title
            holder.metadata.text = if (entry.viewCount >= 0L) {
                context.getString(
                    R.string.history_item_metadata,
                    entry.author,
                    entry.viewCount
                )
            } else {
                context.getString(R.string.history_item_metadata_unknown_views, entry.author)
            }
            return view
        }
    }

    private data class ViewHolder(
        val time: TextView,
        val title: TextView,
        val metadata: TextView
    )

}
