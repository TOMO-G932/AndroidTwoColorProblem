package com.example.twocolorproblem.model

import kotlinx.serialization.Serializable

@Serializable
data class Puzzle(
    val puzzleID: String = "",
    val blockSizeX: List<Int> = emptyList(),
    val blockSizeY: List<Int> = emptyList(),
    val leftTop: List<List<Int>> = emptyList(),
    val puzzle: List<List<Int>> = emptyList()
)
