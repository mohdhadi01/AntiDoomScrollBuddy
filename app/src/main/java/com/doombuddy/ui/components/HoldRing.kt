package com.doombuddy.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Circular press-and-hold progress ring — the shared dismissal affordance used by the
 * production overlay and the onboarding preview (spec §32).
 */
@Composable
fun HoldRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 118.dp,
    ringWidth: Dp = 5.dp,
    trackColor: Color = Color.White.copy(alpha = 0.16f),
    progressColor: Color = Color(0xFFC6F24E),
) {
    Box(
        modifier
            .size(size)
            .drawBehind {
                val stroke = ringWidth.toPx()
                val inset = stroke / 2
                val arcSize = size.toPx() - stroke
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(arcSize, arcSize),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (progress > 0.01f) {
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(arcSize, arcSize),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            },
    )
}
