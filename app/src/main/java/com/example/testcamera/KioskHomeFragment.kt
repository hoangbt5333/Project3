package com.example.testcamera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
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
}
