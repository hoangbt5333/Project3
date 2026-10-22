package com.example.testcamera.data

import android.os.Handler
import android.os.Looper

object KioskSessionManager {

    private const val INACTIVITY_TIMEOUT_MS = 90_000L // 90 seconds timeout

    var currentStudent: Student? = null
        private set

    var sessionStartTime: Long = 0L
        private set

    var isSessionActive: Boolean = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var onSessionEndedListener: (() -> Unit)? = null

    private val timeoutRunnable = Runnable {
        endSession()
    }

    fun startSession(student: Student) {
        currentStudent = student
        sessionStartTime = System.currentTimeMillis()
        isSessionActive = true
        touchInactivity()
    }

    fun touchInactivity() {
        if (!isSessionActive) return
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, INACTIVITY_TIMEOUT_MS)
    }

    fun endSession() {
        handler.removeCallbacks(timeoutRunnable)
        currentStudent = null
        sessionStartTime = 0L
        isSessionActive = false
        onSessionEndedListener?.invoke()
    }

    fun setOnSessionEndedListener(listener: (() -> Unit)?) {
        this.onSessionEndedListener = listener
    }
}
