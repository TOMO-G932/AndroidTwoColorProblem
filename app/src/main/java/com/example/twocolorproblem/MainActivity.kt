package com.example.twocolorproblem

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.JsonReader
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.twocolorproblem.model.Answer
import com.example.twocolorproblem.model.AplState
import com.example.twocolorproblem.model.CellParticle
import com.example.twocolorproblem.model.Puzzle
import com.example.twocolorproblem.model.PuzzleHeaderInfo
import com.example.twocolorproblem.model.PuzzleState
import com.example.twocolorproblem.model.createGridParticles
import com.example.twocolorproblem.ui.theme.TwoColorProblemTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/******************************************************************************
 * Jsonファイルのプロパティを設定する
 ******************************************************************************/
private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
}

/******************************************************************************
 * メインアクティビティクラス
 *   起動イベントonCreate()を受けてメインアプリ処理をおこなう
 ******************************************************************************/
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TwoColorProblemTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFDAFBDC)
                ){
                    val filesDir = applicationContext.filesDir
                    MainApp(filesDir)
                }
            }
        }
    }
}

/******************************************************************************
 * 起動時に呼び出されるメイン処理
 * @param filesDir アプリケーションのファイルディレクトリ
 ******************************************************************************/
@Composable
fun MainApp(filesDir: File) {
    var aplState by remember { mutableStateOf<AplState?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val stateJsonFile = File(filesDir, "AplState.json")

    LaunchedEffect(filesDir) {
        withContext(Dispatchers.IO) {
            try {
                if (!stateJsonFile.exists()) {
                    val initialState = AplState()
                    stateJsonFile.writeText(json.encodeToString(initialState)) // インデント付きで整形保存
                    aplState = initialState
                } else {
                    val jsonText = stateJsonFile.readText()
                    val loadedState = json.decodeFromString<AplState>(jsonText)
                    aplState = loadedState
                }
            } catch (e: Exception) {
                e.printStackTrace()
                aplState = AplState(puzzleID = "")
            }
}      // aplState に "AplState.json"ファイルを読み込む
    }

    // --- 画面分岐ロジック ---
    val currentState = aplState         // スマートキャストすることで!!付けなくても大丈夫

    when {
        // 1. 読み込み中
        currentState == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }       // クルクル表示
        }

        // 2. puzzleID が空 "" の場合 → thumbnails フォルダからパズル選択画面を表示
        currentState.puzzleID.isEmpty() -> {
            PuzzleSelectionScreen(
                filesDir = filesDir,
                onPuzzleSelected = { selectedPuzzleID ->
                    val newState = AplState(puzzleID = selectedPuzzleID)
                    aplState = newState
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            stateJsonFile.writeText(json.encodeToString(newState))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            )           // サムネイルを表示するパズル選択画面
        }
        // 3. puzzleIDが入っていて、blockIDが入っていない場合 -> ブロック選択画面を表示
        currentState.blockID.isEmpty() -> {
            BlockSelectionScreen(
                filesDir = filesDir,
                puzzleID = currentState.puzzleID,
                onBackToSelectPuzzle = {
                    // パズル選択に戻る場合（puzzleID を空にして保存）
                    val newState = AplState(puzzleID = "")
                    aplState = newState
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            stateJsonFile.writeText(json.encodeToString(newState))
                        }
                        catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                onBlockSelected = { blockID -> String()
                    val newState = currentState.copy(blockID = blockID)
                    aplState = newState
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            stateJsonFile.writeText(json.encodeToString(newState))
                        }
                        catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            )           // 解き終わった部分を画像表示するブロック選択画面
        }
        // 4. blockID が入っている場合 -> ブロックプレイ画面を表示
        else -> {
            PuzzleBlockPlayScreen(
                filesDir = filesDir,
                puzzleID = currentState.puzzleID,
                blockID = currentState.blockID,
                onBackToSelectBlock = { puzzleState ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            // パズル選択に戻る場合（puzzleID を空にして保存）
                            val newState = currentState.copy(blockID = "")
                            stateJsonFile.writeText(json.encodeToString(newState))
                            modifySolvingBinary(filesDir, currentState.puzzleID, currentState.blockID, puzzleState)
                            withContext(Dispatchers.Main) {
                                aplState = newState
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            )           // 白黒セルを表示するブロックプレイ画面
        }
    }
}

/******************************************************************************
 * パズル選択画面表
 ******************************************************************************/
@Composable
fun PuzzleSelectionScreen(
    filesDir: File,
    onPuzzleSelected: (String) -> Unit
) {
    var pngFiles by remember { mutableStateOf<List<File>?>(null) }

    // thumbnails フォルダ内の png ファイル一覧を取得
    LaunchedEffect(filesDir) {
        withContext(Dispatchers.IO) {
            val thumbnailsDir = File(filesDir, "thumbnails")
            if (!thumbnailsDir.exists()) {
                thumbnailsDir.mkdirs() // ディレクトリがなければ作成
            }
            pngFiles = thumbnailsDir.listFiles { file ->
                file.isFile && file.extension.equals("png", ignoreCase = true)
            }?.toList() ?: emptyList()
        }       // pngFiles に thumbnails フォルダ内の png ファイル一覧を保存
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        val currentPngFiles = pngFiles      // スマートキャストすることで!!付けなくても大丈夫
        Text(text = "解きたいパズルを選択してください", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        if (currentPngFiles == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator() // クルクル回るローディング表示
            }
        } else if (currentPngFiles.isEmpty()) {
            Text(text = "thumbnails フォルダに PNG ファイルが見つかりません。")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),           // <- この設定でpngを横に並べる数が決まる
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(currentPngFiles) { file ->
                    // ファイル名（拡張子なし）を puzzleID として使用
                    val puzzleID = file.nameWithoutExtension

                    // PNG画像を表示するカードUI（クリック可能）
                    Card(
                        onClick = { onPuzzleSelected(puzzleID) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            // PNG ファイルを ImageBitmap にデコードして表示
                            val bitmap = remember(file) {
                                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                            }

                            if (bitmap != null) {
                                // 画像本来のアスペクト比（幅 / 高さ）を計算
                                val imageAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

                                Image(
                                    bitmap = bitmap,
                                    contentDescription = file.name,
                                    contentScale = ContentScale.Fit, // 画像全体が収まるように表示（トリミングなし）
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(imageAspectRatio) // 画像本来の縦横比を維持
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "画像読み込みエラー")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/******************************************************************************
 * ブロック選択画面表示
 ******************************************************************************/
@Composable
fun BlockSelectionScreen(
    filesDir: File,
    puzzleID: String,
    onBackToSelectPuzzle: () -> Unit,
    onBlockSelected: (String) -> Unit
) {
    var solvingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var puzzle by remember { mutableStateOf<Puzzle?>(null) }
    // ★ 1. 拡大率（scale）と移動量（offset）の状態を追加
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding() // ★1. ステータスバー（時計・バッテリー）との重なりを防ぐ
            .padding(16.dp),    // ★3. 画面の上下左右のマージン
        contentAlignment = Alignment.Center // ★2. 盤面を上下左右中央に配置
    ) {
        LaunchedEffect(filesDir) {
            withContext(Dispatchers.IO) {
                val solvingBinaryFile = File(filesDir, "puzzles/$puzzleID/solving.bin")
                puzzle = makePuzzleCondition(filesDir = filesDir, puzzleID = puzzleID)
                if (solvingBinaryFile.exists()) {
                    solvingBytes = solvingBinaryFile.readBytes()
                }
            }
        }
        val currentPuzzle = puzzle      // スマートキャストすることで!!付けなくても大丈夫

        if (currentPuzzle == null || solvingBytes == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator() // クルクル回るローディング表示
            }
        } else if (currentPuzzle.blockSizeX.isEmpty() || currentPuzzle.blockSizeY.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(text = "パズルデータの読み込みに失敗しました。")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBackToSelectPuzzle) {
                    Text(text = "パズル選択に戻る")
                }
            }
        } else {
            // パズル盤面のレイアウト位置計算：盤面ドット数から正方形で表示するための単位を求める
            val numBX = currentPuzzle.blockSizeX.size
            val numBY = currentPuzzle.blockSizeY.size
            val numX = currentPuzzle.blockSizeX.sum()
            val numY = currentPuzzle.blockSizeY.sum()
            val dotWidth = constraints.maxWidth / numX
            val dotHeight = constraints.maxHeight / numY
            val dotSize = minOf(dotWidth, dotHeight)
            // 盤面全体の幅と高さ（dp 単位に変換）
            val density = LocalDensity.current
            val boardWidthDp = with(density) { (dotSize * numX).toDp() }
            val boardHeightDp = with(density) { (dotSize * numY).toDp() }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // リセットボタン（倍率が上がった時に元に戻せるようにすると便利です）
                Row {
                    AppButton(text = "パズル選択に戻る", onClick = onBackToSelectPuzzle)
                    if (scale != 1f || offset != Offset.Zero) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AppButton(
                            text = "拡大リセット",
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ★ 2. ピンチ操作・パン操作を受け取る Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // ピンチ操作で倍率変更（1倍〜5倍の範囲に制限）
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                // ドラッグ移動
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(boardWidthDp, boardHeightDp)
                            // ★ 3. 拡大・移動を適用
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        // Composeの仕様上、graphicsLayerの拡大・移動が適用されていても
                                        // tapOffset は自動的にローカル座標に逆変換されるため、
                                        // タップ判定の計算式は変更不要です！
                                        val dotCol = (tapOffset.x / dotSize).toInt()
                                        val dotRow = (tapOffset.y / dotSize).toInt()

                                        var sX = 0
                                        var eX: Int
                                        var bX = 0
                                        for (x in 0 until numBX) {
                                            eX = sX + currentPuzzle.blockSizeX[x]
                                            if (dotCol in sX until eX) {
                                                bX = x
                                                break
                                            }
                                            sX = eX
                                        }

                                        var sY = 0
                                        var eY: Int
                                        var bY = 0
                                        for (y in 0 until numBY) {
                                            eY = sY + currentPuzzle.blockSizeY[y]
                                            if (dotRow in sY until eY) {
                                                bY = y
                                                break
                                            }
                                            sY = eY
                                        }

                                        val blockID = "Y%03dX%03d".format(bY, bX)
                                        onBlockSelected(blockID)
                                    }
                                )
                            }
                    ) {
                        var sY = 0
                        val flagsSize = numBY * numBX
                        for (y in 0 until numBY) {
                            val blockRows = currentPuzzle.blockSizeY[y]
                            var sX = 0
                            for (x in 0 until numBX) {
                                val blockCols = currentPuzzle.blockSizeX[x]
                                val startX = (sX * dotSize).toFloat()
                                val startY = (sY * dotSize).toFloat()
                                val topLeft = Offset(startX, startY)
                                val blockW = (currentPuzzle.blockSizeX[x] * dotSize).toFloat()
                                val blockH = (currentPuzzle.blockSizeY[y] * dotSize).toFloat()
                                val rectSize = Size(blockW, blockH)

                                val blockIndex = y * numBX + x
                                val isSolved = solvingBytes != null &&
                                               solvingBytes!!.size > blockIndex &&
                                               solvingBytes!![blockIndex] != 0.toByte()
                                if (isSolved && solvingBytes != null) {
                                    var pos = flagsSize
                                    for (bY in 0 until y) {
                                        val bySize = currentPuzzle.blockSizeY[bY]
                                        for (bX in 0 until numBX) {
                                            pos += bySize * currentPuzzle.blockSizeX[bX]
                                        }
                                    }
                                    for (bX in 0 until x) {
                                        pos += blockRows * currentPuzzle.blockSizeX[bX]
                                    }
                                    // ブロック内の全マスの描画
                                    for (r in 0 until blockRows) {
                                        for (c in 0 until blockCols) {
                                            val dotVal = solvingBytes!![pos + r * blockCols + c].toInt()
                                            val dotColor = if (dotVal == -1) Color.Black else Color.White
                                            drawRect(
                                                color = dotColor,
                                                topLeft = Offset((sX + c) * dotSize.toFloat(), (sY + r) * dotSize.toFloat()),
                                                size = Size(dotSize.toFloat(), dotSize.toFloat())
                                            )
                                        }
                                    }
                                } else {
                                    // 塗りつぶし（LightGray）
                                    drawRect(
                                        color = Color.LightGray,
                                        topLeft = topLeft,
                                        size = rectSize
                                    )
                                    // 枠線（Gray）
                                    drawRect(
                                        color = Color.Gray,
                                        topLeft = topLeft,
                                        size = rectSize,
                                        style = Stroke(width = 2f)
                                    )
                                }
                                sX += currentPuzzle.blockSizeX[x]
                            }
                            sY += currentPuzzle.blockSizeY[y]
                        }
                    }
                }
            }
        }
    }
}

/******************************************************************************
 * ブロックパズルを解く画面表示
 ******************************************************************************/
@Composable
fun PuzzleBlockPlayScreen(
    filesDir: File,
    puzzleID: String,
    blockID: String,
    onBackToSelectBlock: (PuzzleState?) -> Unit
){
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // テキスト計測用の TextMeasurer を準備
    val textMeasurer = rememberTextMeasurer()

    // ドラッグ範囲選択用
    var startCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var currentCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var isLongPressDrag by remember { mutableStateOf(false) }
    // パズル終了時のパーティクル用アニメーション状態
    val clearAnimProgress = remember { Animatable(0f) }
    var particles by remember { mutableStateOf<List<CellParticle>>(emptyList()) }

    val answerJsonFile = File(filesDir, "puzzles/$puzzleID/$blockID/answer.json")
    val answerBinaryFile = File(filesDir, "puzzles/$puzzleID/$blockID/answer.bin")
    var puzzleState by remember { mutableStateOf<PuzzleState?>(null) }
    LaunchedEffect(filesDir) {
        withContext(Dispatchers.IO) {
            try {
                if (!answerJsonFile.exists() || !answerBinaryFile.exists()) {
                    onBackToSelectBlock(null)
                }
                else {
                    val jsonText = answerJsonFile.readText()
                    val answerData = json.decodeFromString<Answer>(jsonText)
                    val bytes = answerBinaryFile.readBytes()
                    val isPuzzleSolved = (bytes[0] != 0.toByte())
                    if (bytes.size < 1 + answerData.row * answerData.col) {
                        onBackToSelectBlock(null)
                    }
                    else {
                        puzzleState = PuzzleState(
                            isPuzzleSolved = isPuzzleSolved,
                            puzzleData = answerData.puzzleData,
                            answerData = Array(answerData.row) { r ->
                                IntArray(answerData.col) { c ->
                                    bytes[1 + r * answerData.col + c].toInt()
                                }
                            }
                        )
                        refreshTrigger++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onBackToSelectBlock(null)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding() // ★1. ステータスバー（時計・バッテリー）との重なりを防ぐ
            .padding(16.dp),    // ★3. 画面の上下左右のマージン
        contentAlignment = Alignment.Center // ★2. 盤面を上下左右中央に配置
    ) {
        if (puzzleState == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator() // クルクル回るローディング表示
            }
        }
        else {
            val state = puzzleState ?: return@BoxWithConstraints
            val cellWidth = constraints.maxWidth / state.col
            val cellHeight = constraints.maxHeight / state.row
            val cellSize = minOf(cellWidth, cellHeight)
            val density = LocalDensity.current
            val boardWidthDp = with(density) { (cellSize * state.col).toDp() }
            val boardHeightDp = with(density) { (cellSize * state.row).toDp() }

            // パズルクリア時のパーティクル発動処理（refreshTrigger の変更時も再評価する）
            LaunchedEffect(state.isPuzzleSolved, refreshTrigger) {
                if (state.isPuzzleSolved) {
                    particles = createGridParticles(
                        rows = state.row,
                        cols = state.col,
                        cellSize = cellSize
                    )
                    clearAnimProgress.snapTo(0f)
                    clearAnimProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 2500, easing = LinearEasing)
                    )
                } else {
                    clearAnimProgress.snapTo(0f)
                }
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppButton(
                    text = "ブロック選択画面に戻る",
                    onClick = { onBackToSelectBlock(state) }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(boardWidthDp, boardHeightDp)
                            // 1. タップジェスチャー検知
                            .pointerInput(state) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        if (state.isPuzzleSolved) return@detectTapGestures
                                        val c = (offset.x / cellSize).toInt().coerceIn(0, state.col - 1)
                                        val r = (offset.y / cellSize).toInt().coerceIn(0, state.row - 1)
                                        if (state.changeBlack(r, c)) {  // シングルタップが有効に処理されたか？
                                            refreshTrigger++
                                            coroutineScope.launch(Dispatchers.Default) {
                                                // セルサイズ１の自動解決実施
                                                state.doAutoSolve(
                                                    r, c, PuzzleState.BLACK,
                                                    onUpdateStep = {
                                                        withContext(Dispatchers.Main) { refreshTrigger++ }
                                                    }
                                                )
                                                // 解答チェック実施(不整合問題数字の赤字表示）
                                                state.validateSolution(
                                                    onUpdateStep = {
                                                        withContext(Dispatchers.Main) { refreshTrigger++ }
                                                    }
                                                )
                                                saveAnswerBinary(answerBinaryFile, state)
                                            }
                                        }
                                    },
                                    onDoubleTap = { offset ->
                                        if (state.isPuzzleSolved) return@detectTapGestures
                                        val c = (offset.x / cellSize).toInt().coerceIn(0, state.col - 1)
                                        val r = (offset.y / cellSize).toInt().coerceIn(0, state.row - 1)
                                        if (state.changeWhite(r, c)) {
                                            refreshTrigger++
                                            coroutineScope.launch(Dispatchers.Default) {
                                                // セルサイズ１の自動解決実施
                                                state.doAutoSolve(
                                                    r, c, PuzzleState.WHITE,
                                                    onUpdateStep = {
                                                        withContext(Dispatchers.Main) { refreshTrigger++ }
                                                    }
                                                )
                                                // 解答チェック実施(不整合問題数字の赤字表示）
                                                state.validateSolution(
                                                    onUpdateStep = {
                                                        withContext(Dispatchers.Main) { refreshTrigger++ }
                                                    }
                                                )
                                                saveAnswerBinary(answerBinaryFile, state)
                                            }
                                        }
                                    },
                                    onLongPress = { offset ->
                                        if (state.isPuzzleSolved) return@detectTapGestures
                                        val c = (offset.x / cellSize).toInt().coerceIn(0, state.col - 1)
                                        val r = (offset.y / cellSize).toInt().coerceIn(0, state.row - 1)
                                        if (state.cancelColor(r, c)) {
                                            refreshTrigger++
                                            coroutineScope.launch(Dispatchers.Default) {
                                                // 解答チェック実施(不整合問題数字の赤字表示）
                                                state.validateSolution(
                                                    onUpdateStep = {
                                                        withContext(Dispatchers.Main) { refreshTrigger++ }
                                                    }
                                                )
                                                saveAnswerBinary(answerBinaryFile, state)
                                            }
                                        }
                                    }
                                )
                            }
                            // 2. ドラッグジェスチャー検知
                            .pointerInput(state) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        if (state.isPuzzleSolved) return@detectDragGestures
                                        val c = (offset.x / cellSize).toInt().coerceIn(0, state.col - 1)
                                        val r = (offset.y / cellSize).toInt().coerceIn(0, state.row - 1)
                                        startCell = Pair(r, c)
                                        currentCell = Pair(r, c)
                                    },
                                    onDrag = { change, _ ->
                                        if (state.isPuzzleSolved) return@detectDragGestures
                                        change.consume()
                                        val c = (change.position.x / cellSize).toInt().coerceIn(0, state.col - 1)
                                        val r = (change.position.y / cellSize).toInt().coerceIn(0, state.row - 1)
                                        currentCell = Pair(r, c)
                                    },
                                    onDragEnd = {
                                        if (state.isPuzzleSolved) return@detectDragGestures
                                        // ドラッグ終了時に選択範囲（startCell ～ currentCell）を一括で黒にする
                                        if (startCell != null && currentCell != null) {
                                            val minR = minOf(startCell!!.first, currentCell!!.first)
                                            val maxR = maxOf(startCell!!.first, currentCell!!.first)
                                            val minC = minOf(startCell!!.second, currentCell!!.second)
                                            val maxC = maxOf(startCell!!.second, currentCell!!.second)

                                            coroutineScope.launch(Dispatchers.Default) {
                                                state.doReverseColor(minR, maxR, minC, maxC)
                                                // 解答チェック実施(不整合問題数字の赤字表示）
                                                state.validateSolution(
                                                    onUpdateStep = {
                                                        withContext(Dispatchers.Main) { refreshTrigger++ }
                                                    }
                                                )
                                                saveAnswerBinary(answerBinaryFile, state)
                                                refreshTrigger++
                                            }
                                        }
                                        isLongPressDrag = false
                                        startCell = null
                                        currentCell = null
                                    }
                                )
                            }
                            // 3. 長押しドラッグジェスチャー検知
                            .pointerInput(state) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        if (state.isPuzzleSolved) return@detectDragGesturesAfterLongPress
                                        val c = (offset.x / cellSize).toInt().coerceIn(0, state.col - 1)
                                        val r = (offset.y / cellSize).toInt().coerceIn(0, state.row - 1)
                                        startCell = Pair(r, c)
                                        currentCell = Pair(r, c)
                                    },
                                    onDrag = { change, _ ->
                                        if (state.isPuzzleSolved) return@detectDragGesturesAfterLongPress
                                        change.consume()
                                        val c = (change.position.x / cellSize).toInt().coerceIn(0, state.col - 1)
                                        val r = (change.position.y / cellSize).toInt().coerceIn(0, state.row - 1)
                                        currentCell = Pair(r, c)
                                    },
                                    onDragEnd = {
                                        if (state.isPuzzleSolved) return@detectDragGesturesAfterLongPress
                                        // ドラッグ終了時に選択範囲（startCell ～ currentCell）を一括で黒にする
                                        if (startCell != null && currentCell != null) {
                                            val minR = minOf(startCell!!.first, currentCell!!.first)
                                            val maxR = maxOf(startCell!!.first, currentCell!!.first)
                                            val minC = minOf(startCell!!.second, currentCell!!.second)
                                            val maxC = maxOf(startCell!!.second, currentCell!!.second)

                                            coroutineScope.launch(Dispatchers.Default) {
                                                state.doCancelColor(minR, maxR, minC, maxC)
                                                // 解答チェック実施(不整合問題数字の赤字表示）
                                                state.validateSolution(
                                                    onUpdateStep = {
                                                        withContext(Dispatchers.Main) { refreshTrigger++ }
                                                    }
                                                )
                                                saveAnswerBinary(answerBinaryFile, state)
                                                refreshTrigger++
                                            }
                                        }
                                        isLongPressDrag = false
                                        startCell = null
                                        currentCell = null
                                    }
                                )
                            }
                    ) {
                        refreshTrigger                  // <- refreshTrigger が参照されていることにして変化したときに再描画できるように
                        // 描画処理 (図形の描画)
                        // パズルのマス目・罫線・数値の描画
                        for (r in 0 until state.row) {
                            for (c in 0 until state.col) {
                                val answerVal = state.answerData[r][c]
                                val isBlack = answerVal == -1
                                val isWhite = answerVal == 1

                                val cellTopLeft = Offset((c * cellSize).toFloat(), (r * cellSize).toFloat())
                                val cellRectSize = Size(cellSize.toFloat(), cellSize.toFloat())

                                // 1. マスの背景色の描画
                                val bgColor = when {
                                    isBlack -> Color.Black
                                    isWhite -> Color.White
                                    else -> Color.LightGray
                                }
                                drawRect(
                                    color = bgColor,
                                    topLeft = cellTopLeft,
                                    size = cellRectSize
                                )

                                // 2. マスの外枠（罫線）の描画
                                drawRect(
                                    color = Color.Gray,
                                    topLeft = cellTopLeft,
                                    size = cellRectSize,
                                    style = Stroke(width = 2f)
                                )

                                // 3. マスの真ん中に puzzleData (数値) を表示
                                if (state.puzzleData[r][c] != 0) {
                                    val cellText = state.puzzleData[r][c].toString()
                                    val textLayoutResult = textMeasurer.measure(
                                        text = cellText,
                                        style = TextStyle(
                                            color = if (state.isError[r][c]) Color.Red else if (isBlack) Color.White else Color.Black,
                                            fontSize = (cellSize * 0.4f).sp, // マス目の大きさに合わせた文字サイズ
                                            textAlign = TextAlign.Center
                                        )
                                    )

                                    // テキストをマスの真ん中に配置するための X, Y オフセット計算
                                    val textX =
                                        c * cellSize + (cellSize - textLayoutResult.size.width) / 2f
                                    val textY =
                                        r * cellSize + (cellSize - textLayoutResult.size.height) / 2f

                                    drawText(
                                        textLayoutResult = textLayoutResult,
                                        topLeft = Offset(textX, textY)
                                    )
                                }
                            }
                        }
                        // --------------------------------------------------
                        // ★ 3. マスの上にパーティクルを重ねて描画
                        // --------------------------------------------------
                        if (clearAnimProgress.value > 0f && clearAnimProgress.value < 1f) {
                            val totalElapsedMs = clearAnimProgress.value * 2500f // 経過時間(ms)

                            particles.forEach { p ->
                                val particleAgeSec =
                                    (totalElapsedMs - p.delayMs) / 1000f // この粒子の経過時間(秒)

                                // 遅延時間を過ぎており、1秒以内の粒子のみ描画
                                if (particleAgeSec > 0f && particleAgeSec < 1.0f) {
                                    // 時間経過に伴う移動・ふわっと下がる重力・透明度・サイズの減衰
                                    val currentX = p.startX + p.vx * particleAgeSec
                                    val currentY =
                                        p.startY + p.vy * particleAgeSec + 80f * particleAgeSec * particleAgeSec
                                    val alpha = (1.0f - particleAgeSec).coerceIn(0f, 1f)
                                    val currentRadius = p.size * (1.0f - particleAgeSec * 0.5f)

                                    drawCircle(
                                        color = p.color.copy(alpha = alpha),
                                        center = Offset(currentX, currentY),
                                        radius = currentRadius
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/******************************************************************************
 * 標準のアプリケーションのボタンを定義する
 ******************************************************************************/
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 18.sp, color = Color.White)
        )
    }
}

/******************************************************************************
 * 初めてpuzzleIDのパズルを選んだときに、フォルダ構成、ファイルを整える
 *   fileDir/${puzzleID}
 *                +- puzzle.json           <- このファイルのデータを基に
 *                +- Y000X000              <- これ以下のフォルダ、ファイルを作成
 *                     +- answer.json
 *                     +- answer.bin
 *                +- Y000X001
 *                     +- answer.json
 *                     +- answer.bin
 *
 * @param filesDir アプリケーションのファイル保存先
 * @param puzzleID パズルID
 ******************************************************************************/
private fun makePuzzleCondition(filesDir: File, puzzleID: String): Puzzle {
    val puzzleDir = File(filesDir, "puzzles/$puzzleID")
    val solvingBinaryFile = File(puzzleDir, "solving.bin")
    val puzzleJsonFile = File(puzzleDir, "puzzle.json")
    var puzzle = Puzzle()
    try {
        if (puzzleJsonFile.exists()) {
            val puzzleText = puzzleJsonFile.readText()
            puzzle = json.decodeFromString<Puzzle>(puzzleText)
            if (!solvingBinaryFile.exists()) {
                val isSolvedSize = puzzle.blockSizeY.size * puzzle.blockSizeX.size
                val answerSize = puzzle.blockSizeY.sum() * puzzle.blockSizeX.sum()
                val solvingBytes = ByteArray(isSolvedSize + answerSize)
                solvingBinaryFile.writeBytes(solvingBytes)
                var pY = 0
                for (y in puzzle.blockSizeY.indices) {
                    var pX = 0
                    for (x in puzzle.blockSizeX.indices) {
                        val folderName = "Y%03dX%03d".format(y, x)
                        val cellDir = File(puzzleDir, folderName)
                        if (!cellDir.exists()) {
                            cellDir.mkdirs()
                        }
                        // Y???X???フォルダのしたにanswer.json を作成
                        val answerJsonFile = File(cellDir, "answer.json")
                        val endY = (pY + puzzle.blockSizeY[y]).coerceAtMost(puzzle.puzzle.size)
                        val blockPuzzleData = puzzle.puzzle
                            .subList(pY, endY)
                            .map { row ->
                                val endX = (pX + puzzle.blockSizeX[x]).coerceAtMost(row.size)
                                row.subList(pX, endX)
                            }
                        val answer = Answer(
                            puzzleData = blockPuzzleData,
                            row = puzzle.blockSizeY[y],
                            col = puzzle.blockSizeX[x]
                        )
                        answerJsonFile.writeText(json.encodeToString(answer))
                        // Y???X???フォルダのしたにanswer.bin を作成
                        val answerBinaryFile = File(cellDir, "answer.bin")
                        val isSolvedByte = 0.toByte()
                        val answerData = Array(puzzle.blockSizeY[y]) { IntArray(puzzle.blockSizeX[x]) }
                        answerData[0][0] = puzzle.leftTop[y][x]
                        val answerBytes = answerData.flatMap { row -> row.map { it.toByte()}}
                        val allByte = byteArrayOf(isSolvedByte, *answerBytes.toByteArray())
                        answerBinaryFile.writeBytes(allByte)

                        pX += puzzle.blockSizeX[x]
                    }
                    pY += puzzle.blockSizeY[y]
                }
            }
        } else {
            Log.e("makePuzzleCondition", "puzzle.json が見つかりません: ${puzzleJsonFile.absolutePath}")        }
    } catch (e: Exception) {
        Log.e("makePuzzleCondition", "makePuzzleCondition で例外が発生しました", e)
    }
    return puzzle
}

/******************************************************************************
 * ブロックが解かれていた場合、そのブロックの解答を全体の解答エリアにコピーする
 * ブロック解答画面からブロック選択画面に戻るときに呼ばれる
 *   fileDir/${puzzleID}/${blockID}/answer.bin  ->  fileDir/${puzzleID}/solving.bin
 *   solving.binからパズルを解いた時の絵を描画するようになる
 *
 * @param filesDir アプリケーションのファイル保存先
 * @param puzzleID パズルID
 * @param blockID ブロックID
 ******************************************************************************/
private fun modifySolvingBinary(filesDir: File, puzzleID: String, blockID: String, puzzleState: PuzzleState?) {
    if (puzzleState != null && puzzleState.isPuzzleSolved) {
        val puzzleJsonFile = File(filesDir, "puzzles/${puzzleID}/puzzle.json")
        val solvingBinaryFile = File(filesDir, "puzzles/${puzzleID}/solving.bin")
        if (puzzleJsonFile.exists() && solvingBinaryFile.exists()) {
            val parts = blockID.removePrefix("Y").split("X")
            val blockY = parts[0].toInt()
            val blockX = parts[1].toInt()
            val solvingBytes = solvingBinaryFile.readBytes()
            val puzzleHeaderInfo = readPuzzleHeaderOnly(puzzleJsonFile)
            if (solvingBytes.size > puzzleHeaderInfo.blockSizeY.size * puzzleHeaderInfo.blockSizeX.size) {
                if (solvingBytes[puzzleHeaderInfo.blockSizeX.size * blockY + blockX] == 0.toByte()) {
                    solvingBytes[puzzleHeaderInfo.blockSizeX.size * blockY + blockX] = 1.toByte()
                    var pos = puzzleHeaderInfo.blockSizeY.size * puzzleHeaderInfo.blockSizeX.size
                    for (y in 0 until blockY) {
                        val sizeY = puzzleHeaderInfo.blockSizeY[y]
                        for (x in puzzleHeaderInfo.blockSizeX.indices) {
                            pos += sizeY * puzzleHeaderInfo.blockSizeX[x]
                        }
                    }
                    val sizeY = puzzleHeaderInfo.blockSizeY[blockY]
                    for (x in 0 until blockX) {
                        pos += sizeY * puzzleHeaderInfo.blockSizeX[x]
                    }
                    val row = puzzleState.answerData.size
                    val col = if (row > 0) puzzleState.answerData[0].size else 0
                    for (r in 0 until row) {
                        for (c in 0 until col) {
                            solvingBytes[pos++] = puzzleState.answerData[r][c].toByte()
                        }
                    }
                    solvingBinaryFile.writeBytes(solvingBytes)
                }
            }
        }
    }
}

/******************************************************************************
 * パズルの解答状態をanswer.binに記録する
 *   パズルの解答状態が変化したときに呼ばれる
 *
 * @param answerBinaryFile ブロック内のanswer.binファイル
 * @param puzzleState パズル状態
 ******************************************************************************/
private suspend fun saveAnswerBinary(answerBinaryFile: File, puzzleState: PuzzleState) {
    withContext(Dispatchers.IO) {   // <- スレッドを切り替えているのでsuspendが必要。元スレッドはフリーズせず解放される
        val isSolvedByte = if (puzzleState.isPuzzleSolved) 1.toByte() else 0.toByte()
        val answerBytes = puzzleState.answerData.flatMap { row -> row.map { it.toByte()}}
        val allByte = byteArrayOf(isSolvedByte, *answerBytes.toByteArray())
        answerBinaryFile.writeBytes(allByte)
    }
}

/******************************************************************************
 * puzzle.jsonファイルは大きすぎるので、必要な"puzzleID"、"blockSizeX"、"blockSizeY"だけ読み込む
 *
 * @param jsonFile puzzle.jsonファイルへのpath
 * @return パズルのヘッダー情報
 ******************************************************************************/
private fun readPuzzleHeaderOnly(jsonFile: File): PuzzleHeaderInfo {
    val headerInfo = PuzzleHeaderInfo()
    jsonFile.inputStream().bufferedReader().use { bufferedReader ->
        val reader = JsonReader(bufferedReader)
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "puzzleID" -> headerInfo.puzzleID = reader.nextString()
                "blockSizeX" -> headerInfo.blockSizeX = reader.nextIntList()
                "blockSizeY" -> headerInfo.blockSizeY = reader.nextIntList()
                else -> reader.skipValue()
            }
            if (headerInfo.puzzleID.isNotEmpty() &&
                headerInfo.blockSizeX.isNotEmpty() &&
                headerInfo.blockSizeY.isNotEmpty()) {
                break
            }
        }
    }
    return headerInfo
}

/******************************************************************************
 * JsonReaderへユーザーメソッドを追加した
 * JsonファイルからList<Int>を読み込む
 *
 * @return List<Int> Jsonファイルから読み取ったList
 ******************************************************************************/
private fun JsonReader.nextIntList(): List<Int> {
    val list = mutableListOf<Int>()
    beginArray()
    while (hasNext()) {
        list.add(nextInt())
    }
    endArray()
    return list
}

