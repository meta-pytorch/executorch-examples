/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.video

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.executorch.athleticintelligence.squat.SquatAnnotationFrame
import com.example.executorch.athleticintelligence.squat.SquatOverlayTone
import com.example.executorch.athleticintelligence.squat.primaryAlert
import com.example.executorch.athleticintelligence.squat.squatAlertTone
import com.example.executorch.athleticintelligence.squat.squatAnnotationConnections
import com.example.executorch.athleticintelligence.squat.squatCenterCropProjection
import com.example.executorch.athleticintelligence.squat.visibleSquatLandmarks
import java.util.Locale
import kotlin.math.max

data class SquatAnnotationPalette(
    val success: Int = Color.rgb(73, 184, 138),
    val warning: Int = Color.rgb(224, 169, 63),
    val error: Int = Color.rgb(237, 73, 86),
    val scrim: Int = Color.argb(179, 8, 10, 11),
    val text: Int = Color.WHITE,
)

class SquatAnnotationCanvasRenderer(
    private val palette: SquatAnnotationPalette = SquatAnnotationPalette(),
) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.success
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.warning
        style = Paint.Style.FILL
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.scrim
        style = Paint.Style.FILL
    }
    private val badgeAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.text
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.text
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun draw(
        canvas: Canvas,
        frame: SquatAnnotationFrame,
        targetWidthPx: Int = canvas.width,
        targetHeightPx: Int = canvas.height,
    ) {
        if (targetWidthPx <= 0 || targetHeightPx <= 0) return

        val visible = visibleSquatLandmarks(frame.landmarks)
        if (visible.isNotEmpty()) {
            drawPose(canvas, frame, visible, targetWidthPx, targetHeightPx)
        }
        drawStatusBadge(canvas, frame, targetWidthPx)
    }

    private fun drawPose(
        canvas: Canvas,
        frame: SquatAnnotationFrame,
        visible: Map<String, com.example.executorch.athleticintelligence.squat.Landmark>,
        targetWidthPx: Int,
        targetHeightPx: Int,
    ) {
        val projection = squatCenterCropProjection(
            sourceWidthPx = frame.sourceWidthPx,
            sourceHeightPx = frame.sourceHeightPx,
            targetWidthPx = targetWidthPx.toFloat(),
            targetHeightPx = targetHeightPx.toFloat(),
        )
        fun point(name: String) = visible[name]?.let { landmark ->
            projection.project(landmark.xPx, landmark.yPx)
        }

        squatAnnotationConnections.forEach { (startName, endName) ->
            val start = point(startName)
            val end = point(endName)
            if (start != null && end != null) {
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
            }
        }
        visible.keys.forEach { name ->
            point(name)?.let { canvas.drawCircle(it.x, it.y, 9f, dotPaint) }
        }
    }

    private fun drawStatusBadge(canvas: Canvas, frame: SquatAnnotationFrame, targetWidthPx: Int) {
        val alert = frame.primaryAlert()
        val tone = alert?.let { squatAlertTone(it.level) } ?: SquatOverlayTone.WARNING
        badgeAccentPaint.color = when (tone) {
            SquatOverlayTone.SUCCESS -> palette.success
            SquatOverlayTone.WARNING -> palette.warning
            SquatOverlayTone.ERROR -> palette.error
        }
        val title = buildStatusTitle(frame)
        val detail = alert?.message ?: frame.currentCorrection
        val padding = 18f
        val left = 24f
        val top = 24f
        val maxWidth = targetWidthPx - left - 24f
        val titleWidth = textPaint.measureText(title)
        val detailWidth = detail?.let { detailPaint.measureText(it) } ?: 0f
        val width = minOf(maxWidth, max(titleWidth, detailWidth) + padding * 2f + 18f)
        val height = if (detail == null) 70f else 112f
        val rect = RectF(left, top, left + width, top + height)

        canvas.drawRoundRect(rect, 18f, 18f, badgePaint)
        canvas.drawCircle(left + padding, top + 35f, 8f, badgeAccentPaint)
        canvas.drawText(title, left + padding + 22f, top + 45f, textPaint)
        if (detail != null) {
            canvas.drawText(truncateToWidth(detail, detailPaint, width - padding * 2f), left + padding, top + 88f, detailPaint)
        }
    }

    private fun buildStatusTitle(frame: SquatAnnotationFrame): String {
        val view = frame.view?.wireName?.replaceFirstChar { it.uppercase(Locale.US) } ?: "Squat"
        val phase = frame.phase.name.lowercase(Locale.US).replace('_', ' ')
        val score = frame.latestScore?.let { " score $it" }.orEmpty()
        return "$view | $phase | reps ${frame.repCount}$score"
    }

    private fun truncateToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "..."
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + ellipsis
    }
}
