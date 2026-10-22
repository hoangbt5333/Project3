package com.example.testcamera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.testcamera.data.KioskSessionManager

class KioskServicePlaceholderFragment : Fragment() {

    companion object {
        private const val ARG_SERVICE_NAME = "arg_service_name"

        fun newInstance(serviceName: String): KioskServicePlaceholderFragment {
            return KioskServicePlaceholderFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SERVICE_NAME, serviceName)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_kiosk_service_placeholder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val serviceName = arguments?.getString(ARG_SERVICE_NAME) ?: "DỊCH VỤ SINH VIÊN"
        val student = KioskSessionManager.currentStudent

        view.findViewById<View>(R.id.rootServicePlaceholder)?.setOnTouchListener { _, _ ->
            KioskSessionManager.touchInactivity()
            false
        }

        view.findViewById<TextView>(R.id.tvServiceName).text = "📊 $serviceName"
        view.findViewById<TextView>(R.id.tvStudentInfo).text =
            if (student != null) "Sinh viên: ${student.fullName} (${student.studentId})" else "Sinh viên: Chưa xác thực"

        view.findViewById<Button>(R.id.btnBackToProfile).setOnClickListener {
            KioskSessionManager.touchInactivity()
            parentFragmentManager.popBackStack()
        }

        view.findViewById<Button>(R.id.btnEndSessionService).setOnClickListener {
            KioskSessionManager.endSession()
            (activity as? MainActivity)?.navigateToHome()
        }
    }
}
