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

object UserFaceRepository {

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
                val embedding = FloatArray(embArr.length())
                for (j in 0 until embArr.length()) {
                    embedding[j] = embArr.getDouble(j).toFloat()
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

    fun findBestMatch(liveEmbedding: FloatArray, threshold: Float = 0.65f): Pair<UserFaceModel, Float>? {
        if (userList.isEmpty() || liveEmbedding.isEmpty()) return null

        var bestUser: UserFaceModel? = null
        var maxSimilarity = -1f

        for (user in userList) {
            val similarity = calculateCosineSimilarity(liveEmbedding, user.embedding)
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity
                bestUser = user
            }
        }

        return if (bestUser != null && maxSimilarity >= threshold) {
            Pair(bestUser, maxSimilarity)
        } else {
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
        return (dotProduct / (sqrt(normA.toDouble()) * sqrt(normB.toDouble()))).toFloat()
    }
}
