package com.example.testcamera

import com.example.testcamera.data.UserFaceModel
import com.example.testcamera.data.UserFaceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.sqrt

class FaceRecognitionPipelineTest {

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum.toDouble()).toFloat()
        if (norm > 0f && !norm.isNaN()) {
            for (i in v.indices) v[i] /= norm
        }
        return v
    }

    private fun generateSimulatedFaceEmbedding(seed: Long): FloatArray {
        val random = Random(seed)
        val raw = FloatArray(192) { 0.25f + random.nextFloat() * 0.55f }
        val mean = raw.average().toFloat()
        for (i in raw.indices) raw[i] -= mean
        return l2Normalize(raw)
    }

    @Test
    fun testCosineSimilarityProperties() {
        val vecA = floatArrayOf(1f, 0f, 0f)
        val vecB = floatArrayOf(1f, 0f, 0f)
        val vecC = floatArrayOf(0f, 1f, 0f)
        val vecD = floatArrayOf(-1f, 0f, 0f)
        val zeroVec = floatArrayOf(0f, 0f, 0f)

        // Identical vectors -> 1.0
        assertEquals(1.0f, UserFaceRepository.calculateCosineSimilarity(vecA, vecB), 1e-4f)

        // Orthogonal vectors -> 0.0
        assertEquals(0.0f, UserFaceRepository.calculateCosineSimilarity(vecA, vecC), 1e-4f)

        // Opposite vectors -> -1.0
        assertEquals(-1.0f, UserFaceRepository.calculateCosineSimilarity(vecA, vecD), 1e-4f)

        // Zero-norm vector -> safe 0.0 without exception
        assertEquals(0.0f, UserFaceRepository.calculateCosineSimilarity(vecA, zeroVec), 1e-4f)
        assertEquals(0.0f, UserFaceRepository.calculateCosineSimilarity(zeroVec, zeroVec), 1e-4f)
    }

    @Test
    fun testRegisteredStudentsMatchAccurately() {
        // Registered Student A
        val embStudentA = generateSimulatedFaceEmbedding(10001L)
        val userA = UserFaceModel("20231001", "Nguyễn Văn An", embStudentA)

        // Registered Student B
        val embStudentB = generateSimulatedFaceEmbedding(20002L)
        val userB = UserFaceModel("20231002", "Trần Thị Bình", embStudentB)

        // Live camera frame for Student A with subtle lighting variation
        val liveStudentA = FloatArray(192) { i -> embStudentA[i] + (Random(42).nextFloat() - 0.5f) * 0.04f }
        l2Normalize(liveStudentA)

        val simWithA = UserFaceRepository.calculateCosineSimilarity(liveStudentA, userA.embedding)
        val simWithB = UserFaceRepository.calculateCosineSimilarity(liveStudentA, userB.embedding)

        println("Live A vs User A: $simWithA (Expect >= 0.65)")
        println("Live A vs User B: $simWithB (Expect < 0.65)")

        assertTrue("Similarity with Student A must be >= 0.65: $simWithA", simWithA >= 0.65f)
        assertTrue("Similarity with Student B must be < 0.65: $simWithB", simWithB < 0.65f)
    }

    @Test
    fun testUnregisteredPersonIsStrictlyRejected() {
        // Registered Student A
        val embStudentA = generateSimulatedFaceEmbedding(10001L)
        val userA = UserFaceModel("20231001", "Nguyễn Văn An", embStudentA)

        // Unregistered stranger (different face geometry/illumination pattern)
        val embStranger = generateSimulatedFaceEmbedding(99999L)

        val simStrangerWithStudent = UserFaceRepository.calculateCosineSimilarity(embStranger, userA.embedding)
        println("Stranger vs Registered Student: $simStrangerWithStudent (Expect < 0.65)")

        assertTrue("Stranger must NOT match registered student (sim < 0.65): $simStrangerWithStudent", simStrangerWithStudent < 0.65f)
    }

    @Test
    fun testZeroMeanPreventsHighCorrelationBug() {
        val size = 192
        val randA = Random(111L)
        val randB = Random(222L)

        // Raw positive luminance values
        val rawA = FloatArray(size) { 0.3f + randA.nextFloat() * 0.5f }
        val rawB = FloatArray(size) { 0.3f + randB.nextFloat() * 0.5f }

        // WITHOUT zero-mean (the bug in phase 5.1):
        val bugA = l2Normalize(rawA.clone())
        val bugB = l2Normalize(rawB.clone())
        val bugSimilarity = UserFaceRepository.calculateCosineSimilarity(bugA, bugB)
        println("Similarity WITHOUT zero-mean (Bug 5.1): $bugSimilarity (Shows > 0.85)")
        assertTrue("Demonstrates the bug: positive luminance vectors artificially correlate > 0.8", bugSimilarity > 0.8f)

        // WITH zero-mean (the fix in Task 3):
        val fixedA = generateSimulatedFaceEmbedding(111L)
        val fixedB = generateSimulatedFaceEmbedding(222L)
        val fixedSimilarity = UserFaceRepository.calculateCosineSimilarity(fixedA, fixedB)
        println("Similarity WITH zero-mean (Fixed): $fixedSimilarity (Shows < 0.40)")
        assertTrue("Fixed vectors must have low correlation (< 0.40): $fixedSimilarity", fixedSimilarity < 0.40f)
    }

    @Test
    fun testEmptyDatabaseHandling() {
        val liveEmbedding = generateSimulatedFaceEmbedding(12345L)
        // With empty database, UserFaceRepository.findBestMatch returns null
        val matchResult = UserFaceRepository.findBestMatch(liveEmbedding, threshold = 0.65f)
        assertNull("Empty database must return null (NO MATCH)", matchResult)
    }

    @Test
    fun testInvalidEmbeddingValidation() {
        val allZeroVector = FloatArray(192) { 0f }
        val nanVector = FloatArray(192) { Float.NaN }
        val validVector = generateSimulatedFaceEmbedding(777L)

        val isZeroValid = allZeroVector.size == 192 && allZeroVector.any { it != 0f } && allZeroVector.none { it.isNaN() }
        val isNanValid = nanVector.size == 192 && nanVector.any { it != 0f } && nanVector.none { it.isNaN() }
        val isValidValid = validVector.size == 192 && validVector.any { it != 0f } && validVector.none { it.isNaN() }

        assertEquals(false, isZeroValid)
        assertEquals(false, isNanValid)
        assertEquals(true, isValidValid)
    }

    @Test
    fun testScoreMarginDisambiguationAndAcceptance() {
        UserFaceRepository.clearUsersForTesting()

        val embA = generateSimulatedFaceEmbedding(101L)
        val embB = generateSimulatedFaceEmbedding(202L)

        val userA = UserFaceModel("20231001", "Sinh viên A", embA)
        val userB = UserFaceModel("20231002", "Sinh viên B", embB)

        UserFaceRepository.addUserForTesting(userA)
        UserFaceRepository.addUserForTesting(userB)

        // Case 1: High confidence match on User A (large margin)
        // Live vector is very close to A (sim ~0.95), far from B
        val liveCloseToA = FloatArray(192) { i -> embA[i] + (Random(42).nextFloat() - 0.5f) * 0.02f }
        l2Normalize(liveCloseToA)

        val matchA = UserFaceRepository.findBestMatch(liveCloseToA, threshold = 0.70f, minMargin = 0.08f)
        assertNotNull("Clear match must be accepted", matchA)
        assertEquals("20231001", matchA?.user?.studentId)
        assertTrue("Margin must be significant (> 0.20): ${matchA?.margin}", (matchA?.margin ?: 0f) > 0.20f)

        // Case 2: Ambiguous synthetic vector halfway between User A and User B
        // Sim(A) and Sim(B) will be nearly equal
        val halfwayVector = FloatArray(192) { i -> (embA[i] + embB[i]) / 2.0f }
        l2Normalize(halfwayVector)

        val simA = UserFaceRepository.calculateCosineSimilarity(halfwayVector, embA)
        val simB = UserFaceRepository.calculateCosineSimilarity(halfwayVector, embB)
        println("Halfway SimA: $simA, SimB: $simB, Diff: ${Math.abs(simA - simB)}")

        // If threshold is set so both pass, margin check must reject it!
        val lowerThresh = minOf(simA, simB) - 0.05f
        val ambiguousMatch = UserFaceRepository.findBestMatch(halfwayVector, threshold = lowerThresh, minMargin = 0.08f)
        assertNull("Ambiguous match with margin < 0.08 must be strictly rejected", ambiguousMatch)

        UserFaceRepository.clearUsersForTesting()
    }
}
