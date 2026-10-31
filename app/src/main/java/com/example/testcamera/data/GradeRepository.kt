package com.example.testcamera.data

/**
 * Repository cung cấp dữ liệu bảng điểm học phần mẫu cho sinh viên.
 * Dữ liệu giả lập mô phỏng hệ thống đào tạo tín chỉ Đại học Bách Khoa Hà Nội.
 */
object GradeRepository {

    // Bộ dữ liệu mẫu bảng điểm (được gắn nhãn là dữ liệu giả lập)
    private val mockTranscriptList: List<TranscriptEntry> = listOf(
        // ==================== 1. Nguyễn Văn An (20231001 - CNTT) - Bám sát ảnh mẫu ====================
        TranscriptEntry("20231001", "20253", "SSH1131", "Chủ nghĩa xã hội khoa học", 2, "A", 8.5f),
        TranscriptEntry("20231001", "20252", "SSH1141", "Lịch sử Đảng cộng sản Việt Nam", 2, "A", 8.8f),
        TranscriptEntry("20231001", "20252", "IT4611", "Các hệ thống phân tán và ứng dụng", 2, "A", 8.5f),
        TranscriptEntry("20231001", "20252", "IT4210", "Hệ nhúng", 3, "A+", 9.2f),
        TranscriptEntry("20231001", "20252", "IT4060", "Lập trình mạng", 2, "A", 8.7f),
        TranscriptEntry("20231001", "20252", "IT4015", "Nhập môn an toàn thông tin", 3, "B", 7.5f),
        TranscriptEntry("20231001", "20252", "IT3931", "Project II", 2, "A+", 9.5f),
        TranscriptEntry("20231001", "20252", "IT3120", "Phân tích và thiết kế hệ thống", 2, "B+", 8.2f),
        TranscriptEntry("20231001", "20251", "SSH1151", "Tư tưởng Hồ Chí Minh", 2, "B", 7.8f),

        // ==================== 2. Trần Thị Bình (20231002 - KHMT) ====================
        TranscriptEntry("20231002", "20252", "IT3011", "Cấu trúc dữ liệu và giải thuật", 3, "A+", 9.5f),
        TranscriptEntry("20231002", "20252", "IT3020", "Toán rời rạc", 3, "A", 8.8f),
        TranscriptEntry("20231002", "20252", "IT3030", "Kiến trúc máy tính", 3, "B+", 8.2f),
        TranscriptEntry("20231002", "20251", "MI1111", "Giải tích I", 4, "A", 8.6f),
        TranscriptEntry("20231002", "20251", "MI1141", "Đại số tuyến tính", 3, "B+", 8.0f),
        TranscriptEntry("20231002", "20251", "SSH1111", "Triết học Mác - Lênin", 3, "B", 7.5f),

        // ==================== 3. Lê Hoàng Cường (20231003 - ĐTVT) ====================
        TranscriptEntry("20231003", "20252", "ET2011", "Kỹ thuật mạch điện tử", 3, "B+", 8.0f),
        TranscriptEntry("20231003", "20252", "ET3020", "Xử lý tín hiệu số", 3, "A", 8.7f),
        TranscriptEntry("20231003", "20252", "ET3040", "Lý thuyết thông tin", 2, "B", 7.6f),
        TranscriptEntry("20231003", "20251", "PH1110", "Vật lý đại cương I", 3, "B+", 8.2f),
        TranscriptEntry("20231003", "20251", "EE2010", "Cơ sở kỹ thuật điện", 3, "A", 8.5f),

        // ==================== 4. Phạm Thị Duyên (20231004 - Kinh tế & Quản lý) ====================
        TranscriptEntry("20231004", "20252", "EM1010", "Kinh tế vi mô", 3, "A", 8.8f),
        TranscriptEntry("20231004", "20252", "EM1020", "Kinh tế vĩ mô", 3, "B+", 8.3f),
        TranscriptEntry("20231004", "20252", "EM2011", "Nguyên lý kế toán", 3, "A+", 9.2f),
        TranscriptEntry("20231004", "20251", "EM3100", "Quản trị học", 2, "A", 8.6f),
        TranscriptEntry("20231004", "20251", "MI2020", "Xác suất thống kê", 3, "B", 7.4f),

        // ==================== 5. Hoàng Văn Em (20231005 - Cơ khí) ====================
        TranscriptEntry("20231005", "20252", "ME2010", "Vẽ kỹ thuật cơ khí", 3, "B+", 8.0f),
        TranscriptEntry("20231005", "20252", "ME3012", "Nguyên lý máy", 3, "A", 8.5f),
        TranscriptEntry("20231005", "20252", "ME3020", "Chi tiết máy", 3, "B", 7.8f),
        TranscriptEntry("20231005", "20251", "ME2100", "Sức bền vật liệu", 4, "B+", 8.1f),
        TranscriptEntry("20231005", "20251", "PH1120", "Vật lý đại cương II", 3, "A", 8.7f),

        // ==================== 6. Vũ Thị Giang (20231006 - Kỹ thuật Ô tô) ====================
        TranscriptEntry("20231006", "20252", "TE2010", "Nhập môn Kỹ thuật Ô tô", 2, "A", 8.7f),
        TranscriptEntry("20231006", "20252", "TE3011", "Động cơ đốt trong", 3, "B+", 8.3f),
        TranscriptEntry("20231006", "20252", "TE3020", "Lý thuyết ô tô", 3, "A", 8.6f),
        TranscriptEntry("20231006", "20251", "ME2020", "Cơ học kỹ thuật", 3, "B", 7.7f),
        TranscriptEntry("20231006", "20251", "EE2020", "Kỹ thuật điện tử đại cương", 3, "A", 8.5f),

        // ==================== 7. Đỗ Minh Hải (20231007 - CNTT) ====================
        TranscriptEntry("20231007", "20252", "IT3100", "Lập trình hướng đối tượng", 3, "A", 8.9f),
        TranscriptEntry("20231007", "20252", "IT3110", "Hệ điều hành", 3, "B+", 8.4f),
        TranscriptEntry("20231007", "20252", "IT3080", "Mạng máy tính", 3, "A", 8.8f),
        TranscriptEntry("20231007", "20251", "IT2000", "Nhập môn CNTT và truyền thông", 2, "A+", 9.3f),
        TranscriptEntry("20231007", "20251", "MI1121", "Giải tích II", 3, "B", 7.8f),

        // ==================== 8. Bùi Phương Thảo (20231008 - KHMT) ====================
        TranscriptEntry("20231008", "20252", "IT4040", "Trí tuệ nhân tạo", 3, "A+", 9.6f),
        TranscriptEntry("20231008", "20252", "IT4050", "Học máy", 3, "A+", 9.4f),
        TranscriptEntry("20231008", "20252", "IT4030", "Cơ sở dữ liệu", 3, "A", 8.9f),
        TranscriptEntry("20231008", "20251", "IT3170", "Thuật toán ứng dụng", 2, "A", 8.7f),
        TranscriptEntry("20231008", "20251", "SSH1120", "Kinh tế chính trị Mác - Lênin", 2, "B+", 8.2f),

        // ==================== 9. Đặng Quốc Khánh (20231009 - Điện - Điện tử) ====================
        TranscriptEntry("20231009", "20252", "EE3010", "Khí cụ điện", 2, "B+", 8.1f),
        TranscriptEntry("20231009", "20252", "EE3020", "Máy điện", 3, "A", 8.5f),
        TranscriptEntry("20231009", "20252", "EE3030", "Hệ thống điện", 3, "B", 7.6f),
        TranscriptEntry("20231009", "20251", "EE2011", "Mạch điện I", 3, "A", 8.6f),
        TranscriptEntry("20231009", "20251", "PH1130", "Vật lý đại cương III", 3, "B+", 8.3f),

        // ==================== 10. Ngô Thị Linh (20231010 - Toán ứng dụng) ====================
        TranscriptEntry("20231010", "20252", "MI3010", "Giải tích hàm", 3, "A+", 9.3f),
        TranscriptEntry("20231010", "20252", "MI3020", "Phương trình vi phân", 3, "A", 8.8f),
        TranscriptEntry("20231010", "20252", "MI3050", "Tối ưu hóa", 3, "A+", 9.5f),
        TranscriptEntry("20231010", "20251", "MI2010", "Giải tích số", 3, "B+", 8.4f),
        TranscriptEntry("20231010", "20251", "IT1110", "Tin học đại cương", 4, "A", 9.0f),

        // ==================== 11. Dương Tấn Minh (20231011 - CNTT) ====================
        TranscriptEntry("20231011", "20252", "IT3040", "Kỹ thuật lập trình", 2, "B+", 8.2f),
        TranscriptEntry("20231011", "20252", "IT3070", "Nguyên lý hệ điều hành", 3, "B", 7.5f),
        TranscriptEntry("20231011", "20252", "IT3090", "Cơ sở dữ liệu nâng cao", 3, "A", 8.7f),
        TranscriptEntry("20231011", "20251", "MI1131", "Giải tích III", 3, "B+", 8.0f),
        TranscriptEntry("20231011", "20251", "SSH1141", "Lịch sử Đảng", 2, "B", 7.8f)

        // ==================== 12. Võ Hoàng Nam (20231012 - ĐTVT) ====================
        // Cố ý ĐỂ TRỐNG (0 học phần) để kiểm thử giao diện Empty State khi sinh viên chưa có dữ liệu điểm
    )

    /**
     * Lấy danh sách điểm học phần theo [studentId].
     * Trả về danh sách rỗng nếu không tìm thấy hoặc sinh viên chưa có điểm.
     */
    fun getGradesByStudentId(studentId: String): List<TranscriptEntry> {
        val targetId = studentId.trim()
        if (targetId.isEmpty()) return emptyList()
        return mockTranscriptList.filter { it.studentId.equals(targetId, ignoreCase = true) }
    }

    /**
     * Tổng số tín chỉ tích lũy của sinh viên từ dữ liệu mẫu.
     */
    fun getTotalCredits(studentId: String): Int {
        return getGradesByStudentId(studentId).sumOf { it.credits }
    }

    /**
     * Kiểm tra sinh viên có dữ liệu bảng điểm học phần hay không.
     */
    fun hasGrades(studentId: String): Boolean {
        return getGradesByStudentId(studentId).isNotEmpty()
    }
}
