package com.example.testcamera.data

enum class UserRole(val displayName: String) {
    STUDENT("Sinh viên"),
    ADMIN("Quản trị viên"),
    TECHNICIAN("Kỹ thuật viên")
}

enum class AccountStatus(val description: String) {
    ACTIVE("Đang hoạt động"),
    DISABLED("Bị vô hiệu hóa")
}

data class UserAccount(
    val accountId: String,
    val username: String,
    val displayName: String,
    val role: UserRole,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val email: String? = null,
    val studentDetails: Student? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun isActive(): Boolean = status == AccountStatus.ACTIVE

    fun isStudent(): Boolean = role == UserRole.STUDENT

    fun isAdmin(): Boolean = role == UserRole.ADMIN

    fun isTechnician(): Boolean = role == UserRole.TECHNICIAN

    /**
     * Admin kiêm quyền Technician trong giai đoạn thử nghiệm/demo của Smart Kiosk
     */
    fun hasTechAccess(): Boolean = role == UserRole.ADMIN || role == UserRole.TECHNICIAN

    fun getRoleBadgeText(): String = when (role) {
        UserRole.STUDENT -> "🎓 SINH VIÊN"
        UserRole.ADMIN -> "🛡️ ADMIN"
        UserRole.TECHNICIAN -> "⚙️ KỸ THUẬT"
    }
}
