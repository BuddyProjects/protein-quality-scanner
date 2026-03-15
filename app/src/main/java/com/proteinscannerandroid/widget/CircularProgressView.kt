package com.proteinscannerandroid.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.proteinscannerandroid.R

class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f // 0..1
    private var trackColor = Color.parseColor("#333333")
    private var progressStartColor = Color.parseColor("#00D4E6")
    private var progressEndColor = Color.parseColor("#00D4E6")
    private var strokeWidth = 12f
    private var showText = true
    private var centerText = ""

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private val subtextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val rect = RectF()

    init {
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.CircularProgressView)
            trackColor = a.getColor(R.styleable.CircularProgressView_cpv_trackColor, trackColor)
            progressStartColor = a.getColor(R.styleable.CircularProgressView_cpv_progressColor, progressStartColor)
            strokeWidth = a.getDimension(R.styleable.CircularProgressView_cpv_strokeWidth, strokeWidth)
            showText = a.getBoolean(R.styleable.CircularProgressView_cpv_showText, showText)
            a.recycle()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - strokeWidth

        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Draw track
        trackPaint.strokeWidth = strokeWidth
        trackPaint.color = trackColor
        canvas.drawArc(rect, -90f, 360f, false, trackPaint)

        // Draw progress arc with gradient
        if (progress > 0) {
            progressPaint.strokeWidth = strokeWidth
            progressPaint.shader = SweepGradient(cx, cy, progressStartColor, progressEndColor).apply {
                setLocalMatrix(Matrix().apply { postRotate(-90f, cx, cy) })
            }
            canvas.drawArc(rect, -90f, 360f * progress, false, progressPaint)
        }

        // Draw center text
        if (showText) {
            val percent = (progress * 100).toInt()

            textPaint.textSize = radius * 0.45f
            canvas.drawText("$percent%", cx, cy + textPaint.textSize * 0.15f, textPaint)

            if (centerText.isNotEmpty()) {
                subtextPaint.textSize = radius * 0.22f
                canvas.drawText(centerText, cx, cy + textPaint.textSize * 0.15f + subtextPaint.textSize * 1.5f, subtextPaint)
            }
        }
    }

    fun setProgress(value: Float, animate: Boolean = false) {
        if (animate) {
            animateProgress(value)
        } else {
            progress = value.coerceIn(0f, 1f)
            invalidate()
        }
    }

    fun setCenterText(text: String) {
        centerText = text
        invalidate()
    }

    fun setProgressColor(color: Int) {
        progressStartColor = color
        progressEndColor = color
        invalidate()
    }

    fun animateProgress(target: Float) {
        ValueAnimator.ofFloat(progress, target.coerceIn(0f, 1f)).apply {
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun pulseAnimation() {
        animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(300)
            .withEndAction {
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .start()
            }
            .start()
    }
}
