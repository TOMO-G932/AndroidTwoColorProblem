package com.example.twocolorproblem.model

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class CellParticle(
    val startX: Float,
    val startY: Float,
    val vx: Float,      // X方向の速度
    val vy: Float,      // Y方向の速度
    val color: Color,   // 粒子の色
    val size: Float,    // 粒子の大きさ
    val delayMs: Long   // 噴出開始の遅延時間(ms)
)

// 全マスの粒子を一括生成する関数
fun createGridParticles(
    rows: Int,
    cols: Int,
    cellSize: Int
): List<CellParticle> {
    val particles = mutableListOf<CellParticle>()
    val colors = listOf(
        Color(0xFFFFD700), // ゴールド
        Color(0xFFFFF59D), // 明るいイエロー
        Color(0xFFFFFFFF), // 輝く白
        Color(0xFFFFAB00)  // オレンジゴールド
    )

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            // セルの中心座標
            val centerX = (c + 0.5f) * cellSize
            val centerY = (r + 0.5f) * cellSize

            // 左上から右下へ向かうウェーブ遅延時間
            val delayMs = (r + c) * 60L

            // 1マスあたり 12 個の粒子を全方位へ射出
            repeat(12) {
                val angle = Random.nextFloat() * 2f * Math.PI.toFloat() // 360度ランダム
                val speed = Random.nextFloat() * 200f + 80f              // 飛散スピード
                val vx = cos(angle) * speed
                val vy = sin(angle) * speed

                particles.add(
                    CellParticle(
                        startX = centerX,
                        startY = centerY,
                        vx = vx,
                        vy = vy,
                        color = colors.random(),
                        size = Random.nextFloat() * 6f + 4f,
                        delayMs = delayMs
                    )
                )
            }
        }
    }
    return particles
}