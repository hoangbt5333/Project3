package com.example.testcamera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.testcamera.data.KioskSessionManager

class StudentProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_student_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val student = KioskSessionManager.currentStudent
        if (student == null) {
            endSessionAndReturnHome()
            return
        }

        view.findViewById<View>(R.id.rootStudentProfile)?.setOnTouchListener { _, _ ->
            KioskSessionManager.touchInactivity()
            false
        }

        view.findViewById<TextView>(R.id.tvStudentName).text = "Họ và tên: ${student.fullName}"
        view.findViewById<TextView>(R.id.tvStudentId).text = "Mã sinh viên: ${student.studentId}"
        view.findViewById<TextView>(R.id.tvStudentClass).text = "Lớp: ${student.className} | Khoa: ${student.faculty}"
        view.findViewById<TextView>(R.id.tvStudentEmail).text = "Email: ${student.email}"

        view.findViewById<Button>(R.id.btnViewGrades).setOnClickListener {
            KioskSessionManager.touchInactivity()
            openServicePlaceholder("Xem Bảng Điểm")
        }

        view.findViewById<Button>(R.id.btnPrintGrades).setOnClickListener {
            KioskSessionManager.touchInactivity()
            openServicePlaceholder("In Bảng Điểm")
        }

        view.findViewById<Button>(R.id.btnOtherServices).setOnClickListener {
            KioskSessionManager.touchInactivity()
            openServicePlaceholder("Dịch Vụ Khác")
        }

        val endSessionAction = View.OnClickListener {
            endSessionAndReturnHome()
        }

        view.findViewById<Button>(R.id.btnEndSession).setOnClickListener(endSessionAction)
        view.findViewById<Button>(R.id.btnEndSessionTop).setOnClickListener(endSessionAction)

        KioskSessionManager.setOnSessionEndedListener {
            activity?.runOnUiThread {
                (activity as? MainActivity)?.navigateToHome()
            }
        }
    }

    private fun openServicePlaceholder(serviceName: String) {
        val fragment = KioskServicePlaceholderFragment.newInstance(serviceName)
        (activity as? MainActivity)?.navigateToFragment(fragment, addToBackStack = true)
    }

    private fun endSessionAndReturnHome() {
        KioskSessionManager.endSession()
        (activity as? MainActivity)?.navigateToHome()
    }
}
