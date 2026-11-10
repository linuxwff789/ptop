package com.linuxwff.ptop.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Monochrome palette: one hue, five steps of light. */
object C {
    val bg = Color(0xFF000000)
    val panel = Color(0xFF070707)
    val line = Color(0xFF212121)
    val lineHi = Color(0xFF3A3A3A)
    val text = Color(0xFFE6E6E6)
    val dim = Color(0xFF9C9C9C)
    val faint = Color(0xFF5C5C5C)
    val ghost = Color(0xFF141414)
    val sel = Color(0xFF1D1D1D)
    val barBg = Color(0xFF191919)
    val barFg = Color(0xFFDCDCDC)
}

val Mono = FontFamily.Monospace

private val base = TextStyle(
    fontFamily = Mono,
    fontSize = 11.sp,
    color = C.text,
    lineHeight = 13.5.sp,
)

@Composable
fun T(
    text: String,
    color: Color = C.text,
    size: TextUnit = 11.sp,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = base.copy(color = color, fontSize = size, fontWeight = weight),
        maxLines = maxLines,
        overflow = overflow,
    )
}

fun fmtBytes(b: Double): String {
    if (b < 0) return "0"
    val units = arrayOf("B", "K", "M", "G", "T")
    var v = b
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    return if (i == 0) "${v.toInt()}${units[i]}" else String.format(Locale.US, "%.1f%s", v, units[i])
}

fun fmtKb(kb: Long): String = fmtBytes(kb.toDouble() * 1024)

fun fmtRate(bps: Double): String = fmtBytes(bps) + "/s"

fun fmtPct(f: Float): String = String.format(Locale.US, "%.0f%%", f * 100)

fun fmtUptime(sec: Long): String {
    val d = sec / 86400
    val h = (sec % 86400) / 3600
    val m = (sec % 3600) / 60
    return if (d > 0) "${d}d ${h}h" else if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun pad(s: String, n: Int): String =
    if (s.length >= n) s.take(n) else s + " ".repeat(n - s.length)

fun padStart(s: String, n: Int): String =
    if (s.length >= n) s.takeLast(n) else " ".repeat(n - s.length) + s
