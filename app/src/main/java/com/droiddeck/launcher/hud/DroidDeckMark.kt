package com.droiddeck.launcher.hud

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path

/** The AriseOS mark: the A peak with the rising chevron above it and the inner triangle in the accent colour. */
internal object DroidDeckMark {
    private const val VIEW_X = 254f
    private const val VIEW_Y = 175f
    const val VIEW_W = 516f
    const val VIEW_H = 575f
    private const val CHEVRON_STROKE = 22f

    private val body: Path by lazy {
        Path().apply {
            moveTo(512f, 250f)
            lineTo(760f, 740f)
            lineTo(650f, 740f)
            lineTo(512f, 462f)
            lineTo(374f, 740f)
            lineTo(264f, 740f)
            close()
            offset(-VIEW_X, -VIEW_Y)
        }
    }

    private val chevron: Path by lazy {
        Path().apply {
            moveTo(392f, 270f)
            lineTo(512f, 190f)
            lineTo(632f, 270f)
            offset(-VIEW_X, -VIEW_Y)
        }
    }

    private val accent: Path by lazy {
        Path().apply {
            moveTo(512f, 560f)
            lineTo(590f, 716f)
            lineTo(434f, 716f)
            close()
            offset(-VIEW_X, -VIEW_Y)
        }
    }

    private val scaled = Path()
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun widthFor(height: Float): Float = height * VIEW_W / VIEW_H

    fun inkLeftFor(height: Float): Float = (264f - VIEW_X) * height / VIEW_H
    fun inkRightFor(height: Float): Float = (760f - VIEW_X) * height / VIEW_H

    fun draw(canvas: Canvas, left: Float, top: Float, height: Float, bodyColor: Int, orbColor: Int) {
        val s = height / VIEW_H
        matrix.setScale(s, s)
        matrix.postTranslate(left, top)
        paint.color = bodyColor
        body.transform(matrix, scaled)
        canvas.drawPath(scaled, paint)
        stroke.color = bodyColor
        stroke.strokeWidth = CHEVRON_STROKE * s
        chevron.transform(matrix, scaled)
        canvas.drawPath(scaled, stroke)
        paint.color = orbColor
        accent.transform(matrix, scaled)
        canvas.drawPath(scaled, paint)
    }
}
