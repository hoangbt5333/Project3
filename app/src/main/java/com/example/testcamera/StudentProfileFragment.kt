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

        // Global touch handler to refresh 90s inactivity timer
        view.findViewById<View>(R.id.rootStudentProfile)?.setOnTouchListener { _, _ ->
            KioskSessionManager.touchInactivity()
            false
        }

        // Populate student profile fields dynamically from StudentRepository/Session
        view.findViewById<TextView>(R.id.tvStudentName)?.text = student.fullName
        view.findViewById<TextView>(R.id.tvStudentId)?.text = "MSSV: ${student.studentId}"
        view.findViewById<TextView>(R.id.tvStudentClass)?.text = "Lớp: ${student.className} • Khoa: ${student.faculty}"
        view.findViewById<TextView>(R.id.tvStudentEmail)?.text = "Email: ${student.email}"

        // Service 1: Xem Bảng Điểm Học Tập
        val openGradesAction = View.OnClickListener {
            KioskSessionManager.touchInactivity()
            (activity as? MainActivity)?.navigateToFragment(StudentTranscriptFragment(), addToBackStack = true)
        }
        view.findViewById<Button>(R.id.btnViewGrades)?.setOnClickListener(openGradesAction)
        view.findViewById<View>(R.id.cardBtnViewGrades)?.setOnClickListener(openGradesAction)

        // Service 2: In Bảng Điểm
        val printGradesAction = View.OnClickListener {
            KioskSessionManager.touchInactivity()
            openServicePlaceholder("In Bảng Điểm Sinh Viên")
        }
        view.findViewById<Button>(R.id.btnPrintGrades)?.setOnClickListener(printGradesAction)
        view.findViewById<View>(R.id.cardBtnPrintGrades)?.setOnClickListener(printGradesAction)

        // Service 3: Dịch Vụ Khác
        val otherServicesAction = View.OnClickListener {
            KioskSessionManager.touchInactivity()
            openServicePlaceholder("Dịch Vụ Sinh Viên Khác")
        }
        view.findViewById<Button>(R.id.btnOtherServices)?.setOnClickListener(otherServicesAction)
        view.findViewById<View>(R.id.cardBtnOtherServices)?.setOnClickListener(otherServicesAction)

        // End session actions
        val endSessionAction = View.OnClickListener {
            endSessionAndReturnHome()
        }
        view.findViewById<Button>(R.id.btnEndSession)?.setOnClickListener(endSessionAction)
        view.findViewById<Button>(R.id.btnEndSessionTop)?.setOnClickListener(endSessionAction)

        // Callback when inactivity timeout triggers
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
