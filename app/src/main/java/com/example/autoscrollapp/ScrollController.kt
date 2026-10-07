package com.example.autoscrollapp

import android.content.Context

object ScrollController {
    const val MIN_INTERVAL_SEC = 0.5f
    const val MAX_INTERVAL_SEC = 3600f
    const val DEFAULT_INTERVAL_SEC = 1f

    private const val PREFS = "autoscroll_prefs"
    // Key baru (float). Key lama "interval_sec" bertipe Int, jadi tidak dipakai lagi agar tidak crash.
    private const val KEY_INTERVAL = "interval_sec_f"

    @Volatile var isScrolling = false

    /** Jeda antar scroll, dalam detik (boleh desimal, mis. 2.5). */
    @Volatile var intervalSec: Float = DEFAULT_INTERVAL_SEC
        private set

    val intervalMs: Long get() = (intervalSec * 1000f).toLong()

    fun load(context: Context) {
        intervalSec = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_INTERVAL, DEFAULT_INTERVAL_SEC)
            .coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
    }

    fun setInterval(context: Context, seconds: Float) {
        intervalSec = seconds.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_INTERVAL, intervalSec)
            .apply()
    }

    /** 2.0 -> "2", 2.5 -> "2.5" */
    fun format(seconds: Float): String =
        if (seconds % 1f == 0f) seconds.toInt().toString() else seconds.toString()
}
