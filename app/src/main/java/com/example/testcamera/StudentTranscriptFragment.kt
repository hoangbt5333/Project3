package com.example.testcamera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.testcamera.data.GradeRepository
import com.example.testcamera.data.KioskSessionManager

class StudentTranscriptFragment : Fragment() {

    private lateinit var transcriptAdapter: TranscriptAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_student_transcript, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Kiểm tra tính hợp lệ của phiên làm việc
        val student = KioskSessionManager.currentStudent
        if (student == null || !KioskSessionManager.isSessionActive) {
            endSessionAndReturnHome()
            return
        }

        // 2. Bắt sự kiện chạm để gia hạn inactivity timeout 90 giây
        val touchListener = View.OnTouchListener { _, _ ->
            KioskSessionManager.touchInactivity()
            false
        }
        view.findViewById<View>(R.id.rootTranscriptLayout)?.setOnTouchListener(touchListener)

        // 3. Hiển thị thông tin sinh viên từ phiên hiện tại
        view.findViewById<TextView>(R.id.tvTranscriptStudentName)?.text = student.fullName
        view.findViewById<TextView>(R.id.tvTranscriptStudentDetails)?.text =
            "MSSV: ${student.studentId}  •  Lớp: ${student.className}  •  Khoa: ${student.faculty}"

        val totalCredits = GradeRepository.getTotalCredits(student.studentId)
        view.findViewById<TextView>(R.id.tvTranscriptCreditsSummary)?.text =
            "Tích lũy: $totalCredits TC (Minh họa)"

        // 4. Khởi tạo RecyclerView & Adapter
        val rvTranscript = view.findViewById<RecyclerView>(R.id.rvTranscriptList)
        val layoutEmpty = view.findViewById<View>(R.id.layoutEmptyTranscript)

        transcriptAdapter = TranscriptAdapter()
        rvTranscript?.layoutManager = LinearLayoutManager(requireContext())
        rvTranscript?.adapter = transcriptAdapter
        rvTranscript?.setOnTouchListener(touchListener)

        // 5. Truy xuất bảng điểm theo studentId của đúng sinh viên hiện tại
        val grades = GradeRepository.getGradesByStudentId(student.studentId)

        if (grades.isEmpty()) {
            rvTranscript?.visibility = View.GONE
            layoutEmpty?.visibility = View.VISIBLE
        } else {
            rvTranscript?.visibility = View.VISIBLE
            layoutEmpty?.visibility = View.GONE
            transcriptAdapter.submitList(grades)
        }

        // 6. Điều hướng
        view.findViewById<Button>(R.id.btnBackToProfile)?.setOnClickListener {
            KioskSessionManager.touchInactivity()
            parentFragmentManager.popBackStack()
        }

        val endSessionAction = View.OnClickListener {
            endSessionAndReturnHome()
        }
        view.findViewById<Button>(R.id.btnEndSessionTop)?.setOnClickListener(endSessionAction)
        view.findViewById<Button>(R.id.btnEndSessionBottom)?.setOnClickListener(endSessionAction)

        // Đăng ký listener khi hết 90 giây timeout
        KioskSessionManager.setOnSessionEndedListener {
            activity?.runOnUiThread {
                (activity as? MainActivity)?.navigateToHome()
            }
        }
    }

    private fun endSessionAndReturnHome() {
        KioskSessionManager.endSession()
        (activity as? MainActivity)?.navigateToHome()
    }
}
