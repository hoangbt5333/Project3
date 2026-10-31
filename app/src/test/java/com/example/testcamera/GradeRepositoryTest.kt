package com.example.testcamera

import com.example.testcamera.data.GradeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeRepositoryTest {

    @Test
    fun testGetGradesByStudentId_studentWithGrades() {
        // Sinh viên 20231001 (Nguyễn Văn An)
        val grades = GradeRepository.getGradesByStudentId("20231001")
        assertFalse(grades.isEmpty())
        assertEquals(9, grades.size)

        // Đảm bảo tất cả các học phần đều thuộc về đúng mã sinh viên này
        for (grade in grades) {
            assertEquals("20231001", grade.studentId)
            assertTrue(grade.credits > 0)
            assertTrue(grade.subjectCode.isNotBlank())
            assertTrue(grade.subjectName.isNotBlank())
            assertTrue(grade.letterGrade in listOf("A+", "A", "B+", "B", "C+", "C", "D+", "D", "F"))
        }

        // Kiểm tra tổng số tín chỉ tích lũy (2+2+2+3+2+3+2+2+2 = 20 TC)
        val totalCredits = GradeRepository.getTotalCredits("20231001")
        assertEquals(20, totalCredits)
        assertTrue(GradeRepository.hasGrades("20231001"))
    }

    @Test
    fun testGetGradesByStudentId_emptyStudent() {
        // Sinh viên 20231012 (Võ Hoàng Nam) cố ý để trống để kiểm thử giao diện rỗng
        val grades = GradeRepository.getGradesByStudentId("20231012")
        assertTrue(grades.isEmpty())
        assertEquals(0, GradeRepository.getTotalCredits("20231012"))
        assertFalse(GradeRepository.hasGrades("20231012"))
    }

    @Test
    fun testGetGradesByStudentId_nonExistentStudent() {
        // Sinh viên không tồn tại
        val grades = GradeRepository.getGradesByStudentId("NON_EXISTENT_ID")
        assertTrue(grades.isEmpty())

        val emptyIdGrades = GradeRepository.getGradesByStudentId("")
        assertTrue(emptyIdGrades.isEmpty())
    }

    @Test
    fun testStudentIsolation() {
        // Đảm bảo truy vấn sinh viên A không bao giờ trả về dữ liệu của sinh viên B
        val grades1 = GradeRepository.getGradesByStudentId("20231001")
        val grades2 = GradeRepository.getGradesByStudentId("20231002")

        assertTrue(grades1.none { it.studentId == "20231002" })
        assertTrue(grades2.none { it.studentId == "20231001" })
    }
}
