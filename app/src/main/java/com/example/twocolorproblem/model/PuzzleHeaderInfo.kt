package com.example.twocolorproblem.model

data class PuzzleHeaderInfo(
    var puzzleID: String = "",
    var blockSizeX: List<Int> = emptyList(),
    var blockSizeY: List<Int> = emptyList()
)