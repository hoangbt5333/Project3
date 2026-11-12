package com.example.testcamera.data

import android.os.Handler
import android.os.Looper

object KioskSessionManager {

    private const val INACTIVITY_TIMEOUT_MS = 90_000L // 90 seconds timeout

    var currentAccount: UserAccount? = null
        private set

    /**
     * Tương thích ngược với các màn hình sinh viên hiện có
     */
    val currentStudent: Student?
        get() = currentAccount?.studentDetails

    var sessionStartTime: Long = 0L
        private set

    var isSessionActive: Boolean = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var onSessionEndedListener: (() -> Unit)? = null

    private val timeoutRunnable = Runnable {
        endSession()
    }

    /**
     * Bắt đầu phiên làm việc bằng tài khoản đã được phân quyền (STUDENT / ADMIN / TECHNICIAN)
     */
    fun startSession(account: UserAccount) {
        currentAccount = account
        sessionStartTime = System.currentTimeMillis()
        isSessionActive = true
        touchInactivity()
    }

    /**
     * Hàm hỗ trợ tương thích ngược khi truyền trực tiếp đối tượng Student
     */
    fun startSession(student: Student) {
        val account = AccountRepository.findAccountById(student.studentId) ?: UserAccount(
            accountId = student.studentId,
            username = student.studentId,
            displayName = student.fullName,
            role = UserRole.STUDENT,
            status = AccountStatus.ACTIVE,
            email = student.email,
            studentDetails = student
        )
        startSession(account)
    }

    fun touchInactivity() {
        if (!isSessionActive) return
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, INACTIVITY_TIMEOUT_MS)
    }

    fun endSession() {
        handler.removeCallbacks(timeoutRunnable)
        currentAccount = null
        sessionStartTime = 0L
        isSessionActive = false
        onSessionEndedListener?.invoke()
    }

    fun setOnSessionEndedListener(listener: (() -> Unit)?) {
        this.onSessionEndedListener = listener
    }

    fun getCurrentRole(): UserRole? = currentAccount?.role

    fun hasRole(role: UserRole): Boolean = currentAccount?.role == role

    fun isAdmin(): Boolean = currentAccount?.isAdmin() == true

    fun isTechnician(): Boolean = currentAccount?.isTechnician() == true

    fun hasTechAccess(): Boolean = currentAccount?.hasTechAccess() == true
}
