package com.linuxwff.ptop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxwff.ptop.Proc
import com.linuxwff.ptop.Snapshot

@Composable
fun OverviewPage(s: Snapshot, onProc: (Proc) -> Unit, mod: Modifier = Modifier) {
    Column(
        mod
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CpuPanel(s)
        MemPanel(s)
        StatPanel(s)
        TopPanel(s, onProc)
    }
}

@Composable
private fun CpuPanel(s: Snapshot) {
    Panel("处理器", "负载 " + s.load.joinToString(" ") { String.format(java.util.Locale.US, "%.2f", it) }) {
        Row(verticalAlignment = Alignment.Bottom) {
            T(fmtPct(s.cpuTotal), C.text, 22.sp, FontWeight.Medium)
            Spacer(Modifier.width(7.dp))
            Column {
                T("${s.coreCount} 核", C.faint, 9.sp)
                T(
                    s.cores.take(1).joinToString("") { "${it.freqMhz}MHz" },
                    C.faint, 9.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                T("PSI ${s.psiCpu.toInt()}%", C.faint, 9.sp)
                T("io ${s.psiIo.toInt()}%", C.faint, 9.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Sparkline(s.history, Modifier.fillMaxWidth().height(46.dp))
        Spacer(Modifier.height(9.dp))
        s.cores.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { c ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        T("${c.idx}", C.faint, 9.sp, modifier = Modifier.width(13.dp))
                        Bar(c.pct, Modifier.weight(1f), 4.dp)
                        Spacer(Modifier.width(6.dp))
                        T(padStart(String.format(java.util.Locale.US, "%.0f%%", c.pct * 100), 4), C.dim, 9.sp)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MemPanel(s: Snapshot) {
    Panel("内存", "已用 " + fmtPct(s.mem.usedPct)) {
        Row(verticalAlignment = Alignment.Bottom) {
            T(fmtKb(s.mem.usedKb), C.text, 18.sp, FontWeight.Medium)
            Spacer(Modifier.width(5.dp))
            T("/ ${fmtKb(s.mem.totalKb)}", C.dim, 10.sp)
            Spacer(Modifier.weight(1f))
            if (s.mem.swapTotalKb > 0) {
                T("交换 ${fmtKb(s.mem.swapUsedKb)}/${fmtKb(s.mem.swapTotalKb)}", C.faint, 9.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        Bar(s.mem.usedPct, Modifier.fillMaxWidth(), 7.dp)
        if (s.mem.swapTotalKb > 0) {
            Spacer(Modifier.height(5.dp))
            Bar(s.mem.swapPct, Modifier.fillMaxWidth(), 3.dp, C.faint)
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth()) {
            Metric("缓存", fmtKb(s.mem.cachedKb), Modifier.weight(1f))
            Metric("缓冲", fmtKb(s.mem.buffersKb), Modifier.weight(1f))
            Metric("脏页", fmtKb(s.mem.dirtyKb), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatPanel(s: Snapshot) {
    Panel("系统", "运行 " + fmtUptime(s.uptime)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val netRx = s.net.sumOf { it.rxRate }
            val netTx = s.net.sumOf { it.txRate }
            val rd = s.disks.sumOf { it.readRate }
            val wr = s.disks.sumOf { it.writeRate }
            Column(Modifier.weight(1f)) {
                Metric("网络 下行", fmtRate(netRx))
                Spacer(Modifier.height(5.dp))
                Metric("网络 上行", fmtRate(netTx))
            }
            Column(Modifier.weight(1f)) {
                Metric("磁盘 读", fmtRate(rd))
                Spacer(Modifier.height(5.dp))
                Metric("磁盘 写", fmtRate(wr))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            val batt = s.batt
            val hottest = s.temps.firstOrNull()
            Metric(
                "电池",
                if (batt == null) "-" else "${batt.capacity}% " + when (batt.status) {
                    "Charging" -> "chg"
                    "Discharging" -> "dis"
                    "Full" -> "full"
                    else -> ""
                },
                Modifier.weight(1f),
            )
            Metric("温度", hottest?.let { String.format(java.util.Locale.US, "%.1f°C", it.c) } ?: "-", Modifier.weight(1f))
            Metric("进程", "${s.procs.size}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, mod: Modifier = Modifier) {
    Column(mod) {
        T(label, C.faint, 8.5.sp)
        T(value, C.dim, 11.sp)
    }
}

@Composable
private fun TopPanel(s: Snapshot, onProc: (Proc) -> Unit) {
    val top = s.procs.sortedByDescending { it.cpu }.take(8)
    Panel("TOP 进程", "${s.procs.size} 个") {
        Row(Modifier.fillMaxWidth()) {
            T(pad("PID", 7), C.faint, 9.sp)
            T(padStart("CPU%", 5), C.faint, 9.sp)
            T(padStart("内存%", 6), C.faint, 9.sp)
            Spacer(Modifier.width(8.dp))
            T("名称", C.faint, 9.sp)
        }
        Spacer(Modifier.height(5.dp))
        top.forEach { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onProc(p) }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(pad("${p.pid}", 7), C.dim, 9.5.sp)
                T(padStart(String.format(java.util.Locale.US, "%.0f", p.cpu), 5), C.text, 9.5.sp)
                T(padStart(String.format(java.util.Locale.US, "%.1f", p.memBytes.toFloat() / (s.mem.totalKb * 1024).coerceAtLeast(1) * 100), 6), C.dim, 9.5.sp)
                Spacer(Modifier.width(8.dp))
                T(p.comm, C.dim, 9.5.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
