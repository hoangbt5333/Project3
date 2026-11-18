package com.example.testcamera.ml

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Các kịch bản kiểm thử giả mạo (Presentation Attack Scenarios) theo chuẩn ISO/IEC 30107-3.
 */
enum class AttackScenario(val code: String, val displayName: String, val isBonaFide: Boolean) {
    REAL_FACE("REAL", "Bona Fide (Người thật)", true),
    PHONE_SCREEN("PHONE_SCREEN", "Màn hình điện thoại (Replay)", false),
    TABLET_SCREEN("TABLET_SCREEN", "Màn hình Tablet/iPad", false),
    PRINT_LASER_COLOR("PRINT_LASER", "Ảnh in Laser màu (A4)", false),
    PRINT_INKJET_COLOR("PRINT_INKJET", "Ảnh in Phun màu (A4)", false),
    PRINT_BW("PRINT_BW", "Ảnh in đen trắng (A4)", false),
    GLOSSY_PHOTO("GLOSSY_PHOTO", "Ảnh rửa giấy bóng (Photo)", false),
    CUTOUT_FACE("CUTOUT_FACE", "Ảnh cắt rời vùng mặt", false);

    fun next(): AttackScenario {
        val vals = values()
        return vals[(ordinal + 1) % vals.size]
    }
}

/**
 * Khoảng cách vật lý từ khuôn mặt đến cụm camera Kiosk.
 */
enum class TestDistance(val cm: Int, val label: String) {
    DIST_40CM(40, "40 cm (Gần)"),
    DIST_60CM(60, "60 cm (Chuẩn Kiosk)"),
    DIST_80CM(80, "80 cm (Xa)");

    fun next(): TestDistance {
        val vals = values()
        return vals[(ordinal + 1) % vals.size]
    }
}

/**
 * Dữ liệu thống kê hiệu năng Anti-Spoofing theo chuẩn ISO/IEC 30107-3:
 * - BPCER: Bona Fide Presentation Classification Error Rate (Tỉ lệ người thật bị từ chối sai - False Rejection).
 * - APCER: Attack Presentation Classification Error Rate (Tỉ lệ tấn công giả mạo lọt qua - False Acceptance).
 * - ACER: Average Classification Error Rate = (APCER + BPCER) / 2.
 */
data class AttackStats(
    val total: Int,
    val falseAcceptCount: Int,
    val apcer: Float
)

data class BenchmarkStats(
    val totalSamples: Int,
    val bonaFideCount: Int,
    val bonaFidePassCount: Int,
    val bonaFideFalseRejectCount: Int,
    val bpcer: Float,
    val attackCount: Int,
    val attackRejectCount: Int,
    val attackFalseAcceptCount: Int,
    val apcer: Float,
    val acer: Float,
    val attackBreakdown: Map<AttackScenario, AttackStats>
)

/**
 * Bản ghi chi tiết của một lần kiểm thử liveness.
 */
data class BenchmarkRecord(
    val timestamp: Long,
    val dateTimeStr: String,
    val sessionId: String,
    val scenario: AttackScenario,
    val distance: TestDistance,
    val mode: LivenessMode,
    val trackingId: Int,
    val isLiveDecision: Boolean,
    val isCorrect: Boolean,
    val realScore: Float,
    val printScore: Float,
    val screenScore: Float,
    val predictedClassId: Int,
    val irPass: Boolean,
    val rgbPass: Boolean,
    val irContrast: Float,
    val irMeanFace: Float,
    val irMeanBg: Float,
    val irSatRatio: Float,
    val latencyMs: Long,
    val rejectionReason: String
) {
    fun toCsvRow(): String {
        return String.format(
            Locale.US,
            "%d,%s,%s,%s,%s,%b,%d,%s,%d,%s,%b,%.4f,%.4f,%.4f,%d,%b,%b,%.2f,%.1f,%.1f,%.3f,%d,%s",
            timestamp,
            dateTimeStr,
            sessionId,
            scenario.code,
            scenario.displayName.replace(",", " "),
            scenario.isBonaFide,
            distance.cm,
            mode.name,
            trackingId,
            if (isLiveDecision) "PASS" else "FAIL",
            isCorrect,
            realScore,
            printScore,
            screenScore,
            predictedClassId,
            irPass,
            rgbPass,
            irContrast,
            irMeanFace,
            irMeanBg,
            irSatRatio,
            latencyMs,
            rejectionReason.replace(",", ";")
        )
    }
}

/**
 * Quản lý ghi nhận, lưu trữ file CSV và tính toán chỉ số đánh giá thực nghiệm (APCER/BPCER).
 */
class LivenessBenchmarkLogger private constructor(private val appContext: Context) {

    var currentScenario: AttackScenario = AttackScenario.REAL_FACE
    var currentDistance: TestDistance = TestDistance.DIST_60CM
    var isRecording: Boolean = false
        private set

    private var currentSessionId: String = generateSessionId()
    private val recordsList = mutableListOf<BenchmarkRecord>()

    companion object {
        const val CSV_FILE_NAME = "liveness_benchmark.csv"
        const val CSV_HEADER = "timestamp,datetime,session_id,ground_truth,attack_type,is_bona_fide,distance_cm,mode,tracking_id,decision,is_correct,real_score,print_score,screen_score,pred_class,ir_pass,rgb_pass,ir_contrast,ir_mean_face,ir_mean_bg,ir_sat_ratio,latency_ms,rejection_reason"

        @Volatile
        private var instance: LivenessBenchmarkLogger? = null

        fun getInstance(context: Context): LivenessBenchmarkLogger {
            return instance ?: synchronized(this) {
                instance ?: LivenessBenchmarkLogger(context.applicationContext).also { instance = it }
            }
        }

        fun generateSessionId(): String = UUID.randomUUID().toString().take(8)

        /**
         * Hàm tính toán thống kê thuần túy (dễ test trên JVM độc lập).
         */
        fun computeStats(records: List<BenchmarkRecord>): BenchmarkStats {
            val total = records.size
            if (total == 0) {
                return BenchmarkStats(
                    totalSamples = 0,
                    bonaFideCount = 0,
                    bonaFidePassCount = 0,
                    bonaFideFalseRejectCount = 0,
                    bpcer = 0f,
                    attackCount = 0,
                    attackRejectCount = 0,
                    attackFalseAcceptCount = 0,
                    apcer = 0f,
                    acer = 0f,
                    attackBreakdown = emptyMap()
                )
            }

            var bonaFideCount = 0
            var bonaFidePassCount = 0
            var bonaFideFalseReject = 0

            var attackCount = 0
            var attackRejectCount = 0
            var attackFalseAccept = 0

            val attackGroups = mutableMapOf<AttackScenario, Pair<Int, Int>>() // total to falseAccept

            for (r in records) {
                if (r.scenario.isBonaFide) {
                    bonaFideCount++
                    if (r.isLiveDecision) {
                        bonaFidePassCount++
                    } else {
                        bonaFideFalseReject++
                    }
                } else {
                    attackCount++
                    val current = attackGroups.getOrDefault(r.scenario, Pair(0, 0))
                    if (r.isLiveDecision) {
                        attackFalseAccept++
                        attackGroups[r.scenario] = Pair(current.first + 1, current.second + 1)
                    } else {
                        attackRejectCount++
                        attackGroups[r.scenario] = Pair(current.first + 1, current.second)
                    }
                }
            }

            val bpcer = if (bonaFideCount > 0) bonaFideFalseReject.toFloat() / bonaFideCount else 0f
            val apcer = if (attackCount > 0) attackFalseAccept.toFloat() / attackCount else 0f
            val acer = (bpcer + apcer) / 2f

            val breakdown = mutableMapOf<AttackScenario, AttackStats>()
            for ((scenario, counts) in attackGroups) {
                val scTotal = counts.first
                val scFa = counts.second
                val scApcer = if (scTotal > 0) scFa.toFloat() / scTotal else 0f
                breakdown[scenario] = AttackStats(total = scTotal, falseAcceptCount = scFa, apcer = scApcer)
            }

            return BenchmarkStats(
                totalSamples = total,
                bonaFideCount = bonaFideCount,
                bonaFidePassCount = bonaFidePassCount,
                bonaFideFalseRejectCount = bonaFideFalseReject,
                bpcer = bpcer,
                attackCount = attackCount,
                attackRejectCount = attackRejectCount,
                attackFalseAcceptCount = attackFalseAccept,
                apcer = apcer,
                acer = acer,
                attackBreakdown = breakdown
            )
        }
    }

    fun startRecording(): String {
        isRecording = true
        currentSessionId = generateSessionId()
        return currentSessionId
    }

    fun stopRecording() {
        isRecording = false
    }

    fun toggleRecording(): Boolean {
        isRecording = !isRecording
        if (isRecording) {
            currentSessionId = generateSessionId()
        }
        return isRecording
    }

    fun getBenchmarkFile(): File {
        val dir = appContext.getExternalFilesDir("benchmark") ?: appContext.filesDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, CSV_FILE_NAME)
    }

    @Synchronized
    fun recordSample(
        result: LivenessResult,
        trackingId: Int
    ): BenchmarkRecord? {
        if (!isRecording) return null

        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))
        val isCorrect = if (currentScenario.isBonaFide) result.isLive else !result.isLive

        val record = BenchmarkRecord(
            timestamp = now,
            dateTimeStr = dateStr,
            sessionId = currentSessionId,
            scenario = currentScenario,
            distance = currentDistance,
            mode = result.mode,
            trackingId = trackingId,
            isLiveDecision = result.isLive,
            isCorrect = isCorrect,
            realScore = result.realScore,
            printScore = result.printScore,
            screenScore = result.screenScore,
            predictedClassId = result.predictedClass.id,
            irPass = result.irPass,
            rgbPass = result.rgbFasPass,
            irContrast = result.irMetrics?.contrast ?: 0f,
            irMeanFace = result.irMetrics?.meanFace ?: 0f,
            irMeanBg = result.irMetrics?.meanBg ?: 0f,
            irSatRatio = result.irMetrics?.satRatio ?: 0f,
            latencyMs = result.inferenceTimeMs,
            rejectionReason = result.rejectionReason ?: "NONE"
        )

        recordsList.add(record)
        appendRecordToFile(record)
        return record
    }

    @Synchronized
    private fun appendRecordToFile(record: BenchmarkRecord) {
        try {
            val file = getBenchmarkFile()
            val fileExists = file.exists() && file.length() > 0
            FileWriter(file, true).use { writer ->
                if (!fileExists) {
                    writer.append(CSV_HEADER).append("\n")
                }
                writer.append(record.toCsvRow()).append("\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun getInMemoryStats(): BenchmarkStats {
        return computeStats(recordsList)
    }

    @Synchronized
    fun clearAllData(): Boolean {
        recordsList.clear()
        return try {
            val file = getBenchmarkFile()
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getRecordsCount(): Int = recordsList.size

    fun getFormattedSummary(): String {
        val stats = getInMemoryStats()
        if (stats.totalSamples == 0) {
            return "Chưa có mẫu nào được ghi."
        }
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "📊 Tổng: %d mẫu | Real: %d | Attack: %d\n", stats.totalSamples, stats.bonaFideCount, stats.attackCount))
        sb.append(String.format(Locale.US, "📈 BPCER (Từ chối mặt thật): %.1f%% (%d/%d)\n", stats.bpcer * 100f, stats.bonaFideFalseRejectCount, stats.bonaFideCount))
        sb.append(String.format(Locale.US, "⚠️ APCER (Lọt giả mạo): %.1f%% (%d/%d)\n", stats.apcer * 100f, stats.attackFalseAcceptCount, stats.attackCount))
        sb.append(String.format(Locale.US, "🎯 ACER: %.1f%%\n", stats.acer * 100f))

        if (stats.attackBreakdown.isNotEmpty()) {
            sb.append("--- Chi tiết theo loại tấn công ---\n")
            for ((sc, ast) in stats.attackBreakdown) {
                sb.append(String.format(Locale.US, "• %s: APCER=%.1f%% (%d/%d)\n", sc.displayName, ast.apcer * 100f, ast.falseAcceptCount, ast.total))
            }
        }
        return sb.toString().trimEnd()
    }
}
