package com.xyq.livetranslate

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 视频字幕悬浮层：默认是可拖动的小尺寸字幕面板；收起时贴在屏幕侧边成为小胶囊。
 * 收起仅改变悬浮层外观，不暂停录音、音频发送或翻译会话。
 *
 * 控制条（状态点 + 暂停/打开/收起三个小图标）由用户手动开关：点面板空白处收起，
 * 再点一下复原。不做定时自动隐藏——字幕是持续刷新的，任何「无操作计时」都会被
 * 每条新字幕重置，结果就是正在看的时候永远不收。
 */
class SubtitleOverlay(
    private val context: Context,
    private val sessionMode: String,
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: AccentFrameLayout? = null
    private var panel: LinearLayout? = null
    private var headerRow: LinearLayout? = null
    private var collapsedHandle: TextView? = null
    private var pauseButton: ImageView? = null
    private var openButton: ImageView? = null
    private var collapseButton: ImageView? = null
    private val subtitleRows = mutableListOf<TextView>()
    // Two finalized dialogue rows plus one live karaoke row.
    private val subtitleHistory = ArrayDeque<String>()
    private var lastProcessedConfirmed = ""
    private var dot: View? = null
    private var lp: WindowManager.LayoutParams? = null
    private var collapsed = false
    private var collapsedOnLeft = false
    private var appliedStyle = -1
    private var expandedWidth = 0
    private var expandedX = 0
    private var expandedY = 0
    private var latestCurrent = ""
    private var controlsVisible = false

    companion object {
        private const val SUBTITLE_HISTORY_LINES = 3

        private fun applyStyleNow() {
        appliedStyle = StatusBus.styleVersion.get()
        val font = SettingsStore.fontSizeSp(context).toFloat()
        subtitleRows.forEach { it.textSize = font }
        applyAppearance()
    }

    private fun applyAppearance() {
        val window = root ?: return
        val container = panel ?: return
        val handle = collapsedHandle ?: return
        val params = lp ?: return
        val dm = context.resources.displayMetrics
        if (collapsed) {
            container.visibility = View.GONE
            handle.visibility = View.VISIBLE
            window.accentVisible = false
            window.background = collapsedBar(onLeft = collapsedOnLeft)
            params.width = dp(COLLAPSED_TOUCH_WIDTH_DP)
            params.height = dp(COLLAPSED_HEIGHT_DP)
            params.x = SubtitleOverlayGeometry.collapsedX(
                displayWidth = dm.widthPixels,
                collapsedWidth = params.width,
                onLeft = collapsedOnLeft,
            )
        } else {
            handle.visibility = View.GONE
            container.visibility = View.VISIBLE
            container.setPadding(dp(12), dp(8), dp(12), dp(10))
            window.accentVisible = true
            val opacity = SettingsStore.bgOpacityPct(context).coerceIn(20, 100)
            window.background = roundedRect(
                fill = Color.argb(255 * opacity / 100, 20, 29, 43),
                stroke = Color.argb(55, 255, 255, 255),
                radius = 22,
            )
            headerRow?.visibility = if (controlsVisible) View.VISIBLE else View.GONE

            params.width = expandedWidth
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.x = expandedX
            params.y = expandedY
        }
        runCatching { wm.updateViewLayout(window, params) }
        window.post { clampToDisplay(window, params) }
    }

    private fun clampToDisplay(window: View, params: WindowManager.LayoutParams) {
        if (root !== window || lp !== params) return
        val dm = context.resources.displayMetrics
        val width = params.width.takeIf { it > 0 }
            ?: window.width.takeIf { it > 0 }
            ?: dp(72)
        val height = params.height.takeIf { it > 0 }
            ?: window.height.takeIf { it > 0 }
            ?: dp(72)
        if (collapsed) {
            params.x = SubtitleOverlayGeometry.collapsedX(
                displayWidth = dm.widthPixels,
                collapsedWidth = width,
                onLeft = collapsedOnLeft,
            )
        } else {
            params.x = params.x.coerceIn(0, (dm.widthPixels - width).coerceAtLeast(0))
            expandedX = params.x
        }
        params.y = SubtitleOverlayGeometry.clampedY(
            displayHeight = dm.heightPixels,
            windowHeight = height,
            requestedY = params.y,
        )
        if (!collapsed) expandedY = params.y
        runCatching { wm.updateViewLayout(window, params) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun dragListener(
        container: View,
        params: WindowManager.LayoutParams,
    ) = object : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && dx * dx + dy * dy > dp(6) * dp(6)) moved = true
                    if (moved) {
                        if (collapsed) {
                            // 收起态只允许纵向拖动，x 保持贴边。
                            params.y = startY + dy.toInt()
                        } else {
                            params.x = startX + dx.toInt()
                            params.y = startY + dy.toInt()
                        }
                        runCatching { wm.updateViewLayout(container, params) }
                    }
                }
                MotionEvent.ACTION_UP -> if (moved) clampToDisplay(container, params) else onOverlayTap()
                MotionEvent.ACTION_CANCEL -> if (moved) clampToDisplay(container, params)
            }
            return true
        }
    }

    private fun controlButton(iconRes: Int, description: String) = ImageView(context).apply {
        setImageResource(iconRes)
        contentDescription = description
        scaleType = ImageView.ScaleType.FIT_CENTER
        val inset = dp(5)
        setPadding(inset, inset, inset, inset)
        background = roundedRect(
            fill = Color.argb(28, 255, 255, 255),
            stroke = Color.TRANSPARENT,
            radius = 8,
        )
        isClickable = true
        isFocusable = true
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    /**
     * 收起态背景：贴边一根实心蓝条 + 朝屏幕内侧渐隐的半透明晕。
     * 晕只为把触摸区做大，视觉上仍然只看得见那根细条。
     */
    private fun collapsedBar(onLeft: Boolean): Drawable {
        val solid = Color.argb(232, 0, 88, 188)
        val fadeColors = if (onLeft) {
            intArrayOf(Color.argb(64, 0, 88, 188), Color.TRANSPARENT)
        } else {
            intArrayOf(Color.TRANSPARENT, Color.argb(64, 0, 88, 188))
        }
        val halo = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, fadeColors).apply {
            cornerRadius = dp(COLLAPSED_BAR_WIDTH_DP).toFloat() / 2
        }
        val bar = GradientDrawable().apply {
            cornerRadius = dp(COLLAPSED_BAR_WIDTH_DP).toFloat() / 2
            setColor(solid)
        }
        val inward = dp(COLLAPSED_TOUCH_WIDTH_DP - COLLAPSED_BAR_WIDTH_DP)
        return LayerDrawable(arrayOf(halo, bar)).apply {
            // 蓝条永远压在贴屏幕边的那一侧，多出来的宽度全部让给内侧的晕。
            setLayerInset(1, if (onLeft) 0 else inward, 0, if (onLeft) inward else 0, 0)
        }
    }

    private fun roundedRect(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private class AccentFrameLayout(
        context: Context,
        density: Float,
    ) : FrameLayout(context) {
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.primary_container)
        }
        private val accentWidth = (4 * density).roundToInt().toFloat()
        private val accentInset = (12 * density).roundToInt().toFloat()
        private val accentRadius = (2 * density).roundToInt().toFloat()

        var accentVisible: Boolean = true
            set(value) {
                field = value
                invalidate()
            }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (!accentVisible || height <= accentInset * 2) return
            canvas.drawRoundRect(
                0f,
                accentInset,
                accentWidth,
                height - accentInset,
                accentRadius,
                accentRadius,
                accentPaint,
            )
        }
    }

}

internal object SubtitleOverlayPresentation {
    /** Completed lines stay above the live partial line, which always gets the bottom slot. */
    fun visibleRows(
        pending: List<String>,
        history: List<String>,
        current: String,
        maxRows: Int,
    ): List<String> {
        require(maxRows > 0)
        val partial = current.trim()
        if (partial.isNotEmpty()) {
            val completed = if (pending.isNotEmpty()) {
                pending.take(maxRows - 1)
            } else {
                history.takeLast(maxRows - 1)
            }
            return completed + partial
        }
        return if (pending.isNotEmpty()) pending.take(maxRows) else history.takeLast(maxRows)
    }
}

internal object SubtitleOverlayGeometry {
    fun expandedWidth(displayWidth: Int, density: Float): Int {
        val margin = (24 * density).roundToInt()
        val available = (displayWidth - margin).coerceAtLeast(1)
        val preferred = (displayWidth * 0.88f).roundToInt()
        val cap = (360 * density).roundToInt()
        val minimum = minOf((240 * density).roundToInt(), available)
        return minOf(preferred, cap, available).coerceAtLeast(minimum)
    }

    fun collapseToLeft(currentX: Int, currentWidth: Int, displayWidth: Int): Boolean =
        currentX + currentWidth / 2 <= displayWidth / 2

    fun collapsedX(displayWidth: Int, collapsedWidth: Int, onLeft: Boolean): Int =
        if (onLeft) 0 else (displayWidth - collapsedWidth).coerceAtLeast(0)

    fun clampedY(displayHeight: Int, windowHeight: Int, requestedY: Int): Int =
        requestedY.coerceIn(0, (displayHeight - windowHeight).coerceAtLeast(0))
}
