package com.example.testcamera

import com.example.testcamera.ml.AttackScenario
import com.example.testcamera.ml.BenchmarkRecord
import com.example.testcamera.ml.LivenessBenchmarkLogger
import com.example.testcamera.ml.LivenessMode
import com.example.testcamera.ml.TestDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivenessBenchmarkLoggerTest {

    @Test
    fun testScenarioAndDistanceCycling() {
        val sc1 = AttackScenario.REAL_FACE
        val sc2 = sc1.next()
        assertEquals(AttackScenario.PHONE_SCREEN, sc2)

        val lastSc = AttackScenario.CUTOUT_FACE
        assertEquals(AttackScenario.REAL_FACE, lastSc.next())

        val d1 = TestDistance.DIST_40CM
        val d2 = d1.next()
        assertEquals(TestDistance.DIST_60CM, d2)
        assertEquals(TestDistance.DIST_80CM, d2.next())
        assertEquals(TestDistance.DIST_40CM, d2.next().next())
    }

    @Test
    fun testEmptyStats() {
        val stats = LivenessBenchmarkLogger.computeStats(emptyList())
        assertEquals(0, stats.totalSamples)
        assertEquals(0f, stats.apcer, 0.0001f)
        assertEquals(0f, stats.bpcer, 0.0001f)
        assertEquals(0f, stats.acer, 0.0001f)
    }

    @Test
    fun testApcerBpcerComputation() {
        // Tạo 10 mẫu:
        // - 5 mẫu Bona Fide (REAL_FACE): 4 PASS, 1 FAIL (BPCER = 1 / 5 = 20.0%)
        // - 5 mẫu Attack:
        //    * 3 PHONE_SCREEN: 2 FAIL, 1 PASS (False Accept)
        //    * 2 PRINT_LASER: 2 FAIL, 0 PASS
        //    => Tổng Attack False Accept = 1 / 5 = 20.0% APCER
        //    => ACER = (20% + 20%) / 2 = 20.0%

        val records = mutableListOf<BenchmarkRecord>()

        // 4 Real Face Pass
        repeat(4) { idx ->
            records.add(createDummyRecord(AttackScenario.REAL_FACE, isLiveDecision = true, id = idx))
        }
        // 1 Real Face Fail (False Rejection)
        records.add(createDummyRecord(AttackScenario.REAL_FACE, isLiveDecision = false, id = 99))

        // 2 Phone Screen Fail (Correctly blocked)
        repeat(2) { idx ->
            records.add(createDummyRecord(AttackScenario.PHONE_SCREEN, isLiveDecision = false, id = 100 + idx))
        }
        // 1 Phone Screen Pass (False Acceptance)
        records.add(createDummyRecord(AttackScenario.PHONE_SCREEN, isLiveDecision = true, id = 102))

        // 2 Print Laser Fail (Correctly blocked)
        repeat(2) { idx ->
            records.add(createDummyRecord(AttackScenario.PRINT_LASER_COLOR, isLiveDecision = false, id = 200 + idx))
        }

        val stats = LivenessBenchmarkLogger.computeStats(records)
        assertEquals(10, stats.totalSamples)
        assertEquals(5, stats.bonaFideCount)
        assertEquals(4, stats.bonaFidePassCount)
        assertEquals(1, stats.bonaFideFalseRejectCount)
        assertEquals(0.20f, stats.bpcer, 0.001f)

        assertEquals(5, stats.attackCount)
        assertEquals(4, stats.attackRejectCount)
        assertEquals(1, stats.attackFalseAcceptCount)
        assertEquals(0.20f, stats.apcer, 0.001f)

        assertEquals(0.20f, stats.acer, 0.001f)

        // Kiểm tra breakdown chi tiết
        val phoneStats = stats.attackBreakdown[AttackScenario.PHONE_SCREEN]
        assertEquals(3, phoneStats?.total)
        assertEquals(1, phoneStats?.falseAcceptCount)
        assertEquals(1f / 3f, phoneStats?.apcer ?: 0f, 0.001f)

        val printStats = stats.attackBreakdown[AttackScenario.PRINT_LASER_COLOR]
        assertEquals(2, printStats?.total)
        assertEquals(0, printStats?.falseAcceptCount)
        assertEquals(0.0f, printStats?.apcer ?: 0f, 0.001f)
    }

    @Test
    fun testCsvRowFormatting() {
        val record = createDummyRecord(AttackScenario.PHONE_SCREEN, isLiveDecision = false, id = 42)
        val row = record.toCsvRow()

        assertTrue(row.contains("PHONE_SCREEN"))
        assertTrue(row.contains("FAIL"))
        assertTrue(row.contains("false")) // is_bona_fide
        assertTrue(row.contains("true")) // isCorrect: attack detected as fail is correct
    }

    private fun createDummyRecord(
        scenario: AttackScenario,
        isLiveDecision: Boolean,
        id: Int
    ): BenchmarkRecord {
        val isCorrect = if (scenario.isBonaFide) isLiveDecision else !isLiveDecision
        return BenchmarkRecord(
            timestamp = 1700000000000L + id,
            dateTimeStr = "2026-11-18 15:00:00",
            sessionId = "sess_01",
            scenario = scenario,
            distance = TestDistance.DIST_60CM,
            mode = LivenessMode.HYBRID_AND,
            trackingId = id,
            isLiveDecision = isLiveDecision,
            isCorrect = isCorrect,
            realScore = if (isLiveDecision) 0.95f else 0.05f,
            printScore = if (isLiveDecision) 0.02f else 0.85f,
            screenScore = if (isLiveDecision) 0.03f else 0.10f,
            predictedClassId = if (isLiveDecision) 1 else 0,
            irPass = isLiveDecision,
            rgbPass = isLiveDecision,
            irContrast = 0.45f,
            irMeanFace = 120.0f,
            irMeanBg = 50.0f,
            irSatRatio = 0.01f,
            latencyMs = 35L,
            rejectionReason = if (isLiveDecision) "NONE" else "IR_CONTRAST_LOW"
        )
    }
}
