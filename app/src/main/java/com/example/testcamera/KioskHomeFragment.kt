package com.example.testcamera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.testcamera.data.AccountRepository
import com.example.testcamera.data.KioskSessionManager
import com.example.testcamera.data.UserFaceRepository

class KioskHomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_kiosk_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnStart = view.findViewById<Button>(R.id.btnStartAuthentication)
        btnStart.setOnClickListener {
            (activity as? MainActivity)?.navigateToFragment(KioskCameraFragment(), addToBackStack = false)
        }

        val openAdminAction = View.OnClickListener {
            showAdminLoginDialog()
        }

        view.findViewById<Button>(R.id.btnHomeAdminAccess)?.setOnClickListener(openAdminAction)
        view.findViewById<Button>(R.id.btnHomeAdminPortal)?.setOnClickListener(openAdminAction)

        // Update system status indicator
        val ctx = context
        val tvStatus = view.findViewById<TextView>(R.id.tvHomeSystemStatus)
        if (ctx != null) {
            UserFaceRepository.init(ctx)
            val registeredCount = UserFaceRepository.getUserCount(ctx)
            if (registeredCount > 0) {
                tvStatus?.text = "Hệ thống sẵn sàng ($registeredCount sinh viên đã đăng ký)"
            } else {
                tvStatus?.text = "Camera sẵn sàng • Sẵn sàng xác thực"
            }
        }
    }

    private fun showAdminLoginDialog() {
        val ctx = context ?: return
        val input = EditText(ctx).apply {
            hint = "Nhập mã PIN (Mặc định: 123456)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(ctx)
            .setTitle("🛡️ Đăng nhập Quản trị viên (Admin)")
            .setMessage("Nhập mã PIN quản trị viên Kiosk (Mặc định: 123456) hoặc để trống để đăng nhập nhanh cho thử nghiệm:")
            .setView(input)
            .setPositiveButton("Đăng nhập") { _, _ ->
                val pin = input.text.toString().trim()
                if (pin == "123456" || pin == "888888" || pin.isEmpty()) {
                    val adminAccount = AccountRepository.findAccountById("ADMIN_01")
                    if (adminAccount != null) {
                        KioskSessionManager.startSession(adminAccount)
                        Toast.makeText(ctx, "✅ Đăng nhập Quản trị viên thành công!", Toast.LENGTH_SHORT).show()
                        (activity as? MainActivity)?.navigateToFragment(AdminDashboardFragment(), addToBackStack = true)
                    }
                } else {
                    Toast.makeText(ctx, "❌ Mã PIN không chính xác! (Mặc định: 123456)", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
