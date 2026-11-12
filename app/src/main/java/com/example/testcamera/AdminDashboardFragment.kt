package com.example.testcamera

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.testcamera.data.AccountRepository
import com.example.testcamera.data.AccountStatus
import com.example.testcamera.data.KioskSessionManager
import com.example.testcamera.data.UserAccount
import com.example.testcamera.data.UserFaceRepository
import com.example.testcamera.data.UserRole

class AdminDashboardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val account = KioskSessionManager.currentAccount
        if (account == null || (!account.isAdmin() && !account.isTechnician())) {
            Toast.makeText(context, "Truy cập bị từ chối: Yêu cầu quyền Quản trị viên!", Toast.LENGTH_LONG).show()
            (activity as? MainActivity)?.navigateToHome()
            return
        }

        // Global touch handler to refresh 90s inactivity timer
        view.findViewById<View>(R.id.rootAdminDashboard)?.setOnTouchListener { _, _ ->
            KioskSessionManager.touchInactivity()
            false
        }

        // Header info
        val tvHeaderTitle = view.findViewById<TextView>(R.id.tvAdminHeaderTitle)
        val tvRoleBadge = view.findViewById<TextView>(R.id.tvAdminRoleBadge)
        val tvName = view.findViewById<TextView>(R.id.tvAdminAccountName)
        val tvId = view.findViewById<TextView>(R.id.tvAdminAccountId)

        if (account.isAdmin()) {
            tvHeaderTitle?.text = "BẢNG ĐIỀU KHIỂN QUẢN TRỊ VIÊN (ADMIN)"
            tvRoleBadge?.text = "🛡️ ADMIN"
            tvRoleBadge?.setTextColor(ContextCompat.getColor(requireContext(), R.color.hust_gold))
        } else {
            tvHeaderTitle?.text = "BẢNG ĐIỀU KHIỂN KỸ THUẬT VIÊN"
            tvRoleBadge?.text = "⚙️ TECHNICIAN"
            tvRoleBadge?.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_info))
        }

        tvName?.text = account.displayName
        tvId?.text = "Mã tài khoản: ${account.accountId} • ${account.email ?: "N/A"}"

        // Logout
        view.findViewById<Button>(R.id.btnAdminLogout)?.setOnClickListener {
            KioskSessionManager.endSession()
            (activity as? MainActivity)?.navigateToHome()
        }

        // Quick Action 1: Open Dual Camera Technical Diagnostics
        view.findViewById<View>(R.id.btnOpenTechDiagnostics)?.setOnClickListener {
            KioskSessionManager.touchInactivity()
            val cameraFragment = KioskCameraFragment.newInstance(openInTechMode = true)
            (activity as? MainActivity)?.navigateToFragment(cameraFragment, addToBackStack = true)
        }

        // Quick Action 2: Open Face Registration Mode
        view.findViewById<View>(R.id.btnOpenRegisterFace)?.setOnClickListener {
            KioskSessionManager.touchInactivity()
            val cameraFragment = KioskCameraFragment.newInstance(openInTechMode = true)
            (activity as? MainActivity)?.navigateToFragment(cameraFragment, addToBackStack = true)
        }

        // Session timeout listener
        KioskSessionManager.setOnSessionEndedListener {
            activity?.runOnUiThread {
                (activity as? MainActivity)?.navigateToHome()
            }
        }

        refreshDashboard(view)
    }

    private fun refreshDashboard(rootView: View) {
        val ctx = context ?: return

        // 1. Update stats
        UserFaceRepository.init(ctx)
        val registeredFaceCount = UserFaceRepository.getUserCount(ctx)
        val allAccounts = AccountRepository.getAllAccounts()
        val disabledCount = allAccounts.count { !it.isActive() }

        rootView.findViewById<TextView>(R.id.tvStatRegisteredFaces)?.text = registeredFaceCount.toString()
        rootView.findViewById<TextView>(R.id.tvStatTotalAccounts)?.text = allAccounts.size.toString()
        rootView.findViewById<TextView>(R.id.tvStatDisabledAccounts)?.text = disabledCount.toString()

        // 2. Render accounts list
        val container = rootView.findViewById<LinearLayout>(R.id.containerAdminAccountList) ?: return
        container.removeAllViews()

        for (acc in allAccounts) {
            val isFaceRegistered = UserFaceRepository.findUserByStudentId(acc.accountId) != null

            val rowView = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
                setPadding(16, 12, 16, 12)
                setBackgroundColor(0xFFF9FAFB.toInt())
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            // Info Column
            val infoLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvTitle = TextView(ctx).apply {
                text = "${acc.getRoleBadgeText()}  ${acc.displayName} (${acc.accountId})"
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                textSize = 13f
                paint.isFakeBoldText = true
            }

            val tvSub = TextView(ctx).apply {
                val faceStatus = if (isFaceRegistered) "✅ Đã nạp khuôn mặt" else "⚪ Chưa nạp khuôn mặt"
                text = "${acc.email ?: "N/A"} • $faceStatus"
                setTextColor(if (isFaceRegistered) 0xFF2E7D32.toInt() else 0xFF757575.toInt())
                textSize = 11f
            }

            infoLayout.addView(tvTitle)
            infoLayout.addView(tvSub)
            rowView.addView(infoLayout)

            // Status Toggle Button
            val btnStatusToggle = TextView(ctx).apply {
                setPadding(20, 10, 20, 10)
                textSize = 11f
                paint.isFakeBoldText = true

                if (acc.isActive()) {
                    text = "HOẠT ĐỘNG"
                    setTextColor(0xFF2E7D32.toInt())
                    setBackgroundColor(0xFFE8F5E9.toInt())
                } else {
                    text = "VÔ HIỆU HÓA"
                    setTextColor(0xFFC62828.toInt())
                    setBackgroundColor(0xFFFFEBEE.toInt())
                }

                setOnClickListener {
                    KioskSessionManager.touchInactivity()
                    val newStatus = if (acc.isActive()) AccountStatus.DISABLED else AccountStatus.ACTIVE
                    val actionName = if (newStatus == AccountStatus.DISABLED) "vô hiệu hóa" else "kích hoạt lại"

                    AlertDialog.Builder(ctx)
                        .setTitle("Xác nhận thay đổi trạng thái")
                        .setMessage("Bạn có chắc chắn muốn $actionName tài khoản '${acc.displayName}' (${acc.accountId}) không?")
                        .setPositiveButton("Đồng ý") { _, _ ->
                            AccountRepository.updateAccountStatus(acc.accountId, newStatus)
                            Toast.makeText(ctx, "Đã cập nhật trạng thái: ${newStatus.description}", Toast.LENGTH_SHORT).show()
                            refreshDashboard(rootView)
                        }
                        .setNegativeButton("Hủy", null)
                        .show()
                }
            }

            rowView.addView(btnStatusToggle)
            container.addView(rowView)
        }
    }
}
