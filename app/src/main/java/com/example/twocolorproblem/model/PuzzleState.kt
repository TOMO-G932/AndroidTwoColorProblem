package com.example.twocolorproblem.model


import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds


/**************************************************************************
 * パズルの問題と解答状態を管理するクラス
 **************************************************************************/
data class PuzzleState(
    // オブジェクト生成時に受け取った値で初期化される
    var isPuzzleSolved: Boolean,
    val puzzleData: List<List<Int>>,
    var answerData: Array<IntArray>
) {
    // バックグラウンド処理が多重起動されないように排他処理をおこなう
    val actionMutex = Mutex()
    private var isSolved: Array<Array<Boolean>> = Array(puzzleData.size) { r ->
        Array(puzzleData[r].size) { false }
    }
    var isError: Array<Array<Boolean>> = Array(puzzleData.size) { r ->
        Array(puzzleData[r].size) { false }
    }

    val row: Int get() = puzzleData.size
    val col: Int get() = puzzleData.firstOrNull()?.size ?: 0

    companion object {
        const val BLACK: Int = -1
        const val WHITE: Int = 1
    }
    // 固定値の宣言
    private val nextXY: Array<IntArray> = arrayOf(
        intArrayOf(0, -1),
        intArrayOf(1, 0),
        intArrayOf(0, 1),
        intArrayOf(-1, 0)
    )
    /**************************************************************************
     * オブジェクト生成時実行処理
     *    オブジェクト生成時の防御的バリデーション（不正な状態のオブジェクト生成を防止）
     **************************************************************************/
    init {
        require(puzzleData.isNotEmpty()) { "puzzleData が空です。" }
        val firstRowSize = puzzleData.first().size
        require(firstRowSize > 0) { "1行あたりの要素数が0です。" }
        require(puzzleData.all { row -> row.size == firstRowSize }) {
            "puzzleData の各行のサイズ（列数）が統一されていません。"
        }
    }
    /**************************************************************************
    * 指定されたセルを黒に変更する
    * UIスレッドから呼び出すことを前提としている
    * @param r 行番号
    * @param c 列番号
    * @return ミューテックスの取得ができず、処理ができなかったらfalse
    **************************************************************************/
    fun changeBlack(r: Int, c: Int): Boolean {
        if (!actionMutex.tryLock()) return false
        if (r in 0 until row && c in 0 until col) {
            if (r != 0 || c != 0) {
                answerData[r][c] = BLACK
                for (row in isSolved) {
                    row.fill(false)
                }
            }
        }
        actionMutex.unlock()
        return true
    }
    /**************************************************************************
     * 指定されたセルを白に変更する
     * UIスレッドから呼び出すことを前提としている
     * @param r 行番号
     * @param c 列番号
     * @return ミューテックスの取得ができず、処理ができなかったらfalse
     **************************************************************************/
    fun changeWhite(r: Int, c: Int): Boolean {
        if (!actionMutex.tryLock()) return false
        if (r in 0 until row && c in 0 until col) {
            if (r != 0 || c != 0) {
                answerData[r][c] = WHITE
                for (row in isSolved) {
                    row.fill(false)
                }
            }
        }
        actionMutex.unlock()
        return true
    }
    /**************************************************************************
     * 指定されたセルを未設定に変更する
     * UIスレッドから呼び出すことを前提としている
     * @param r 行番号
     * @param c 列番号
     * @return ミューテックスの取得ができず、処理ができなかったらfalse
     **************************************************************************/
    fun cancelColor(r: Int, c: Int): Boolean {
        if (!actionMutex.tryLock()) return false
        if (r in 0 until row && c in 0 until col) {
            if (r != 0 || c != 0) {
                answerData[r][c] = 0
                if (puzzleData[r][c] != 0) isError[r][c] = false
            }
        }
        actionMutex.unlock()
        return true
    }

    /**************************************************************************
     * 指定された範囲のセルを反転する
     * Defaultスレッドから呼び出すことを前提としている
     * @param minR 最小列番号
     * @param maxR 最大列番号
     * @param minC 最小行番号
     * @param maxC 最大行番号
     **************************************************************************/
    suspend fun doReverseColor(minR: Int, maxR: Int, minC: Int, maxC: Int) {
        actionMutex.withLock {
            for (r in minR..maxR) {
                for (c in minC..maxC) {
                    if (r in 0 until row && c in 0 until col) {
                        if (r != 0 || c != 0) {
                            answerData[r][c] = -answerData[r][c]
                        }
                    }
                }
            }
        }
    }

    /**************************************************************************
     * 指定された範囲のセルを未回答状態に戻す
     * Defaultスレッドから呼び出すことを前提としている
     * @param minR 最小列番号
     * @param maxR 最大列番号
     * @param minC 最小行番号
     * @param maxC 最大行番号
     */
    suspend fun doCancelColor(minR: Int, maxR: Int, minC: Int, maxC: Int) {
        actionMutex.withLock {
            for (r in minR..maxR) {
                for (c in minC..maxC) {
                    if (r in 0 until row && c in 0 until col) {
                        if (r != 0 || c != 0) {
                            answerData[r][c] = 0
                            if (puzzleData[r][c] != 0) isError[r][c] = false
                        }
                    }
                }
            }
        }
    }
    /**************************************************************************
     * 指定されたセルを起点に問題が1になっているセルの周りの白黒を自動的に解決する
     * @param r 行番号
     * @param c 列番号
     * @param color 色（黒または白）
     * @param onUpdateStep ひとつ解決するごとに表示するためのコールバック
     **************************************************************************/
    suspend fun doAutoSolve(r: Int, c: Int, color: Int, onUpdateStep: suspend () -> Unit) {
        actionMutex.withLock {
            autoSolve(r, c, color, onUpdateStep)
        }
    }
    /***************************************************************************
     * 自動解決処理の再帰呼び出し部分の関数
     * @param r 行番号
     * @param c 列番号
     * @param color 色（黒または白）
     * @param onUpdateStep ひとつ解決するごとに表示するためのコールバック
     ***************************************************************************/
    private suspend fun autoSolve(r: Int, c: Int, color: Int, onUpdateStep: suspend () -> Unit) {
        if (puzzleData[r][c] == 1) {
            for (i in nextXY.indices) {
                val nextR = r + nextXY[i][0]
                val nextC = c + nextXY[i][1]
                if (nextR in 0 until row && nextC in 0 until col) {
                    if ((nextR != 0 || nextC != 0) && !isSolved[nextR][nextC]) {
                        answerData[nextR][nextC] = -color
                        isSolved[nextR][nextC] = true
                        onUpdateStep()
                        delay(50.milliseconds)
                        autoSolve(nextR, nextC, -color, onUpdateStep)
                    }
                }
            }
        } else {
            for (i in nextXY.indices) {
                val nextR = r + nextXY[i][0]
                val nextC = c + nextXY[i][1]
                if (nextR in 0 until row && nextC in 0 until col) {
                    if ((nextR != 0 || nextC != 0) && !isSolved[nextR][nextC] && puzzleData[nextR][nextC] == 1) {
                        answerData[nextR][nextC] = -color
                        isSolved[nextR][nextC] = true
                        onUpdateStep()
                        delay(50.milliseconds)
                        autoSolve(nextR, nextC, -color, onUpdateStep)
                    }
                }
            }
        }
    }
    /**************************************************************************
     * 解答結果の妥当性を解答データ全体に対して行う
     * @param onUpdateStep 数字のエラー表示するためのコールバック
     **************************************************************************/
    suspend fun validateSolution(onUpdateStep: suspend () -> Unit) {
        actionMutex.withLock {
            for (row in isSolved) {
                row.fill(false)
            }
            for (r in 0 until row) {
                for (c in 0 until col) {
                    val errorList = mutableListOf<IntArray>()
                    val count = IntArray(2)
                    val errorFlag = countCells(r, c, count, errorList)
                    if (errorFlag) {
                        for (error in errorList) {
                            if (!isError[error[0]][error[1]]) {
                                isError[error[0]][error[1]] = true
                                onUpdateStep()
                            }
                        }
                    }
                    else {
                        if (errorList.size == 1 && count[1] == 0 && count[0] != puzzleData[errorList[0][0]][errorList[0][1]]) {
                            if (!isError[errorList[0][0]][errorList[0][1]]) {
                                isError[errorList[0][0]][errorList[0][1]] = true
                                onUpdateStep()
                            }
                        }
                        else {
                            for (error in errorList) {
                                if (isError[error[0]][error[1]]) {
                                    isError[error[0]][error[1]] = false
                                    onUpdateStep()
                                }
                            }
                        }
                    }
                }
            }
            // パズル終了チェック
            if (isError.none { true in it} && answerData.none { 0 in it }){
                isPuzzleSolved = true
                onUpdateStep()
            }
        }
    }
    /**************************************************************************
     * 連続するセルの数を数える
     * @param r 行番号
     * @param c 列番号
     * @param count 連続セルの数[0]と隣接する未決定セルの数[1]を格納する配列
     * @param errorList エラーになったセルのリスト
     **************************************************************************/
    private fun countCells(r: Int, c: Int, count: IntArray, errorList: MutableList<IntArray>): Boolean {
        var errorFlag = false
        if (!isSolved[r][c] && answerData[r][c] != 0) {
            count[0]++
            isSolved[r][c] = true
            if (puzzleData[r][c] != 0) errorList.add(intArrayOf(r, c))
            if (errorList.size > 1) errorFlag = true
            if (errorList.size == 1 && count[0] > puzzleData[errorList[0][0]][errorList[0][1]]) errorFlag =
                true
            for (i in nextXY.indices) {
                val nextR = r + nextXY[i][0]
                val nextC = c + nextXY[i][1]
                if (nextR in 0 until row && nextC in 0 until col) {
                    if (answerData[nextR][nextC] == 0) count[1]++
                    if (!isSolved[nextR][nextC] && answerData[nextR][nextC] == answerData[r][c]) {
                        val hasError = countCells(nextR, nextC, count, errorList)
                        errorFlag = errorFlag || hasError   // 直接countCells()とするとerrorFlagがtrue時、countCells()が実行されない
                    }
                }
            }
        }
        return errorFlag
    }
    /**************************************************************************
     * PuzzleState オブジェクト同士を比較する composeが使うけど、多分使われない
     * @param other 比較するオブジェクト
     **************************************************************************/
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PuzzleState

        return answerData.contentDeepEquals(other.answerData)
    }
    /**************************************************************************
     * PuzzleState 変化を検知するためのハッシュコードを生成する composeが使うけど、多分使われない
     **************************************************************************/
    override fun hashCode(): Int {
        val result = answerData.contentDeepHashCode()
        return result
    }
}
