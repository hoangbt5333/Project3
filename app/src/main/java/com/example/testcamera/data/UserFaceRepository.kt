package com.example.testcamera.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

data class UserFaceModel(
    val studentId: String,
    val name: String,
    val embedding: FloatArray,
    val registeredTimestamp: Long = System.currentTimeMillis()
) {
    val id: String get() = studentId

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UserFaceModel
        return studentId == other.studentId
    }

    override fun hashCode(): Int {
        return studentId.hashCode()
    }
}

data class FaceMatchResult(
    val user: UserFaceModel,
    val similarity: Float,
    val secondSimilarity: Float = -1f,
    val margin: Float = 1.0f
) {
    // Backward compatibility with Pair<UserFaceModel, Float>
    val first: UserFaceModel get() = user
    val second: Float get() = similarity
}

object UserFaceRepository {

    const val DEFAULT_MATCH_THRESHOLD = 0.70f
    const val DEFAULT_MIN_MARGIN = 0.08f
    const val DEFAULT_DUPLICATE_THRESHOLD = 0.75f

    private const val DB_FILE_NAME = "registered_faces.json"
    private val userList = mutableListOf<UserFaceModel>()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        loadFromDisk(context)
        isInitialized = true
    }

    private fun loadFromDisk(context: Context) {
        userList.clear()
        try {
            val file = File(context.filesDir, DB_FILE_NAME)
            if (!file.exists()) return

            val jsonStr = file.readText(Charsets.UTF_8)
            if (jsonStr.isBlank()) return

            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val studentId = obj.optString("studentId", obj.optString("id", "USR_${System.currentTimeMillis()}"))
                val name = obj.optString("name", "Sinh viên $studentId")
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())

                val embArr = obj.getJSONArray("embedding")
                if (embArr.length() != 192) {
                    try {
                        android.util.Log.w("UserFaceRepository", "⚠️ Skipping legacy or incompatible embedding for student: $studentId (dimension=${embArr.length()}, expected 192)")
                    } catch (_: Exception) {}
                    continue
                }

                val embedding = FloatArray(embArr.length())
                for (j in 0 until embArr.length()) {
                    embedding[j] = embArr.getDouble(j).toFloat()
                }

                // Check for NaN or all zeros
                if (embedding.any { it.isNaN() } || embedding.all { it == 0f }) {
                    try {
                        android.util.Log.w("UserFaceRepository", "⚠️ Skipping corrupt embedding (NaN or all zeros) for student: $studentId")
                    } catch (_: Exception) {}
                    continue
                }

                userList.add(UserFaceModel(studentId, name, embedding, timestamp))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveToDisk(context: Context) {
        try {
            val file = File(context.filesDir, DB_FILE_NAME)
            val jsonArray = JSONArray()

            for (user in userList) {
                val obj = JSONObject().apply {
                    put("studentId", user.studentId)
                    put("id", user.studentId)
                    put("name", user.name)
                    put("timestamp", user.registeredTimestamp)

                    val embArr = JSONArray()
                    for (v in user.embedding) {
                        embArr.put(v.toDouble())
                    }
                    put("embedding", embArr)
                }
                jsonArray.put(obj)
            }

            file.writeText(jsonArray.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun registerStudentFace(context: Context, studentId: String, name: String, embedding: FloatArray): UserFaceModel {
        require(embedding.size == 192) { "Embedding dimension must be exactly 192 (got ${embedding.size})" }
        require(!embedding.any { it.isNaN() }) { "Embedding contains NaN values" }
        require(embedding.any { it != 0f }) { "Embedding cannot be all zeros" }

        init(context)
        val cleanId = studentId.trim()
        val cleanName = name.trim()
        val model = UserFaceModel(cleanId, cleanName, embedding)

        val index = userList.indexOfFirst { it.studentId.equals(cleanId, ignoreCase = true) }
        if (index != -1) {
            userList[index] = model
        } else {
            userList.add(model)
        }

        saveToDisk(context)
        return model
    }

    fun updateStudentFace(context: Context, updatedUser: UserFaceModel) {
        init(context)
        val index = userList.indexOfFirst { it.studentId.equals(updatedUser.studentId, ignoreCase = true) }
        if (index != -1) {
            userList[index] = updatedUser
        } else {
            userList.add(updatedUser)
        }
        saveToDisk(context)
    }

    fun findUserByStudentId(studentId: String): UserFaceModel? {
        val targetId = studentId.trim()
        return userList.firstOrNull { it.studentId.equals(targetId, ignoreCase = true) }
    }

    fun findUserByName(name: String): UserFaceModel? {
        val searchName = name.trim()
        return userList.firstOrNull { it.name.equals(searchName, ignoreCase = true) }
    }

    fun checkDuplicateFace(embedding: FloatArray, duplicateThreshold: Float = 0.72f): Pair<UserFaceModel, Float>? {
        if (userList.isEmpty() || embedding.isEmpty()) return null

        var bestUser: UserFaceModel? = null
        var maxSimilarity = -1f

        for (user in userList) {
            val similarity = calculateCosineSimilarity(embedding, user.embedding)
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity
                bestUser = user
            }
        }

        return if (bestUser != null && maxSimilarity >= duplicateThreshold) {
            Pair(bestUser, maxSimilarity)
        } else {
            null
        }
    }

    fun getAllUsers(context: Context): List<UserFaceModel> {
        init(context)
        return userList.toList()
    }

    fun getUserCount(context: Context? = null): Int {
        if (context != null) init(context)
        return userList.size
    }

    fun clearAllUsers(context: Context) {
        userList.clear()
        try {
            val file = File(context.filesDir, DB_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetDatabase(context: Context) {
        clearAllUsers(context)
        isInitialized = false
        init(context)
    }

    fun addUserForTesting(user: UserFaceModel) {
        userList.add(user)
    }

    fun clearUsersForTesting() {
        userList.clear()
    }

    fun findBestMatch(
        liveEmbedding: FloatArray,
        threshold: Float = DEFAULT_MATCH_THRESHOLD,
        minMargin: Float = DEFAULT_MIN_MARGIN
    ): FaceMatchResult? {
        if (userList.isEmpty() || liveEmbedding.isEmpty()) return null

        var top1User: UserFaceModel? = null
        var top1Score = -1f

        var top2User: UserFaceModel? = null
        var top2Score = -1f

        for (user in userList) {
            val similarity = calculateCosineSimilarity(liveEmbedding, user.embedding)
            if (similarity > top1Score) {
                // Shift top1 to top2
                top2Score = top1Score
                top2User = top1User

                top1Score = similarity
                top1User = user
            } else if (similarity > top2Score) {
                top2Score = similarity
                top2User = user
            }
        }

        if (top1User == null || top1Score < threshold) {
            return null
        }

        val margin = if (userList.size > 1 && top2Score > -1f) (top1Score - top2Score) else 1.0f

        // When multiple users are enrolled, require sufficient margin between Rank-1 and Rank-2
        // to prevent false acceptance on ambiguous/impostor faces
        return if (userList.size <= 1 || margin >= minMargin) {
            FaceMatchResult(
                user = top1User,
                similarity = top1Score,
                secondSimilarity = top2Score,
                margin = margin
            )
        } else {
            try {
                android.util.Log.w(
                    "UserFaceRepository",
                    "⚠️ Ambiguous match rejected: Top-1=${top1User.studentId} ($top1Score), Top-2=${top2User?.studentId} ($top2Score), Margin=$margin < $minMargin"
                )
            } catch (_: Exception) {
                // Ignore during unit tests without android log mock
            }
            null
        }
    }

    fun calculateCosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in vecA.indices) {
            dotProduct += vecA[i] * vecB[i]
            normA += vecA[i] * vecA[i]
            normB += vecB[i] * vecB[i]
        }

        if (normA == 0f || normB == 0f) return 0f
        val denom = sqrt(normA.toDouble()) * sqrt(normB.toDouble())
        if (denom == 0.0 || denom.isNaN()) return 0f
        return (dotProduct / denom).toFloat().coerceIn(-1.0f, 1.0f)
    }
}
