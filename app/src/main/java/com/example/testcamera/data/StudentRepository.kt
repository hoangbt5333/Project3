package com.example.testcamera.data

object StudentRepository {

    private val mockStudentList = listOf(
        Student(
            studentId = "20231001",
            fullName = "Nguyễn Văn An",
            className = "IT1-01",
            faculty = "Công nghệ Thông tin",
            email = "an.nv231001@sis.hust.edu.vn"
        ),
        Student(
            studentId = "20231002",
            fullName = "Trần Thị Bình",
            className = "IT2-02",
            faculty = "Khoa học Máy tính",
            email = "binh.tt231002@sis.hust.edu.vn"
        ),
        Student(
            studentId = "20231003",
            fullName = "Lê Hoàng Cường",
            className = "ET1-01",
            faculty = "Điện tử Viễn thông",
            email = "cuong.lh231003@sis.hust.edu.vn"
        ),
        Student(
            studentId = "20231004",
            fullName = "Phạm Thị Duyên",
            className = "EM1-02",
            faculty = "Kinh tế & Quản lý",
            email = "duyen.pt231004@sis.hust.edu.vn"
        ),
        Student(
            studentId = "20231005",
            fullName = "Hoàng Văn Em",
            className = "ME1-01",
            faculty = "Cơ khí",
            email = "em.hv231005@sis.hust.edu.vn"
        ),
        Student(
            studentId = "20231006",
            fullName = "Vũ Thị Giang",
            className = "TE1-02",
            faculty = "Kỹ thuật Ô tô",
            email = "giang.vt231006@sis.hust.edu.vn"
        ),
        Student(
            studentId = "20231007",
            fullName = "Đỗ Minh Hải",
            className = "IT1-02",
            faculty = "Công nghệ Thông tin",
            email = "hai.dm231007@sis.hust.edu.vn"
        ),
        Student(
            studentId = "20231008",
            fullName = "Bùi Phương Thảo",
            className = "IT2-01",
            faculty = "Khoa học Máy tính",
            email = "thao.bp231008@sis.hust.edu.vn"
        ),
        Student(
            studentId = "20231009",
            fullName = "Đặng Quốc Khánh",
            className = "EE1-01",
            faculty = "Điện - Điện tử",
            email = "khanh.dq231009@sis.hust.edu.vn"
        ),
        Student(
            studentId = "20231010",
            fullName = "Ngô Thị Linh",
            className = "MI1-02",
            faculty = "Toán ứng dụng",
            email = "linh.nt231010@sis.hust.edu.vn"
        ),
        Student(
            studentId = "20231011",
            fullName = "Dương Tấn Minh",
            className = "IT1-03",
            faculty = "Công nghệ Thông tin",
            email = "minh.dt231011@sis.hust.edu.vn"
        ),
        Student(
            studentId = "20231012",
            fullName = "Võ Hoàng Nam",
            className = "ET2-01",
            faculty = "Điện tử Viễn thông",
            email = "nam.vh231012@sis.hust.edu.vn"
        )
    )

    fun getAllStudents(): List<Student> {
        return mockStudentList
    }

    fun findStudentById(studentId: String): Student? {
        val targetId = studentId.trim()
        return mockStudentList.firstOrNull { it.studentId.equals(targetId, ignoreCase = true) }
    }

    fun searchStudents(query: String): List<Student> {
        val q = query.trim()
        if (q.isEmpty()) return mockStudentList

        return mockStudentList.filter { student ->
            student.studentId.contains(q, ignoreCase = true) ||
                    student.fullName.contains(q, ignoreCase = true) ||
                    student.className.contains(q, ignoreCase = true) ||
                    student.faculty.contains(q, ignoreCase = true)
        }
    }

    fun studentExists(studentId: String): Boolean {
        return findStudentById(studentId) != null
    }
}
