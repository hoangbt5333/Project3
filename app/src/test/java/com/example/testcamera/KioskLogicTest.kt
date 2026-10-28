package com.example.testcamera

import com.example.testcamera.data.Student
import com.example.testcamera.data.StudentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskLogicTest {

    @Test
    fun testStudentRepositoryFindAndSearch() {
        val student = StudentRepository.findStudentById("20231001")
        assertNotNull(student)
        assertEquals("Nguyễn Văn An", student?.fullName)
        assertEquals("IT1-01", student?.className)
        assertEquals("Công nghệ Thông tin", student?.faculty)

        val searchResults = StudentRepository.searchStudents("Bách Khoa")
        assertTrue(searchResults.isEmpty())

        val searchByName = StudentRepository.searchStudents("An")
        assertTrue(searchByName.isNotEmpty())
        assertTrue(searchByName.any { it.studentId == "20231001" })

        val allStudents = StudentRepository.getAllStudents()
        assertEquals(12, allStudents.size)
    }

    @Test
    fun testVerificationStatesEnum() {
        val states = KioskCameraFragment.VerificationState.values()
        assertEquals(6, states.size)
        assertTrue(states.contains(KioskCameraFragment.VerificationState.CAMERA_CONNECTING))
        assertTrue(states.contains(KioskCameraFragment.VerificationState.IDLE))
        assertTrue(states.contains(KioskCameraFragment.VerificationState.DETECTING))
        assertTrue(states.contains(KioskCameraFragment.VerificationState.VERIFYING))
        assertTrue(states.contains(KioskCameraFragment.VerificationState.SUCCESS))
        assertTrue(states.contains(KioskCameraFragment.VerificationState.UNRECOGNIZED))
    }
}
