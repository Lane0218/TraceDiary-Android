package com.tracediary.app.data.ai

import kotlin.math.sqrt

object AiVectorMath {
    fun normalize(vector: FloatArray): FloatArray {
        var squareSum = 0.0
        vector.forEach { value -> squareSum += value * value }
        val length = sqrt(squareSum).toFloat()
        if (length == 0f) return vector.copyOf()
        return FloatArray(vector.size) { index -> vector[index] / length }
    }

    fun cosine(left: FloatArray, right: FloatArray): Float {
        require(left.size == right.size) { "向量维度不一致" }
        var dot = 0f
        var leftSquare = 0f
        var rightSquare = 0f
        for (index in left.indices) {
            dot += left[index] * right[index]
            leftSquare += left[index] * left[index]
            rightSquare += right[index] * right[index]
        }
        if (leftSquare == 0f || rightSquare == 0f) return 0f
        return (dot / sqrt(leftSquare * rightSquare))
    }
}
