package com.linuxwff.ptop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxwff.ptop.Proc
import com.linuxwff.ptop.Snapshot
import java.util.Locale

private enum class Sort { CPU, MEM, PID, NAME }

@Composable
fun ProcessPage(s: Snapshot, onProc: (Proc) -> Unit, mod: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(Sort.CPU) }
    var tree by remember { mutableStateOf(false) }

    val totalMem = (s.mem.totalKb * 1024).coerceAtLeast(1)
    val rows = remember(s.procs, query, sort, tree) {
        buildRows(s.procs, query.trim(), sort, tree)
    }

    Column(mod.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .background(C.ghost)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T("> ", C.faint, 11.sp)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) T("搜索名称 / PID / 用户", C.faint, 11.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = Mono, fontSize = 11.sp, color = C.text,
                        ),
                        cursorBrush = SolidColor(C.text),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    T("x", C.faint, 11.sp, modifier = Modifier.clickable { query = "" })
                }
            }
            Spacer(Modifier.width(8.dp))
            T("${rows.size}", C.faint, 10.sp)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Segmented(
                options = listOf("CPU", "内存", "PID", "名称"),
                selected = sort.ordinal,
                onSelect = { sort = Sort.entries[it] },
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .background(if (tree) C.text else C.ghost)
                    .clickable { tree = !tree }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                T("树", if (tree) C.bg else C.dim, 10.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp)) {
            T(pad("PID", 7), C.faint, 9.sp, modifier = Modifier.width(46.dp))
            T(pad("用户", 8), C.faint, 9.sp, modifier = Modifier.width(58.dp))
            T(padStart("CPU", 5), C.faint, 9.sp, modifier = Modifier.width(34.dp))
            T(padStart("内存", 5), C.faint, 9.sp, modifier = Modifier.width(34.dp))
            T(padStart("线程", 4), C.faint, 9.sp, modifier = Modifier.width(28.dp))
            Spacer(Modifier.width(6.dp))
            T("名称", C.faint, 9.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(rows, key = { it.proc.pid }) { row ->
                val p = row.proc
                val hot = p.cpu >= 25f
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onProc(p) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(pad("${p.pid}", 7), C.dim, 9.5.sp, modifier = Modifier.width(46.dp))
                    T(pad(p.user, 8), C.faint, 9.5.sp, modifier = Modifier.width(58.dp))
                    T(
                        padStart("${p.cpu.toInt()}", 5),
                        if (hot) C.text else C.dim,
                        9.5.sp,
                        if (hot) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.width(34.dp),
                    )
                    T(
                        padStart(String.format(Locale.US, "%.1f", p.memBytes.toFloat() / totalMem * 100), 5),
                        C.dim, 9.5.sp, modifier = Modifier.width(34.dp),
                    )
                    T(padStart("${p.threads}", 4), C.faint, 9.5.sp, modifier = Modifier.width(28.dp))
                    Spacer(Modifier.width(6.dp))
                    row.tree?.let { TreeLines(it) }
                    AppIcon(p.pkg, 14.dp)
                    Spacer(Modifier.width(4.dp))
                    T(p.comm, C.dim, 9.5.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private class PRow(val proc: Proc, val tree: TreeMeta?)

/** depth, whether this node is the last child, and per-ancestor "still has siblings". */
private class TreeMeta(val depth: Int, val isLast: Boolean, val flags: List<Boolean>)

/** Draws tree guides with real lines instead of ├ / └ glyphs. */
@Composable
private fun TreeLines(meta: TreeMeta) {
    val step = 11.dp
    Box(Modifier.width((step * meta.depth).coerceAtLeast(0.dp)).height(16.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = step.toPx()
            val h = size.height
            val stroke = 1.dp.toPx()
            for (level in 0 until meta.depth - 1) {
                if (meta.flags.getOrElse(level) { false }) {
                    val x = level * w + w / 2
                    drawLine(C.lineHi, Offset(x, 0f), Offset(x, h), stroke)
                }
            }
            if (meta.depth > 0) {
                val x = (meta.depth - 1) * w + w / 2
                drawLine(C.lineHi, Offset(x, 0f), Offset(x, if (meta.isLast) h / 2 else h), stroke)
                drawLine(C.lineHi, Offset(x, h / 2), Offset(x + w / 2, h / 2), stroke)
            }
        }
    }
}

private fun buildRows(all: List<Proc>, q: String, sort: Sort, tree: Boolean): List<PRow> {
    val filtered = if (q.isEmpty()) all else all.filter {
        it.comm.contains(q, true) || it.pid.toString().contains(q) ||
            it.user.contains(q, true) || (it.pkg?.contains(q, true) == true)
    }
    val cmp = when (sort) {
        Sort.CPU -> compareByDescending<Proc> { it.cpu }
        Sort.MEM -> compareByDescending<Proc> { it.memBytes }
        Sort.PID -> compareBy<Proc> { it.pid }
        Sort.NAME -> compareBy<Proc> { it.comm.lowercase() }
    }
    val sorted = filtered.sortedWith(cmp)
    if (!tree) return sorted.map { PRow(it, null) }

    val byPid = sorted.associateBy { it.pid }
    val children = sorted.groupBy { it.ppid }
    val roots = sorted.filter { it.ppid == 0 || byPid[it.ppid] == null || it.ppid == it.pid }
    val out = ArrayList<PRow>(sorted.size)
    val seen = HashSet<Int>(sorted.size * 2)

    fun walk(p: Proc, depth: Int, isLast: Boolean, flags: List<Boolean>) {
        if (depth > 14 || !seen.add(p.pid)) return
        out.add(PRow(p, TreeMeta(depth, isLast, flags)))
        val kids = children[p.pid]?.filter { it.pid != p.pid } ?: emptyList()
        kids.forEachIndexed { i, k ->
            walk(k, depth + 1, i == kids.size - 1, flags + !isLast)
        }
    }
    for (r in roots.sortedWith(cmp)) walk(r, 0, true, emptyList())
    for (p in sorted) if (!seen.contains(p.pid)) out.add(PRow(p, TreeMeta(0, true, emptyList())))
    return out
}
