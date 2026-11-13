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
    private var target = Vec(360.0, 600.0)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttons = mutableListOf<Button>()
    private var pressed: Button? = null
    private val particles = mutableListOf<Particle>()
    private val toasts = mutableListOf<ToastFx>()
    private var laserLife = 0f
    private var laserY = 0f
    private var stockPulse = 0f
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
        contentDescription = "小游戏。按住游戏区域拖动瞄准，松手发射弹珠。"
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
        observedPhase = Phase.AIM; screen = Screen.PLAY; paused = false
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
            Screen.PLAY -> { if (!paused && engine?.phase in listOf(Phase.AIM, Phase.FLIGHT)) paused = true else { screen = Screen.HOME; save() } }
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
        }
        if (foreground && !paused) animate(dt.toFloat())
        val description = when (screen) {
            Screen.HOME -> "小游戏 · 首页"
            Screen.LEVELS -> "小游戏 · 关卡选择"
            Screen.SETTINGS -> "小游戏 · 设置"
            Screen.PLAY -> engine?.let { e ->
                val state = if (paused) "暂停" else when (e.phase) {
                    Phase.AIM -> "等待发射"; Phase.FLIGHT -> "弹珠飞行"; Phase.WON -> "通关"; Phase.LOST -> "失败"
                }
                "小游戏 · 游戏中；第 ${e.level} 关；回合 ${e.rounds}；弹珠 ${e.stock}；$state"
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
        text(c, "一束微光，击碎整片星海", 360f, 298f, 29f, muted)
        for (i in 0..4) rect(c, 174f + i * 78, 370f + if (i % 2 == 0) 0 else 25,
            58f, 58f, colors[i], 14f)
        circle(c, 360f, 495f + (sin(animationTime * 2) * 9).toFloat(), 13f, Color.WHITE)
        val active = engine?.phase in listOf(Phase.AIM, Phase.FLIGHT)
        button(c, if (active) "继续游戏" else "开始游戏", 100f, 570f, primary = true) {
            if (active) { screen = Screen.PLAY; paused = false; clock = 0 } else start(progress.last)
        }
        button(c, "关卡选择 · ${progress.unlocked} / 30", 100f, 690f) { screen = Screen.LEVELS }
        button(c, "无限挑战", 100f, 810f) { start(1, true, System.currentTimeMillis()) }
        button(c, "设置", 100f, 930f) { settingsFrom = Screen.HOME; screen = Screen.SETTINGS }
        text(c, "最高分 ${progress.best}    ·    离线畅玩", 360f, 1110f, 25f, muted)
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
        text(c, "拖动瞄准 · 松手发射 · 弹珠上限 150", 360f, 910f, 25f, muted)
        button(c, "返回", 100f, 1020f, primary = true) { screen = settingsFrom }
    }
    private fun game(c: Canvas) {
        val e = engine ?: return
        text(c, if (e.infinite) "无限 · 第 ${e.level} 波" else "第 ${e.level} 关", 26f, 43f, 28f, align = Paint.Align.LEFT)
        text(c, "分数 ${e.score}", 26f, 84f, 25f, muted, Paint.Align.LEFT)
        val size = 32f + stockPulse * 8f
        text(c, "● × ${e.stock}", 390f, 57f, size, cyan)
        button(c, "暂停", 602f, 5f, 110f, 96f) { paused = true; dragging = false; save() }
        paint.color = 0xFF203853.toInt(); paint.strokeWidth = 2f
        c.drawLine(0f, 107f, 720f, 107f, paint)
        for (b in e.bodies) if (b.alive) {
            c.save(); c.translate(b.center.x.toFloat(), b.center.y.toFloat()); c.rotate(Math.toDegrees(b.angle).toFloat())
            val color = when (b.kind) {
                Kind.BLOCK -> colors[((b.hp - 1) / 4).coerceIn(0, colors.lastIndex)]
                Kind.BREAKABLE -> 0xFFD99463.toInt()
                Kind.PADDLE -> cyan
                Kind.MOVING -> 0xFFB693FF.toInt()
                else -> 0xFF486382.toInt()
            }
            val w = b.half.x.toFloat(); val h = b.half.y.toFloat()
            rect(c, -w, -h, w * 2, h * 2, color, if (b.kind == Kind.BLOCK) 11f else 6f)
            rect(c, -w + 5, -h + 4, w * 2 - 10, 3f, 0x45FFFFFF, 2f)
            if (b.breakable) text(c, b.hp.toString(), 0f, 10f, 29f, navy)
            else if (b.kind == Kind.WALL) {
                paint.color = 0x997A99BA.toInt(); paint.strokeWidth = 3f
                for (i in -2..2) c.drawLine(i * 16f - 7, -h + 5, i * 16f + 2, h - 5, paint)
            } else if (b.kind == Kind.MOVING) text(c, "↔", 0f, 8f, 22f, navy)
            c.restore()
        }
        for (p in e.pickups) {
            val col = colors[p.power.ordinal % colors.size]
            val pulse = 1.0 + .13 * sin(animationTime * 5)
            circle(c, p.pos.x.toFloat(), p.pos.y.toFloat(), (26 * pulse).toFloat(), (col and 0x00FFFFFF) or 0x33000000)
            circle(c, p.pos.x.toFloat(), p.pos.y.toFloat(), 18f, col)
            text(c, p.power.icon, p.pos.x.toFloat(), p.pos.y.toFloat() + 8, 22f, navy)
        }
        if (dragging && e.phase == Phase.AIM && !paused) {
            val pts = e.preview(target)
            for (i in 0 until pts.size - 1) {
                val d = pts[i + 1] - pts[i]; val length = d.length()
                val count = (length / 22).toInt()
                for (j in 0..count) {
                    val pos = pts[i] + d * (j * 22 / max(length, 1.0))
                    circle(c, pos.x.toFloat(), pos.y.toFloat(), 3f, 0x99FFFFFF.toInt())
                }
            }
        }
        for (b in e.balls) {
            if (!store.lowPerformance) {
                val trail = b.vel.unit() * -20.0
                circle(c, (b.pos.x + trail.x).toFloat(), (b.pos.y + trail.y).toFloat(), 5f, 0x3353E3E8)
                circle(c, (b.pos.x + trail.x * .5).toFloat(), (b.pos.y + trail.y * .5).toFloat(), 6f, 0x6653E3E8)
            }
            circle(c, b.pos.x.toFloat(), b.pos.y.toFloat(), 7f,
                if (b.explosion > 0) colors[3] else if (b.pierce > 0) colors[2] else Color.WHITE)
        }
        paint.color = 0x4453E3E8; paint.strokeWidth = 2f
        c.drawLine(15f, (Rules.BOTTOM + 8).toFloat(), 705f, (Rules.BOTTOM + 8).toFloat(), paint)
        circle(c, e.launcher.x.toFloat(), e.launcher.y.toFloat(), 22f, 0x3353E3E8)
        circle(c, e.launcher.x.toFloat(), e.launcher.y.toFloat(), 10f, cyan)
        val status = if (e.phase == Phase.AIM) "按住拖动瞄准，松手发射" else "剩余 ${e.balls.size + e.queue} 颗 · ${e.stock} 颗下回合生效"
        text(c, status, 360f, 1149f, 27f, muted)
        text(c, "回合 ${e.rounds} / ${e.maxRounds}    ·    目标 ${e.remaining}", 360f, 1186f, 23f, muted)
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
        text(c, "使用 ${e.rounds} 回合", 360f, 488f, 30f, muted)
        if (!won) text(c, "目标到达底线或回合用尽", 360f, 545f, 27f, muted)
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
                    burst(event.pos, colors[event.amount % colors.size], if (store.lowPerformance) 5 else 14)
                    audio.play("break")
                    if (event.text.isNotEmpty() && event.amount % 3 == 0) { toast(event.text, event.pos); audio.play("combo") }
                }
                "power" -> { toast(event.text, event.pos); stockPulse = 1f; burst(event.pos, cyan, 16); audio.play("power"); vibrate(12) }
                "laser" -> { laserY = event.pos.y.toFloat(); laserLife = .45f }
                "explosion" -> burst(event.pos, colors[3], 18)
                "hit" -> audio.play("hit")
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
        stockPulse = max(0f, stockPulse - dt * 2)
        laserLife = max(0f, laserLife - dt)
        for (p in particles) { p.life -= dt; p.x += p.vx * dt; p.y += p.vy * dt }
        particles.removeAll { it.life <= 0f }
        for (t in toasts) { t.life -= dt; t.y -= dt * 35 }
        toasts.removeAll { it.life <= 0f }
    }
    private fun drawEffects(c: Canvas) {
        for (p in particles) circle(c, p.x, p.y, p.radius * min(1f, p.life * 3),
            (p.color and 0xFFFFFF) or ((255 * min(1f, p.life * 3)).toInt() shl 24))
        for (t in toasts) text(c, t.text, t.x, t.y - 25, 31f, cyan)
        if (laserLife > 0) {
            paint.strokeWidth = 12f * laserLife; paint.color = Color.WHITE
            c.drawLine(0f, laserY, 720f, laserY, paint)
        }
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!foreground) return false
        val x = (event.x - ox) / scale; val y = (event.y - oy) / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = buttons.lastOrNull { it.bounds.contains(x, y) }
                if (pressed == null && screen == Screen.PLAY && !paused && engine?.phase == Phase.AIM && y in 110f..1100f) {
                    dragging = true; target = Vec(x.toDouble(), y.toDouble())
                }
            }
            MotionEvent.ACTION_MOVE -> if (dragging) target = Vec(x.toDouble(), y.toDouble())
            MotionEvent.ACTION_UP -> {
                val button = pressed; pressed = null
                if (button != null && button.bounds.contains(x, y)) { performClick(); button.action() }
                else if (dragging) {
                    target = Vec(x.toDouble(), y.toDouble())
                    if (engine?.shoot(target) == true) { observedPhase = Phase.FLIGHT; save() }
                }
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; pressed = null }
        }
        invalidate(); return true
    }
}
