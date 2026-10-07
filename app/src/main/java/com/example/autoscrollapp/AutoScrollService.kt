package com.example.autoscrollapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class AutoScrollService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlay: DragLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var playPauseButton: TextView? = null

    private val scrollRunnable = object : Runnable {
        override fun run() {
            if (ScrollController.isScrolling) {
                performScroll()
                handler.postDelayed(this, ScrollController.intervalMs)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ScrollController.load(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Toast.makeText(this, "Accessibility Service connected!", Toast.LENGTH_LONG).show()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        stopAndHide()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    if (ScrollController.isScrolling) {
                        handler.removeCallbacks(scrollRunnable)
                        handler.postDelayed(scrollRunnable, 500)
                    }
                }
            }
        }
    }

    // ---------- Kontrol scroll ----------

    /** Mulai/lanjutkan scroll dan tampilkan bubble. */
    fun startScrolling() {
        ScrollController.isScrolling = true
        handler.removeCallbacks(scrollRunnable) // cegah dobel jadwal
        handler.post(scrollRunnable)
        showOverlay()
        updateOverlayState()
    }

    /** Pause: scroll berhenti, bubble tetap tampil supaya bisa dilanjutkan. */
    fun pauseScrolling() {
        ScrollController.isScrolling = false
        handler.removeCallbacks(scrollRunnable)
        updateOverlayState()
    }

    /** Stop: scroll berhenti dan bubble disembunyikan. */
    fun stopAndHide() {
        ScrollController.isScrolling = false
        handler.removeCallbacks(scrollRunnable)
        hideOverlay()
    }

    /** Dipanggil saat interval diubah: jadwalkan ulang dengan jeda baru. */
    fun applyNewInterval() {
        if (ScrollController.isScrolling) {
            handler.removeCallbacks(scrollRunnable)
            handler.postDelayed(scrollRunnable, ScrollController.intervalMs)
        }
    }

    private fun performScroll() {
        performGestureScroll()
    }

    private fun performGestureScroll() {
        val displayMetrics = resources.displayMetrics
        val middleX = displayMetrics.widthPixels / 2
        val startY = displayMetrics.heightPixels * 0.75f
        val endY = displayMetrics.heightPixels * 0.25f

        val path = Path().apply {
            moveTo(middleX.toFloat(), startY)
            lineTo(middleX.toFloat(), endY)
        }

        val gestureDescription = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 200))
            .build()

        dispatchGesture(gestureDescription, null, null)
    }

    // ---------- Bubble / overlay ----------

    private fun showOverlay() {
        if (overlay != null) return
        val wm = windowManager ?: return

        val density = resources.displayMetrics.density
        val pad = (10 * density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            // Overlay khusus accessibility service: tidak butuh izin "draw over other apps"
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - (140 * density).toInt() // dekat tepi kanan
            y = (160 * density).toInt()
        }

        val container = DragLayout(this, wm, params).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40 * density
                setColor(Color.parseColor("#CC222222"))
            }
            setPadding(pad, pad, pad, pad)
        }

        fun makeButton(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val size = (44 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { onClick() }
        }

        val playPause = makeButton("⏸") {
            if (ScrollController.isScrolling) pauseScrolling() else startScrolling()
        }
        val stop = makeButton("■") { stopAndHide() }

        container.addView(playPause)
        container.addView(stop)

        playPauseButton = playPause
        overlay = container
        overlayParams = params
        wm.addView(container, params)
    }

    private fun hideOverlay() {
        overlay?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        overlay = null
        overlayParams = null
        playPauseButton = null
    }

    private fun updateOverlayState() {
        playPauseButton?.text = if (ScrollController.isScrolling) "⏸" else "▶"
    }

    /** Container yang bisa digeser (drag) tanpa mengganggu klik tombol di dalamnya. */
    private class DragLayout(
        context: Context,
        private val wm: WindowManager,
        private val params: WindowManager.LayoutParams
    ) : LinearLayout(context) {

        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (!dragging && (dx * dx + dy * dy) > touchSlop * touchSlop) {
                        dragging = true
                    }
                }
            }
            return dragging
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    params.x = startX + (ev.rawX - downRawX).toInt()
                    params.y = startY + (ev.rawY - downRawY).toInt()
                    wm.updateViewLayout(this, params)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return true
        }
    }

    companion object {
        var instance: AutoScrollService? = null
    }
}
