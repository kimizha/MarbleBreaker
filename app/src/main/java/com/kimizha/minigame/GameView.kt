package com.kimizha.minigame

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import com.kimizha.minigame.core.*
import kotlin.math.*
import kotlin.random.Random

class GameView(context: Context) : View(context) {
    private enum class Screen { HOME, LEVELS, PLAY, SETTINGS }
    private data class Button(val bounds: RectF, val action: () -> Unit)
    private data class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float,
                                var life: Float, val color: Int, val radius: Float)
    private data class ToastFx(val text: String, val x: Float, var y: Float, var life: Float = 1.6f)
    private val store = LocalStore(context)
    private val audio = Audio(context, store)
    private val saved = store.load()
    private val progress = saved.progress
    private var engine: Engine? = saved.engine
    private var screen = Screen.HOME
    private var settingsFrom = Screen.HOME
    private var paused = false
    private var foreground = true
    private var dragging = false
    private var activePointer = -1
    private var downX = 0f
    private var dragDistance = 0f
    private var autosaveTime = 0.0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttons = mutableListOf<Button>()
    private var pressed: Button? = null
    private val particles = mutableListOf<Particle>()
    private val toasts = mutableListOf<ToastFx>()
    private var laserLife = 0f
    private val laserBeams = mutableListOf<Pair<Float, Float>>()
    private val hitGlows = mutableListOf<Particle>()
    private var ballPulse = 0f
    private var clock = 0L
    private var animationTime = 0.0
    private var observedPhase: Phase? = engine?.phase
    private var scale = 1f
    private var ox = 0f
    private var oy = 0f
    private var insetLeft = 0
    private var insetTop = 0
    private var insetRight = 0
    private var insetBottom = 0
    private var saveError = false
    private val navy = Color.rgb(9, 22, 45)
    private val panel = Color.rgb(20, 39, 67)
    private val cyan = Color.rgb(83, 227, 232)
    private val muted = Color.rgb(140, 165, 197)
    private val colors = intArrayOf(0xFF53E3E8.toInt(), 0xFF75A8FF.toInt(), 0xFFB693FF.toInt(),
        0xFFFF718D.toInt(), 0xFFFFB76B.toInt(), 0xFFBEEA77.toInt())
    init {
        isFocusable = true
        contentDescription = "小游戏。单指左右拖动控制挡板，点击开始发球，接住下落道具。"
        setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                insetLeft = safe.left; insetTop = safe.top; insetRight = safe.right; insetBottom = safe.bottom
            } else {
                @Suppress("DEPRECATION")
                run {
                    insetLeft = insets.systemWindowInsetLeft; insetTop = insets.systemWindowInsetTop
                    insetRight = insets.systemWindowInsetRight; insetBottom = insets.systemWindowInsetBottom
                }
                if (Build.VERSION.SDK_INT >= 28) insets.displayCutout?.let {
                    insetTop = max(insetTop, it.safeInsetTop); insetBottom = max(insetBottom, it.safeInsetBottom)
                    insetLeft = max(insetLeft, it.safeInsetLeft); insetRight = max(insetRight, it.safeInsetRight)
                }
            }
            invalidate(); insets
        }
    }
    private fun save() { saveError = !store.save(progress, engine) }
    private fun start(level: Int, infinite: Boolean = false, seed: Long = level.toLong()) {
        engine = Engine(level, infinite, seed)
        if (!infinite) progress.last = level
        observedPhase = Phase.READY; screen = Screen.PLAY; paused = false
        particles.clear(); toasts.clear(); dragging = false; clock = 0
        save(); invalidate()
    }
    fun suspendGame() {
        foreground = false; if (screen == Screen.PLAY) paused = true
        dragging = false; pressed = null; save(); audio.active(false)
    }
    fun resumeForeground() { foreground = true; clock = 0; audio.active(true); invalidate() }
    fun dispose() { save(); audio.release() }
    fun back() {
        dragging = false
        when (screen) {
            Screen.PLAY -> { if (!paused && engine?.phase in listOf(Phase.READY, Phase.PLAYING)) paused = true else { screen = Screen.HOME; save() } }
            Screen.SETTINGS -> { screen = settingsFrom; audio.updateMusic() }
            else -> { screen = Screen.HOME; save() }
        }
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = if (clock == 0L) 0.0 else ((now - clock) / 1e9).coerceIn(0.0, .08)
        clock = now; animationTime += dt
        if (foreground && screen == Screen.PLAY && !paused) {
            engine?.advance(dt)
            processEvents()
            autosaveTime += dt
            if (autosaveTime >= 5.0) { save(); autosaveTime = 0.0 }
        }
        if (foreground && !paused) animate(dt.toFloat())
        val description = when (screen) {
            Screen.HOME -> "小游戏 · 首页；已解锁 ${progress.unlocked} 关"
            Screen.LEVELS -> "小游戏 · 关卡选择"
            Screen.SETTINGS -> "小游戏 · 设置"
            Screen.PLAY -> engine?.let { e ->
                val state = if (paused) "暂停" else when (e.phase) {
                    Phase.READY -> "等待开球"; Phase.PLAYING -> "持续反弹"; Phase.WON -> "通关"; Phase.LOST -> "失败"
                }
                "小游戏 · 游戏中；第 ${e.level} 关；开球 ${e.serves}；生命 ${e.lives}；弹珠 ${e.balls.size}；挡板 ${e.paddle.x.toInt()}；$state"
            } ?: "小游戏"
        }
        if (contentDescription != description) contentDescription = description
        canvas.drawColor(navy)
        scale = min((width - insetLeft - insetRight) / 720f, (height - insetTop - insetBottom) / 1200f).coerceAtLeast(.01f)
        ox = insetLeft + (width - insetLeft - insetRight - 720 * scale) / 2
        oy = insetTop + (height - insetTop - insetBottom - 1200 * scale) / 2
        canvas.save(); canvas.translate(ox, oy); canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, 720f, 1200f)
        buttons.clear()
        when (screen) {
            Screen.HOME -> home(canvas)
            Screen.LEVELS -> levels(canvas)
            Screen.SETTINGS -> settings(canvas)
            Screen.PLAY -> game(canvas)
        }
        if (saveError) text(canvas, "存档暂时无法写入，请检查存储空间", 360f, 1190f, 22f, colors[3])
        canvas.restore()
        if (foreground) {
            if (store.lowPerformance) postInvalidateDelayed(33) else postInvalidateOnAnimation()
        }
    }
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float = 30f,
                     color: Int = Color.WHITE, align: Paint.Align = Paint.Align.CENTER) {
        paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = size; paint.textAlign = align
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        c.drawText(value, x, y, paint)
    }
    private fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = 20f) {
        paint.style = Paint.Style.FILL; paint.color = color
        c.drawRoundRect(x, y, x + w, y + h, radius, radius, paint)
    }
    private fun circle(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.style = Paint.Style.FILL; paint.color = color; c.drawCircle(x, y, radius, paint)
    }
    private fun button(c: Canvas, label: String, x: Float, y: Float, w: Float = 520f, h: Float = 98f,
                       primary: Boolean = false, enabled: Boolean = true, action: () -> Unit) {
        rect(c, x, y, w, h, if (!enabled) 0xFF142038.toInt() else if (primary) cyan else panel)
        text(c, label, x + w / 2, y + h / 2 + 11, if (w < 150) 28f else 34f,
            if (!enabled) muted else if (primary) navy else Color.WHITE)
        if (enabled) buttons.add(Button(RectF(x, y, x + w, y + h), action))
    }
    private fun decoration(c: Canvas) {
        for (i in 0..26) {
            val x = ((i * 137 + 39) % 720).toFloat()
            val y = ((i * 197 + 71) % 1200).toFloat()
            circle(c, x, y, if (i % 3 == 0) 2.5f else 1.3f, 0x334E8AB2)
        }
    }
    private fun home(c: Canvas) {
        decoration(c)
        text(c, "MARBLE BREAKER", 360f, 145f, 25f, cyan)
        text(c, "小游戏", 360f, 240f, 76f)
        text(c, "左右滑动，接住每一束星光", 360f, 298f, 29f, muted)
        for (i in 0..4) rect(c, 174f + i * 78, 370f + if (i % 2 == 0) 0 else 25,
            58f, 58f, colors[i], 14f)
        circle(c, 360f, 495f + (sin(animationTime * 2) * 9).toFloat(), 13f, Color.WHITE)
        val active = engine?.phase in listOf(Phase.READY, Phase.PLAYING)
        button(c, if (active) "继续游戏" else "开始游戏", 100f, 570f, primary = true) {
            if (active) { screen = Screen.PLAY; paused = false; clock = 0 } else start(progress.last)
        }
        button(c, "关卡选择 · ${progress.unlocked} / 30", 100f, 690f) { screen = Screen.LEVELS }
        button(c, "无限挑战", 100f, 810f) { start(1, true, System.currentTimeMillis()) }
        button(c, "设置", 100f, 930f) { settingsFrom = Screen.HOME; screen = Screen.SETTINGS }
        text(c, if (saved.migrated) "已保留旧版关卡进度，对局已升级为挡板模式" else "最高分 ${progress.best}    ·    离线畅玩", 360f, 1110f, 24f, muted)
    }
    private fun levels(c: Canvas) {
        text(c, "选择关卡", 360f, 105f, 48f)
        text(c, "消灭所有彩色方块即可过关", 360f, 162f, 27f, muted)
        for (i in 1..30) {
            val col = (i - 1) % 5; val row = (i - 1) / 5
            val x = 40f + col * 132; val y = 220f + row * 125
            button(c, if (progress.canPlay(i)) i.toString() else "锁", x, y, 112f, 102f,
                primary = i == progress.last, enabled = progress.canPlay(i)) { start(i) }
        }
        button(c, "返回首页", 100f, 1040f) { screen = Screen.HOME }
    }
    private fun settings(c: Canvas) {
        text(c, "设置", 360f, 155f, 54f)
        text(c, "所有设置自动保存在本机", 360f, 215f, 27f, muted)
        button(c, "音效    ${if (store.sound) "开" else "关"}", 100f, 320f) { store.sound = !store.sound }
        button(c, "背景音乐    ${if (store.music) "开" else "关"}", 100f, 450f) { store.music = !store.music; audio.updateMusic() }
        button(c, "震动反馈    ${if (store.vibration) "开" else "关"}", 100f, 580f) { store.vibration = !store.vibration }
        button(c, "低性能模式    ${if (store.lowPerformance) "开" else "关"}", 100f, 710f) { store.lowPerformance = !store.lowPerformance }
        text(c, "低性能模式减少粒子，并以约 30 FPS 绘制", 360f, 858f, 25f, muted)
        text(c, "左右拖动挡板 · 接住道具 · 弹珠上限 150", 360f, 910f, 25f, muted)
        button(c, "返回", 100f, 1020f, primary = true) { screen = settingsFrom }
    }
    private fun game(c: Canvas) {
        val e = engine ?: return
        text(c, if (e.infinite) "无限 · 第 ${e.level} 波" else "第 ${e.level} 关 · ${Levels.names[(e.level - 1).coerceIn(0, 29)]}",
            24f, 38f, 27f, align = Paint.Align.LEFT)
        text(c, "分数 ${e.score}", 24f, 88f, 28f, muted, Paint.Align.LEFT)
        text(c, "♥ ${e.lives}", 320f, 88f, 31f, colors[3])
        text(c, "● ${e.balls.size}", 466f, 88f, 30f + ballPulse * 7f, cyan)
        button(c, "暂停", 602f, 8f, 110f, 96f) { paused = true; dragging = false; save() }
        paint.color = 0xFF203853.toInt(); paint.strokeWidth = 2f
        c.drawLine(0f, 139f, 720f, 139f, paint)
        for (b in e.bodies) if (b.alive) {
            c.save(); c.translate(b.center.x.toFloat(), b.center.y.toFloat()); c.rotate(Math.toDegrees(b.angle).toFloat())
            val w = b.half.x.toFloat(); val h = b.half.y.toFloat()
            rect(c, -w, -h, w * 2, h * 2, b.color, if (b.target) 3f else 5f)
            rect(c, -w + 2, -h + 2, w * 2 - 4, 2f, 0x45FFFFFF, 1f)
            if (b.kind == Kind.BREAKABLE) {
                // Clearly marked gray wall durability; ordinary colored pixels never display numbers.
                for (i in 0 until b.durability) circle(c, (i - (b.durability - 1) / 2f) * 12, 0f, 3f, navy)
            } else if (!b.target) {
                paint.color = 0xFF8193AC.toInt(); paint.strokeWidth = 2f
                c.drawLine(-w + 5, -h + 3, w - 5, h - 3, paint)
                if (b.kind == Kind.MOVING) text(c, "↔", 0f, 7f, 20f, navy)
            }
            c.restore()
        }
        for (p in e.pickups) {
            val col = p.power.color; val x = p.pos.x.toFloat(); val y = p.pos.y.toFloat()
            circle(c, x, y, (25 + 3 * sin(animationTime * 5)).toFloat(), (col and 0xFFFFFF) or 0x33000000)
            circle(c, x, y, 18f, col)
            text(c, p.power.icon, x, y + 8, 22f, navy)
            text(c, "⌄", x, y + 38, 21f, col)
        }
        for (b in e.balls) {
            if (!store.lowPerformance && !b.held) {
                val trail = b.vel.unit() * -17.0
                circle(c, (b.pos.x + trail.x).toFloat(), (b.pos.y + trail.y).toFloat(), 4f, 0x3353E3E8)
                circle(c, (b.pos.x + trail.x * .5).toFloat(), (b.pos.y + trail.y * .5).toFloat(), 5f, 0x6653E3E8)
            }
            circle(c, b.pos.x.toFloat(), b.pos.y.toFloat(), Rules.RADIUS.toFloat(), if (b.splitCharges > 0) colors[2] else Color.WHITE)
        }
        val px = e.paddle.x.toFloat(); val pw = e.paddle.width.toFloat(); val py = Rules.PADDLE_Y.toFloat()
        val pc = if (e.paddle.magnetTime > 0) colors[2] else if (e.paddle.laserTime > 0) colors[3] else cyan
        rect(c, px - pw / 2 - 5, py - 17, pw + 10, 34f, (pc and 0xFFFFFF) or 0x33000000, 17f)
        rect(c, px - pw / 2, py - 12, pw, 24f, pc, 12f)
        rect(c, px - pw / 2 + 12, py - 8, pw - 24, 3f, 0x99FFFFFF.toInt(), 2f)
        if (e.paddle.laserTime > 0) for (x in listOf(px - pw / 2 + 15, px + pw / 2 - 15))
            rect(c, x - 4, py - 19, 8f, 15f, Color.WHITE, 3f)
        if (e.phase == Phase.READY && !paused) button(c, "点击开始", 250f, 890f, 220f, 90f, primary = true) {
            if (e.launch()) { observedPhase = e.phase; save() }
        }
        val state = if (e.phase == Phase.READY) "点击开始发球 · 左右拖动挡板" else if (e.balls.any { it.held }) "磁力暂存中 · 轻点释放弹珠" else "左右拖动挡板 · 接住下落道具"
        text(c, state, 360f, 1120f, 26f, muted)
        val effects = listOfNotNull(
            if (e.paddle.wideTime > 0) "加宽 ${ceil(e.paddle.wideTime).toInt()}秒" else null,
            if (e.paddle.magnetTime > 0) "磁力 ${ceil(e.paddle.magnetTime).toInt()}秒" else null,
            if (e.paddle.laserTime > 0) "激光 ${ceil(e.paddle.laserTime).toInt()}秒" else null).joinToString(" · ")
        text(c, if (effects.isEmpty()) "剩余像素 ${e.remaining}  ·  开球 ${e.serves} 次" else effects, 360f, 1172f, 23f, muted)
        drawEffects(c)
        if (paused && e.phase !in listOf(Phase.WON, Phase.LOST)) pauseOverlay(c, e)
        else if (e.phase == Phase.WON || e.phase == Phase.LOST) resultOverlay(c, e)
    }
    private fun shade(c: Canvas) { paint.color = 0xDB071226.toInt(); c.drawRect(0f, 0f, 720f, 1200f, paint); buttons.clear() }
    private fun pauseOverlay(c: Canvas, e: Engine) {
        shade(c)
        text(c, "休息一下", 360f, 265f, 56f)
        button(c, "继续游戏", 100f, 345f, primary = true) { paused = false; clock = 0 }
        button(c, "重新开始", 100f, 465f) { start(e.level, e.infinite, e.seed) }
        button(c, "音效    ${if (store.sound) "开" else "关"}", 100f, 585f) { store.sound = !store.sound }
        button(c, "震动    ${if (store.vibration) "开" else "关"}", 100f, 705f) { store.vibration = !store.vibration }
        button(c, "更多设置", 100f, 825f) { settingsFrom = Screen.PLAY; screen = Screen.SETTINGS }
        button(c, "返回首页", 100f, 945f) { screen = Screen.HOME; save() }
    }
    private fun resultOverlay(c: Canvas, e: Engine) {
        shade(c)
        val won = e.phase == Phase.WON
        text(c, if (won) "通关成功" else "再试一次", 360f, 330f, 64f, if (won) cyan else colors[3])
        text(c, "本关分数 ${e.score}", 360f, 427f, 37f)
        text(c, "开球 ${e.serves} 次 · 剩余 ${e.lives} 条生命", 360f, 488f, 30f, muted)
        if (!won) text(c, "所有弹珠丢失，生命已用尽", 360f, 545f, 27f, muted)
        if (won) button(c, if (e.infinite) "下一波" else if (e.level < 30) "下一关" else "挑战无限模式",
            100f, 620f, primary = true) {
            if (e.infinite) start(e.level + 1, true, e.seed + 7919)
            else if (e.level < 30) start(e.level + 1) else start(1, true, System.currentTimeMillis())
        }
        button(c, "重新挑战", 100f, 750f, primary = !won) { start(e.level, e.infinite, e.seed) }
        button(c, "返回首页", 100f, 880f) { screen = Screen.HOME; save() }
    }
    private fun processEvents() {
        val e = engine ?: return
        for (event in e.drainEvents()) {
            when (event.type) {
                "break" -> {
                    burst(event.pos, event.color, if (store.lowPerformance) 3 else 7)
                    audio.play("break")
                    if (event.text.isNotEmpty() && event.amount % 3 == 0) { toast(event.text, event.pos); audio.play("combo") }
                }
                "power" -> { toast(event.text, event.pos); ballPulse = 1f; burst(event.pos, event.color, 16); audio.play("power"); vibrate(12) }
                "laser" -> { if (laserLife <= 0) laserBeams.clear(); laserBeams.add(event.pos.x.toFloat() to event.pos.y.toFloat()); laserLife = .18f }
                "life" -> { toast(event.text, event.pos); vibrate(25) }
                "hit" -> {
                    audio.play("hit")
                    if (hitGlows.size < 30) hitGlows.add(Particle(event.pos.x.toFloat(), event.pos.y.toFloat(), 0f, 0f, .12f, cyan, 13f))
                }
                "shot" -> audio.play("shot")
                "win" -> {
                    audio.play("win"); vibrate(35)
                    repeat(6) { burst(Vec(90.0 + it * 105, 430.0), colors[it], 22) }
                }
            }
        }
        if (observedPhase != e.phase) {
            if (e.phase == Phase.WON) {
                if (!e.infinite) progress.complete(e.level, e.score) else progress.best = max(progress.best, e.score)
            }
            observedPhase = e.phase; save()
        }
    }
    private fun vibrate(duration: Long) {
        if (store.vibration) {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    private fun toast(value: String, at: Vec) {
        if (toasts.size >= 8) toasts.removeAt(0)
        toasts.add(ToastFx(value, at.x.toFloat().coerceIn(140f, 580f), at.y.toFloat()))
        announceForAccessibility(value)
    }
    private fun burst(at: Vec, color: Int, count: Int) {
        val max = if (store.lowPerformance) 50 else 220
        repeat(count) {
            if (particles.size >= max) particles.removeAt(0)
            val angle = Random.nextDouble(0.0, PI * 2); val speed = Random.nextDouble(55.0, 240.0)
            particles.add(Particle(at.x.toFloat(), at.y.toFloat(), (cos(angle) * speed).toFloat(),
                (sin(angle) * speed).toFloat(), Random.nextDouble(.4, .9).toFloat(), color, Random.nextDouble(3.0, 7.0).toFloat()))
        }
    }
    private fun animate(dt: Float) {
        ballPulse = max(0f, ballPulse - dt * 2)
        laserLife = max(0f, laserLife - dt)
        hitGlows.forEach { it.life -= dt }; hitGlows.removeAll { it.life <= 0 }
        for (p in particles) { p.life -= dt; p.x += p.vx * dt; p.y += p.vy * dt }
        particles.removeAll { it.life <= 0f }
        for (t in toasts) { t.life -= dt; t.y -= dt * 35 }
        toasts.removeAll { it.life <= 0f }
    }
    private fun drawEffects(c: Canvas) {
        for (p in particles) circle(c, p.x, p.y, p.radius * min(1f, p.life * 3),
            (p.color and 0xFFFFFF) or ((255 * min(1f, p.life * 3)).toInt() shl 24))
        for (t in toasts) text(c, t.text, t.x, t.y - 25, 31f, cyan)
        for (g in hitGlows) circle(c, g.x, g.y, g.radius * (1 - g.life / .15f), 0x6653E3E8)
        if (laserLife > 0) {
            paint.strokeWidth = 4f; paint.color = Color.WHITE
            for ((x, y) in laserBeams) c.drawLine(x, y, x, Rules.PADDLE_Y.toFloat() - 20, paint)
        }
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!foreground) return false
        fun control(index: Int) {
            val x = (event.getX(index) - ox) / scale
            dragDistance = max(dragDistance, abs(x - downX))
            engine?.movePaddle(x.toDouble())
        }
        val x = (event.x - ox) / scale; val y = (event.y - oy) / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = buttons.lastOrNull { it.bounds.contains(x, y) }
                if (pressed == null && screen == Screen.PLAY && !paused && engine?.phase in listOf(Phase.READY, Phase.PLAYING) && y > 140) {
                    activePointer = event.getPointerId(0); dragging = true; downX = x; dragDistance = 0f; control(0)
                }
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val index = event.findPointerIndex(activePointer)
                if (index >= 0) control(index)
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == activePointer) {
                dragging = false; activePointer = -1
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) control(event.findPointerIndex(activePointer).coerceAtLeast(0))
                val button = pressed; pressed = null
                if (button != null && button.bounds.contains(x, y)) { performClick(); button.action() }
                else if (dragging && dragDistance < 18) {
                    if (engine?.launch() == true) { observedPhase = engine?.phase; save() }
                }
                dragging = false; activePointer = -1
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; pressed = null; activePointer = -1 }
        }
        invalidate(); return true
    }
}
