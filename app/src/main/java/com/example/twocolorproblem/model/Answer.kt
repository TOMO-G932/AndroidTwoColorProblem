package com.example.twocolorproblem.model

import kotlinx.serialization.Serializable

@Serializable
data class Answer(
    val puzzleData: List<List<Int>> = emptyList(),
    val row: Int = 0,
    val col: Int = 0
)
