package com.example.testcamera

import com.example.testcamera.ml.AntiSpoofingEngine
import com.example.testcamera.ml.FasClass
import com.example.testcamera.ml.LivenessMode
import com.example.testcamera.ml.LivenessResult
import com.example.testcamera.util.IrMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AntiSpoofingEngineTest {

    @Test
    fun testExpandedBox_NormalFaceCentered() {
        val srcW = 640
        val srcH = 480

        val expanded = AntiSpoofingEngine.calculateExpandedBox(
            srcW = srcW,
            srcH = srcH,
            boxLeft = 270,
            boxTop = 190,
            boxRight = 370,
            boxBottom = 290,
            scale = 2.7f
        )

        // Expected width & height = 100 * 2.7 = 270
        assertEquals(270, expanded.width)
        assertEquals(270, expanded.height)

        // Center must be preserved: cx = 320, cy = 240
        // left = 320 - 135 = 185, top = 240 - 135 = 105
        assertEquals(185, expanded.left)
        assertEquals(105, expanded.top)
        assertEquals(455, expanded.right)
        assertEquals(375, expanded.bottom)
    }

    @Test
    fun testExpandedBox_LeftEdgeOvershoot_ShiftsInwardWithoutDistortion() {
        val srcW = 640
        val srcH = 480

        val expanded = AntiSpoofingEngine.calculateExpandedBox(
            srcW = srcW,
            srcH = srcH,
            boxLeft = 20,
            boxTop = 150,
            boxRight = 120,
            boxBottom = 250,
            scale = 2.7f
        )

        // Must shift inward so left is 0, right is shifted to 270
        assertEquals(0, expanded.left)
        assertEquals(270, expanded.width)
        assertTrue(expanded.right <= srcW)
    }

    @Test
    fun testExpandedBox_TopEdgeOvershoot_ShiftsInwardWithoutDistortion() {
        val srcW = 640
        val srcH = 480

        val expanded = AntiSpoofingEngine.calculateExpandedBox(
            srcW = srcW,
            srcH = srcH,
            boxLeft = 200,
            boxTop = 20,
            boxRight = 300,
            boxBottom = 120,
            scale = 2.7f
        )

        assertEquals(0, expanded.top)
        assertEquals(270, expanded.height)
        assertTrue(expanded.bottom <= srcH)
    }

    @Test
    fun testExpandedBox_RightEdgeOvershoot_ShiftsInwardWithoutDistortion() {
        val srcW = 640
        val srcH = 480

        val expanded = AntiSpoofingEngine.calculateExpandedBox(
            srcW = srcW,
            srcH = srcH,
            boxLeft = 520,
            boxTop = 150,
            boxRight = 620,
            boxBottom = 250,
            scale = 2.7f
        )

        // Must shift left so right reaches srcW - 1 or srcW
        assertEquals(270, expanded.width)
        assertTrue(expanded.right <= srcW)
        assertTrue(expanded.left >= 0)
    }

    @Test
    fun testExpandedBox_BottomEdgeOvershoot_ShiftsInwardWithoutDistortion() {
        val srcW = 640
        val srcH = 480

        val expanded = AntiSpoofingEngine.calculateExpandedBox(
            srcW = srcW,
            srcH = srcH,
            boxLeft = 200,
            boxTop = 360,
            boxRight = 300,
            boxBottom = 460,
            scale = 2.7f
        )

        assertEquals(270, expanded.height)
        assertTrue(expanded.bottom <= srcH)
        assertTrue(expanded.top >= 0)
    }

    @Test
    fun testExpandedBox_TopLeftCorner_BothAxesShiftCorrectly() {
        val srcW = 640
        val srcH = 480

        val expanded = AntiSpoofingEngine.calculateExpandedBox(
            srcW = srcW,
            srcH = srcH,
            boxLeft = 0,
            boxTop = 0,
            boxRight = 80,
            boxBottom = 80,
            scale = 2.7f
        )

        val expectedDim = (80 * 2.7f).toInt()
        assertEquals(0, expanded.left)
        assertEquals(0, expanded.top)
        assertEquals(expectedDim, expanded.width)
        assertEquals(expectedDim, expanded.height)
    }

    @Test
    fun testExpandedBox_GiantFace_ClampsScaleGracefully() {
        val srcW = 640
        val srcH = 480

        val expanded = AntiSpoofingEngine.calculateExpandedBox(
            srcW = srcW,
            srcH = srcH,
            boxLeft = 50,
            boxTop = 50,
            boxRight = 450,
            boxBottom = 450,
            scale = 2.7f
        )

        assertTrue(expanded.left >= 0)
        assertTrue(expanded.top >= 0)
        assertTrue(expanded.right <= srcW)
        assertTrue(expanded.bottom <= srcH)
        // Aspect ratio of expanded crop must remain square (width == height)
        assertEquals(expanded.width, expanded.height)
    }

    @Test
    fun testFasClassEnum() {
        assertEquals(FasClass.PRINT_2D, FasClass.fromId(0))
        assertEquals(FasClass.REAL_FACE, FasClass.fromId(1))
        assertEquals(FasClass.SCREEN_OR_3D, FasClass.fromId(2))
        // Unknown id fallback
        assertEquals(FasClass.SCREEN_OR_3D, FasClass.fromId(99))
    }

    @Test
    fun testLivenessResultCsvFormatting() {
        val irMetrics = IrMetrics(
            meanFace = 85.5f,
            stdDevFace = 12.3f,
            meanBg = 35.0f,
            contrast = 2.44f,
            maxPixel = 210,
            satRatio = 0.02f,
            isPass = true,
            reason = "OK"
        )

        val result = LivenessResult(
            isLive = true,
            realScore = 0.985f,
            printScore = 0.010f,
            screenScore = 0.005f,
            predictedClass = FasClass.REAL_FACE,
            irMetrics = irMetrics,
            irPass = true,
            rgbFasPass = true,
            mode = LivenessMode.HYBRID_AND,
            inferenceTimeMs = 18L,
            rejectionReason = null
        )

        val csvLine = result.toCsvRow(timestampMs = 1000000L, trackingId = 42, personLabel = "SV_NguyenVanA")
        val headerTokens = LivenessResult.CSV_HEADER.split(",")
        val lineTokens = csvLine.split(",")

        assertEquals(headerTokens.size, lineTokens.size)
        assertEquals("1000000", lineTokens[0])
        assertEquals("42", lineTokens[1])
        assertEquals("SV_NguyenVanA", lineTokens[2])
        assertEquals("LIVE", lineTokens[3])
        assertEquals("0.9850", lineTokens[4])
        assertEquals("1", lineTokens[7]) // pred_class = 1 (REAL_FACE)
        assertEquals("true", lineTokens[8]) // ir_pass
        assertEquals("true", lineTokens[9]) // rgb_pass
        assertEquals("NONE", lineTokens[15]) // rejection reason
    }
}
