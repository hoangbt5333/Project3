package com.example.testcamera.data

object AccountRepository {

    // Danh sách tài khoản trong bộ nhớ (hợp nhất Sinh viên, Admin, Technician)
    private val accountList = mutableListOf<UserAccount>()
    private var isInitialized = false

    init {
        ensureInitialized()
    }

    @Synchronized
    private fun ensureInitialized() {
        if (isInitialized) return

        accountList.clear()

        // 1. Tạo tài khoản Admin mẫu an toàn
        accountList.add(
            UserAccount(
                accountId = "ADMIN_01",
                username = "admin",
                displayName = "Quản trị viên Kiosk",
                role = UserRole.ADMIN,
                status = AccountStatus.ACTIVE,
                email = "admin.kiosk@hust.edu.vn"
            )
        )

        // 2. Tạo tài khoản Technician mẫu an toàn
        accountList.add(
            UserAccount(
                accountId = "TECH_01",
                username = "technician",
                displayName = "Kỹ thuật viên Kiosk",
                role = UserRole.TECHNICIAN,
                status = AccountStatus.ACTIVE,
                email = "tech.kiosk@hust.edu.vn"
            )
        )

        // 3. Tự động đồng bộ toàn bộ sinh viên từ StudentRepository
        val allStudents = StudentRepository.getAllStudents()
        for (st in allStudents) {
            // Mẫu thử nghiệm: Đặt tài khoản sinh viên 20231012 là DISABLED để kiểm thử kịch bản vô hiệu hóa
            val initialStatus = if (st.studentId == "20231012") {
                AccountStatus.DISABLED
            } else {
                AccountStatus.ACTIVE
            }

            accountList.add(
                UserAccount(
                    accountId = st.studentId,
                    username = st.studentId,
                    displayName = st.fullName,
                    role = UserRole.STUDENT,
                    status = initialStatus,
                    email = st.email,
                    studentDetails = st
                )
            )
        }

        isInitialized = true
    }

    fun getAllAccounts(): List<UserAccount> {
        ensureInitialized()
        return accountList.toList()
    }

    fun findAccountById(accountId: String): UserAccount? {
        ensureInitialized()
        val cleanId = accountId.trim()
        return accountList.firstOrNull { it.accountId.equals(cleanId, ignoreCase = true) }
    }

    fun findAccountByUsername(username: String): UserAccount? {
        ensureInitialized()
        val cleanUsername = username.trim()
        return accountList.firstOrNull { it.username.equals(cleanUsername, ignoreCase = true) }
    }

    fun getAccountsByRole(role: UserRole): List<UserAccount> {
        ensureInitialized()
        return accountList.filter { it.role == role }
    }

    fun searchAccounts(query: String): List<UserAccount> {
        ensureInitialized()
        val q = query.trim()
        if (q.isEmpty()) return getAllAccounts()

        return accountList.filter { account ->
            account.accountId.contains(q, ignoreCase = true) ||
                    account.displayName.contains(q, ignoreCase = true) ||
                    account.username.contains(q, ignoreCase = true) ||
                    (account.email != null && account.email.contains(q, ignoreCase = true))
        }
    }

    fun updateAccountStatus(accountId: String, status: AccountStatus): Boolean {
        ensureInitialized()
        val index = accountList.indexOfFirst { it.accountId.equals(accountId.trim(), ignoreCase = true) }
        if (index != -1) {
            val oldAccount = accountList[index]
            accountList[index] = oldAccount.copy(status = status)
            return true
        }
        return false
    }

    fun registerOrUpdateAccount(account: UserAccount) {
        ensureInitialized()
        val index = accountList.indexOfFirst { it.accountId.equals(account.accountId.trim(), ignoreCase = true) }
        if (index != -1) {
            accountList[index] = account
        } else {
            accountList.add(account)
        }
    }
}
