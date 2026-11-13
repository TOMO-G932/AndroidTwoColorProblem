package com.example.twocolorproblem.model

import kotlinx.serialization.Serializable
@Serializable
data class AplState(
    val puzzleID: String = "",
    val blockID: String = ""
)
