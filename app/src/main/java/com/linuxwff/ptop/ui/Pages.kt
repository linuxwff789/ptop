package com.linuxwff.ptop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.linuxwff.ptop.Snapshot
import java.util.Locale

@Composable
fun NetworkPage(s: Snapshot, mod: Modifier = Modifier) {
    val rx = s.net.sumOf { it.rxRate }
    val tx = s.net.sumOf { it.txRate }
    val peak = (s.netHistory.maxOrNull() ?: 1f).coerceAtLeast(1024f)

    Column(
        mod.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Panel("throughput") {
            Row(verticalAlignment = Alignment.Bottom) {
                Column {
                    T("down", C.faint, 8.5.sp)
                    T(fmtRate(rx), C.text, 17.sp, FontWeight.Medium)
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    T("up", C.faint, 8.5.sp)
                    T(fmtRate(tx), C.text, 17.sp, FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                T("rx+tx peak ${fmtRate(peak.toDouble())}", C.faint, 8.5.sp)
            }
            Spacer(Modifier.height(8.dp))
            Sparkline(s.netHistory, Modifier.fillMaxWidth().height(52.dp), max = peak)
        }

        Panel("interfaces", "${s.net.size}") {
            val top = (s.net.maxOfOrNull { it.rxRate + it.txRate } ?: 1.0).coerceAtLeast(1.0)
            s.net.sortedByDescending { it.rxRate + it.txRate }.forEach { n ->
                val act = (n.rxRate + n.txRate) / top
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(n.name, C.dim, 10.sp, modifier = Modifier.width(96.dp))
                    Bar(act.toFloat(), Modifier.weight(1f), 4.dp)
                    Spacer(Modifier.width(8.dp))
                    T("↓" + padStart(fmtRate(n.rxRate), 9), C.dim, 9.sp)
                    Spacer(Modifier.width(6.dp))
                    T("↑" + padStart(fmtRate(n.txRate), 9), C.faint, 9.sp)
                }
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth()) {
                    T("", C.faint, 8.sp, modifier = Modifier.width(96.dp))
                    T("total rx ${fmtBytes(n.rx.toDouble())}  tx ${fmtBytes(n.tx.toDouble())}", C.faint, 8.sp)
                }
                Spacer(Modifier.height(7.dp))
            }
        }

        Panel("note") {
            T("速率来自 /proc/net/dev 差分，每 ${1} 秒刷新", C.faint, 9.sp, maxLines = 2)
            T("包含 lo / vpn / 蜂窝接口的全部计数", C.faint, 9.sp, maxLines = 2)
        }
    }
}

@Composable
fun DiskPage(s: Snapshot, mod: Modifier = Modifier) {
    val rd = s.disks.sumOf { it.readRate }
    val wr = s.disks.sumOf { it.writeRate }
    val peak = (s.ioHistory.maxOrNull() ?: 1f).coerceAtLeast(1024f)

    Column(
        mod.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Panel("disk io") {
            Row(verticalAlignment = Alignment.Bottom) {
                Column {
                    T("read", C.faint, 8.5.sp)
                    T(fmtRate(rd), C.text, 17.sp, FontWeight.Medium)
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    T("write", C.faint, 8.5.sp)
                    T(fmtRate(wr), C.text, 17.sp, FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                T("peak ${fmtRate(peak.toDouble())}", C.faint, 8.5.sp)
            }
            Spacer(Modifier.height(8.dp))
            Sparkline(s.ioHistory, Modifier.fillMaxWidth().height(52.dp), max = peak)
        }

        Panel("devices", "${s.disks.size}") {
            val top = (s.disks.maxOfOrNull { it.readRate + it.writeRate } ?: 1.0).coerceAtLeast(1.0)
            s.disks.sortedByDescending { it.readRate + it.writeRate }.forEach { d ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(d.name, C.dim, 10.sp, modifier = Modifier.width(86.dp))
                    Bar(((d.readRate + d.writeRate) / top).toFloat(), Modifier.weight(1f), 4.dp)
                    Spacer(Modifier.width(8.dp))
                    T("R" + padStart(fmtRate(d.readRate), 9), C.dim, 9.sp)
                    Spacer(Modifier.width(6.dp))
                    T("W" + padStart(fmtRate(d.writeRate), 9), C.faint, 9.sp)
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        Panel("memory") {
            KV("total", fmtKb(s.mem.totalKb))
            KV("available", fmtKb(s.mem.availKb))
            KV("free", fmtKb(s.mem.freeKb))
            KV("buffers", fmtKb(s.mem.buffersKb))
            KV("cached", fmtKb(s.mem.cachedKb))
            KV("dirty", fmtKb(s.mem.dirtyKb))
            KV("shmem", fmtKb(s.mem.shmemKb))
            KV("swap", "${fmtKb(s.mem.swapUsedKb)} / ${fmtKb(s.mem.swapTotalKb)}")
            Spacer(Modifier.height(4.dp))
        }

        if (s.temps.isNotEmpty()) {
            Panel("thermal", "${s.temps.size}") {
                s.temps.forEach { t ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T(t.type, C.dim, 9.5.sp, modifier = Modifier.width(120.dp))
                        Spacer(Modifier.weight(1f))
                        T(String.format(Locale.US, "%.1f °C", t.c), C.text, 10.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        Panel("system") {
            KV("uptime", fmtUptime(s.uptime))
            KV("load", s.load.joinToString(" ") { String.format(Locale.US, "%.2f", it) })
            KV("psi cpu", String.format(Locale.US, "%.2f%%", s.psiCpu))
            KV("psi io", String.format(Locale.US, "%.2f%%", s.psiIo))
            s.batt?.let { b ->
                KV("battery", "${b.capacity}% · ${b.status}")
                KV("current", String.format(Locale.US, "%.0f mA", b.currentUa / 1000.0))
                KV("voltage", String.format(Locale.US, "%.2f V", b.voltageUv / 1_000_000.0))
                KV("batt temp", String.format(Locale.US, "%.1f °C", b.tempC))
            }
            KV("cores", "${s.coreCount}")
            KV("procs", "${s.procs.size}")
        }
    }
}

@Composable
private fun NoteRow(text: String) {
    Box(Modifier.fillMaxWidth()) {
        T(text, C.faint, 9.sp, maxLines = 2)
    }
}
