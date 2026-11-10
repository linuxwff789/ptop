package com.linuxwff.ptop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Panel(
    title: String? = null,
    trailing: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(C.panel)
            .border(1.dp, C.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (title != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(title, C.faint, 9.sp, FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                if (trailing != null) T(trailing, C.faint, 9.sp)
            }
            Spacer(Modifier.height(6.dp))
        }
        content()
    }
}

@Composable
fun Bar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 5.dp,
    fg: Color = C.barFg,
    bg: Color = C.barBg,
) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(bg)
    ) {
        if (f > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .fillMaxHeight()
                    .background(fg)
            )
        }
    }
}

/** Thin stepped sparkline, single hue: line + 8% wash underneath. */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = C.dim,
    max: Float = 1f,
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val n = values.size
        val step = size.width / (n - 1)
        val path = Path()
        val fill = Path()
        for (i in 0 until n) {
            val v = (values[i] / max).coerceIn(0f, 1f)
            val x = i * step
            val y = size.height - v * size.height
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, color.copy(alpha = 0.10f))
        drawPath(path, color, style = Stroke(width = 1.4.dp.toPx()))
        // marker at the live edge
        val lastY = size.height - (values.last() / max).coerceIn(0f, 1f) * size.height
        drawCircle(color, radius = 2.dp.toPx(), center = Offset(size.width, lastY))
    }
}

/** Row of text tabs; the selected one is inverted (white block, black text). */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (on) C.text else Color.Transparent)
                    .border(1.dp, if (on) C.text else C.line, RoundedCornerShape(6.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                T(label, if (on) C.bg else C.dim, 10.sp, if (on) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

/** Label / value pair used across the detail sheet. */
@Composable
fun KV(k: String, v: String, modifier: Modifier = Modifier, vColor: Color = C.text) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        T(k, C.faint, 10.sp, modifier = Modifier.width(74.dp))
        T(v, vColor, 10.sp, modifier = Modifier.weight(1f), maxLines = 3)
    }
}

@Composable
fun HLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(C.line))
}
