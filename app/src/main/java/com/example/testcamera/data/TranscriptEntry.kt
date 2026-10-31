package com.example.testcamera.data

/**
 * Model biểu diễn một bản ghi điểm học phần của sinh viên.
 * Khóa liên kết: [studentId].
 */
data class TranscriptEntry(
    val studentId: String,
    val semester: String,        // Ví dụ: "20251", "20252", "20253"
    val subjectCode: String,     // Ví dụ: "SSH1131", "IT4611", "IT4210"
    val subjectName: String,     // Ví dụ: "Chủ nghĩa xã hội khoa học", "Hệ nhúng"
    val credits: Int,            // Số tín chỉ (nguyên dương, 1 - 6)
    val letterGrade: String,     // Điểm học phần: "A+", "A", "B+", "B", "C+", "C", "D+", "D", "F"
    val score: Float? = null     // Điểm thang 10 (tùy chọn minh họa: 0.0 - 10.0)
)
