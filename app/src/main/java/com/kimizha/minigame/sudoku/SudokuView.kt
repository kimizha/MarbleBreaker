package com.kimizha.minigame.sudoku

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.view.*
import com.kimizha.minigame.Audio
import com.kimizha.minigame.LocalStore
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.min

class SudokuView(context: Context, private val exit: () -> Unit) : View(context) {
    private data class Button(val rect: RectF, val action: () -> Unit)
    private val store = SudokuStore(context)
    private val audio = Audio(context, LocalStore(context))
    private var game = store.load()
    private val worker = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null
    private var generation = 0
    private var generating = false
    private var disposed = false
    private var menu = true
    private var paused = false
    private var foreground = true
    private var pending: Difficulty? = null
    private var checking = false
    private var message = "每行、每列、每个九宫格填入 1～9，不重复"
    private var saveError = false
    private var lastTick = SystemClock.elapsedRealtime()
    private var savedAt = 0L
    private var scale = 1f
    private var ox = 0f
    private var oy = 0f
    private var leftInset = 0
    private var topInset = 0
    private var rightInset = 0
    private var bottomInset = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttons = mutableListOf<Button>()
    private var pressed: Button? = null
    private var down = PointF()
    private val bg = 0xFF09162D.toInt()
    private val panel = 0xFF142743.toInt()
    private val cyan = 0xFF53E3E8.toInt()
    private val muted = 0xFF95ACC9.toInt()
    private val red = 0xFFFF718D.toInt()
    init {
        isFocusable = true
        setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                leftInset = safe.left; topInset = safe.top; rightInset = safe.right; bottomInset = safe.bottom
            } else {
                @Suppress("DEPRECATION")
                run {
                    leftInset = insets.systemWindowInsetLeft; topInset = insets.systemWindowInsetTop
                    rightInset = insets.systemWindowInsetRight; bottomInset = insets.systemWindowInsetBottom
                }
                if (Build.VERSION.SDK_INT >= 28) insets.displayCutout?.let {
                    leftInset = maxOf(leftInset, it.safeInsetLeft); topInset = maxOf(topInset, it.safeInsetTop)
                    rightInset = maxOf(rightInset, it.safeInsetRight); bottomInset = maxOf(bottomInset, it.safeInsetBottom)
                }
            }
            invalidate(); insets
        }
    }
    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        if (foreground && !menu && !paused && game?.complete == false) {
            game?.let { it.elapsedMillis += (now - lastTick).coerceAtLeast(0) }
        }
        lastTick = now
    }
    private fun save() { game?.let { saveError = !store.save(it); savedAt = it.elapsedMillis } }
    fun suspendGame() {
        tick(); foreground = false; if (!menu) paused = true
        pressed = null; save(); audio.active(false)
    }
    fun resumeForeground() { foreground = true; lastTick = SystemClock.elapsedRealtime(); audio.active(true); invalidate() }
    fun dispose() { tick(); save(); disposed = true; generation++; task?.cancel(true); worker.shutdownNow(); audio.release() }
    fun back() {
        tick()
        when {
            generating -> { generation++; task?.cancel(true); generating = false; message = "已取消生成，可以继续上局" }
            pending != null -> pending = null
            !menu -> { menu = true; paused = false; save() }
            else -> exit()
        }
        invalidate()
    }
    private fun request(difficulty: Difficulty) {
        if (game?.complete == false) { pending = difficulty; invalidate() } else generate(difficulty)
    }
    private fun generate(difficulty: Difficulty) {
        pending = null; generating = true; message = "正在生成唯一解题目…"; val token = ++generation
        task?.cancel(true)
        task = worker.submit {
            val result = runCatching { SudokuGenerator.generate(difficulty, System.currentTimeMillis()) }
            post {
                if (!disposed && token == generation) {
                    generating = false
                    result.onSuccess {
                        game = SudokuGame(it); menu = false; paused = false; checking = false
                        message = "选中空格，再点击数字；可用笔记记录候选数"
                        lastTick = SystemClock.elapsedRealtime(); save()
                    }.onFailure { message = "生成未完成，请重新选择难度" }
                    invalidate()
                }
            }
        }
        invalidate()
    }
    private fun edit(action: (SudokuGame) -> Boolean) {
        val g = game ?: return; tick()
        if (action(g)) {
            checking = false; message = if (g.complete) "每一格都正确，恭喜完成！" else "剩余 ${g.emptyCount} 个空格"
            audio.play(if (g.complete) "win" else "hit"); save()
        } else message = if (g.selected in 0..80 && g.puzzle.givens[g.selected] > 0) "题目给出的数字不能修改" else "当前没有可执行的操作"
        invalidate()
    }
    private fun hint() {
        val g = game ?: return; tick(); val cell = g.hint()
        if (cell >= 0) {
            checking = false; message = "提示：第 ${cell / 9 + 1} 行，第 ${cell % 9 + 1} 列填 ${g.values[cell]}"
            audio.play(if (g.complete) "win" else "power"); save()
        }
        invalidate()
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c); tick()
        val g = game
        if (g != null && !menu && g.elapsedMillis - savedAt >= 15_000) save()
        scale = min((width - leftInset - rightInset) / 720f, (height - topInset - bottomInset) / 1200f).coerceAtLeast(.01f)
        ox = leftInset + (width - leftInset - rightInset - 720 * scale) / 2
        oy = topInset + (height - topInset - bottomInset - 1200 * scale) / 2
        c.drawColor(bg); c.save(); c.translate(ox, oy); c.scale(scale, scale); c.clipRect(0f, 0f, 720f, 1200f)
        buttons.clear()
        if (menu) menu(c) else if (g != null) board(c, g)
        if (pending != null) confirmation(c)
        if (saveError) text(c, "存档写入失败，请检查存储空间", 360f, 1190f, 22f, red)
        c.restore()
        val description = if (menu) "数独 · 选择难度；${if (generating) "正在生成" else if (g != null) "可继续上局" else "还没有存档"}"
            else g?.let {
                val cell = it.selected
                val selection = if (cell >= 0) "选中 ${cell / 9 + 1} 行 ${cell % 9 + 1} 列；数字 ${it.values[cell]}；候选 ${it.notes[cell]}" else "尚未选格"
                "数独 · 棋盘；${it.puzzle.difficulty.label}；剩余 ${it.emptyCount} 格；$selection；笔记${if (it.noteMode) "开" else "关"}；提示 ${it.hintsUsed}；${if (paused) "暂停" else if (it.complete) "完成" else "进行中"}"
            } ?: "数独"
        if (contentDescription != description) contentDescription = description
        if (foreground && (generating || (!menu && !paused && g?.complete == false))) postInvalidateDelayed(250)
    }
    private fun rect(c: Canvas, r: RectF, color: Int, radius: Float = 14f) {
        paint.color = color; paint.style = Paint.Style.FILL; c.drawRoundRect(r, radius, radius, paint)
    }
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float = 30f, color: Int = Color.WHITE, align: Paint.Align = Paint.Align.CENTER) {
        paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = size; paint.textAlign = align
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL); c.drawText(value, x, y, paint)
    }
    private fun button(c: Canvas, label: String, x: Float, y: Float, w: Float = 520f, h: Float = 96f,
                       active: Boolean = false, enabled: Boolean = true, action: () -> Unit) {
        val bounds = RectF(x, y, x + w, y + h)
        rect(c, bounds, if (!enabled) 0xFF102039.toInt() else if (active) cyan else panel)
        text(c, label, x + w / 2, y + h / 2 + 11, if (w < 150) 26f else 33f,
            if (!enabled) muted else if (active) bg else Color.WHITE)
        if (enabled) buttons.add(Button(bounds, action))
    }
    private fun menu(c: Canvas) {
        text(c, "SUDOKU", 360f, 135f, 27f, cyan)
        text(c, "数独", 360f, 230f, 76f)
        text(c, "静下心，填满每一个九宫格", 360f, 295f, 29f, muted)
        val demo = arrayOf("1 2 3", "4 5 6", "7 8 9")
        for ((i, row) in demo.withIndex()) text(c, row, 360f, 350f + i * 36, 29f, if (i == 1) cyan else muted)
        button(c, if (generating) "正在生成唯一解题目…" else "继续上局", 100f, 470f, active = true, enabled = !generating && game != null) {
            menu = false; paused = false; lastTick = SystemClock.elapsedRealtime(); invalidate()
        }
        for ((index, difficulty) in Difficulty.entries.withIndex()) {
            button(c, "${difficulty.label} · 新游戏", 100f, 590f + index * 120, enabled = !generating) { request(difficulty) }
        }
        text(c, "每道题只有一个解 · 独立自动存档", 360f, 1000f, 27f, muted)
        text(c, if (generating) "生成中可返回取消" else "困难档留白更多，支持笔记和提示", 360f, 1045f, 25f, muted)
        button(c, "返回小游戏", 100f, 1090f, h = 88f) { back() }
    }
    private fun board(c: Canvas, g: SudokuGame) {
        text(c, "数独", 45f, 58f, 44f, align = Paint.Align.LEFT)
        text(c, g.puzzle.difficulty.label, 45f, 121f, 29f, muted, Paint.Align.LEFT)
        text(c, time(g.elapsedMillis), 360f, 121f, 32f, cyan)
        button(c, "暂停", 575f, 23f, 100f, 92f) { tick(); paused = true; save(); invalidate() }
        val conflicts = g.conflicts(); val wrong = if (checking) g.wrongCells() else BooleanArray(81)
        val selectedValue = g.values.getOrElse(g.selected) { 0 }
        for (i in 0..80) {
            val x = 45f + (i % 9) * 70; val y = 165f + (i / 9) * 70
            val color = when {
                i == g.selected -> 0xFF365B8C.toInt()
                conflicts[i] || wrong[i] -> 0xFF64354B.toInt()
                selectedValue > 0 && g.values[i] == selectedValue -> 0xFF264967.toInt()
                g.selected >= 0 && SudokuSolver.peers(i, g.selected) -> 0xFF1C3554.toInt()
                else -> panel
            }
            rect(c, RectF(x, y, x + 70, y + 70), color, 0f)
            val value = g.values[i]
            if (value > 0) text(c, value.toString(), x + 35, y + 49, 39f,
                if (conflicts[i] || wrong[i]) red else if (g.puzzle.givens[i] > 0) Color.WHITE else cyan)
            else for (n in 1..9) if (g.notes[i] and (1 shl (n - 1)) != 0) {
                text(c, n.toString(), x + 12 + (n - 1) % 3 * 23, y + 20 + (n - 1) / 3 * 23, 18f, muted)
            }
        }
        for (i in 0..9) {
            paint.color = if (i % 3 == 0) 0xFF9FB9D8.toInt() else 0xFF34506E.toInt()
            paint.strokeWidth = if (i % 3 == 0) 3.5f else 1f
            c.drawLine(45f + i * 70, 165f, 45f + i * 70, 795f, paint)
            c.drawLine(45f, 165f + i * 70, 675f, 165f + i * 70, paint)
        }
        val labels = listOf("笔记", "擦除", "撤销", "重做", "提示", "检查")
        for ((i, label) in labels.withIndex()) button(c, label, 31f + i * 111, 816f, 103f, 96f,
            active = i == 0 && g.noteMode, enabled = when (i) { 2 -> g.undo.isNotEmpty(); 3 -> g.redo.isNotEmpty(); else -> true }) {
            when (i) {
                0 -> { g.noteMode = !g.noteMode; message = if (g.noteMode) "笔记模式：点击数字记录或取消候选数" else "填写模式"; save(); invalidate() }
                1 -> edit { it.erase() }
                2 -> edit { it.undoMove() }
                3 -> edit { it.redoMove() }
                4 -> hint()
                5 -> { checking = true; val count = g.wrongCells().count { it }; message = if (count == 0) "当前填写正确，还剩 ${g.emptyCount} 个空格" else "有 $count 个数字需要调整，已用红色标出"; invalidate() }
            }
        }
        for (n in 1..9) {
            val x = if (n <= 5) 45f + (n - 1) * 128 else 109f + (n - 6) * 128
            button(c, n.toString(), x, if (n <= 5) 928f else 1037f, 118f, 96f) { edit { it.enter(n) } }
        }
        text(c, message, 360f, 1180f, 23f, muted)
        if (paused) pause(c) else if (g.complete) won(c, g)
    }
    private fun shade(c: Canvas) { buttons.clear(); rect(c, RectF(0f, 0f, 720f, 1200f), 0xED09162D.toInt(), 0f) }
    private fun pause(c: Canvas) {
        shade(c); text(c, "数独已暂停", 360f, 340f, 54f); text(c, "计时暂停，进度已保存", 360f, 410f, 29f, muted)
        button(c, "继续游戏", 100f, 500f, active = true) { paused = false; lastTick = SystemClock.elapsedRealtime(); invalidate() }
        button(c, "选择难度", 100f, 630f) { menu = true; paused = false; save(); invalidate() }
        button(c, "返回小游戏", 100f, 760f) { save(); exit() }
    }
    private fun won(c: Canvas, g: SudokuGame) {
        shade(c); text(c, "数独完成", 360f, 315f, 68f, cyan)
        text(c, "${g.puzzle.difficulty.label} · 用时 ${time(g.elapsedMillis)}", 360f, 410f, 34f)
        text(c, "使用提示 ${g.hintsUsed} 次", 360f, 467f, 29f, muted)
        button(c, "再来一局", 100f, 570f, active = true) { menu = true; generate(g.puzzle.difficulty) }
        button(c, "选择难度", 100f, 700f) { menu = true; invalidate() }
        button(c, "返回小游戏", 100f, 830f) { save(); exit() }
    }
    private fun confirmation(c: Canvas) {
        shade(c); text(c, "开始新的数独？", 360f, 340f, 49f)
        text(c, "当前数独尚未完成，新局会替换上局", 360f, 420f, 28f, muted)
        val difficulty = pending ?: return
        button(c, "开始${difficulty.label}新局", 100f, 530f, active = true) { generate(difficulty) }
        button(c, "继续上局", 100f, 660f) { pending = null; menu = false; paused = false; lastTick = SystemClock.elapsedRealtime(); invalidate() }
        button(c, "取消", 100f, 790f) { pending = null; invalidate() }
    }
    private fun time(millis: Long): String {
        val total = millis / 1000; return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!foreground || event.pointerCount > 1) { pressed = null; return true }
        val x = (event.x - ox) / scale; val y = (event.y - oy) / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { down = PointF(x, y); pressed = buttons.lastOrNull { it.rect.contains(x, y) } }
            MotionEvent.ACTION_UP -> {
                val button = pressed; pressed = null
                if (kotlin.math.abs(x - down.x) < 25 && kotlin.math.abs(y - down.y) < 25) {
                    if (button != null && button.rect.contains(x, y)) { performClick(); button.action() }
                    else if (!menu && !paused && game?.complete == false && x >= 45 && x < 675 && y >= 165 && y < 795) {
                        game?.select(((y - 165) / 70).toInt() * 9 + ((x - 45) / 70).toInt())
                        checking = false; message = "选中格子后点击数字；题目给出的数字不能修改"; save(); invalidate()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> pressed = null
        }
        return true
    }
}
