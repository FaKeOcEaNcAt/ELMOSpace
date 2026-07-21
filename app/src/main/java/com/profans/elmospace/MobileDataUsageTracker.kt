package com.profans.elmospace

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MobileDataUsageTracker {
    private const val PREFS_NAME = "mobile_data_usage"
    private const val BUCKET_PREFIX = "hour_"
    private const val SAMPLE_INTERVAL_MS = 15_000L
    private const val RETENTION_DAYS = 62

    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var appContext: Context? = null
    private var connectivityManager: ConnectivityManager? = null
    private var lastTotalBytes = 0L
    private var wasCellular = false

    private val sampler = object : Runnable {
        override fun run() {
            sampleNow()
            handler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            synchronized(lock) {
                sampleLocked()
                wasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            }
        }

        override fun onLost(network: Network) {
            synchronized(lock) {
                sampleLocked()
                wasCellular = false
            }
        }
    }

    fun start(context: Context) {
        synchronized(lock) {
            if (started) return
            started = true
            appContext = context.applicationContext
            connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            lastTotalBytes = readUidTotalBytes()
            wasCellular = isActiveNetworkCellular()
            pruneOldBuckets()
            runCatching { connectivityManager?.registerDefaultNetworkCallback(networkCallback) }
            handler.postDelayed(sampler, SAMPLE_INTERVAL_MS)
        }
    }

    fun sampleNow() {
        synchronized(lock) { sampleLocked() }
    }

    fun hourlyBytes(day: Calendar): LongArray {
        sampleNow()
        val prefix = SimpleDateFormat("yyyyMMdd", Locale.US).format(day.time)
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return LongArray(24)
        return LongArray(24) { hour ->
            prefs.getLong("$BUCKET_PREFIX${prefix}_${hour.toString().padStart(2, '0')}", 0L)
        }
    }

    fun dailyBytes(days: List<Calendar>): LongArray {
        sampleNow()
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return LongArray(days.size)
        val formatter = SimpleDateFormat("yyyyMMdd", Locale.US)
        return LongArray(days.size) { index ->
            val prefix = "$BUCKET_PREFIX${formatter.format(days[index].time)}_"
            prefs.all.entries.sumOf { entry ->
                if (entry.key.startsWith(prefix)) (entry.value as? Long ?: 0L) else 0L
            }
        }
    }

    private fun sampleLocked() {
        val currentTotal = readUidTotalBytes()
        if (currentTotal < 0L) return
        if (lastTotalBytes <= 0L || currentTotal < lastTotalBytes) {
            lastTotalBytes = currentTotal
            return
        }
        val delta = currentTotal - lastTotalBytes
        lastTotalBytes = currentTotal
        if (!wasCellular || delta <= 0L) return

        val context = appContext ?: return
        val key = BUCKET_PREFIX + SimpleDateFormat("yyyyMMdd_HH", Locale.US).format(Date())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(key, prefs.getLong(key, 0L) + delta).apply()
    }

    private fun readUidTotalBytes(): Long {
        val rx = TrafficStats.getUidRxBytes(android.os.Process.myUid())
        val tx = TrafficStats.getUidTxBytes(android.os.Process.myUid())
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            return -1L
        }
        return rx + tx
    }

    private fun isActiveNetworkCellular(): Boolean {
        val manager = connectivityManager ?: return false
        val activeNetwork = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    }

    private fun pruneOldBuckets() {
        val context = appContext ?: return
        val threshold = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -RETENTION_DAYS)
        }
        val thresholdKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(threshold.time)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            val date = key.removePrefix(BUCKET_PREFIX).take(8)
            if (key.startsWith(BUCKET_PREFIX) && date.length == 8 && date < thresholdKey) {
                editor.remove(key)
            }
        }
        editor.apply()
    }
}
