package com.example.testcamera.data

import kotlin.math.sqrt

data class UserFaceModel(
    val id: String,
    val name: String,
    val embedding: FloatArray,
    val registeredTimestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UserFaceModel
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

object UserFaceRepository {

    private val userList = mutableListOf<UserFaceModel>()

    fun registerUser(name: String, embedding: FloatArray): UserFaceModel {
        val newId = "USR_${System.currentTimeMillis()}"
        val model = UserFaceModel(newId, name, embedding)
        userList.add(model)
        return model
    }

    fun getAllUsers(): List<UserFaceModel> {
        return userList.toList()
    }

    fun getUserCount(): Int {
        return userList.size
    }

    fun clearAllUsers() {
        userList.clear()
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
