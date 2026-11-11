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
        Panel("吞吐") {
            Row(verticalAlignment = Alignment.Bottom) {
                Column {
                    T("下行", C.faint, 8.5.sp)
                    T(fmtRate(rx), C.text, 17.sp, FontWeight.Medium)
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    T("上行", C.faint, 8.5.sp)
                    T(fmtRate(tx), C.text, 17.sp, FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                T("峰值 ${fmtRate(peak.toDouble())}", C.faint, 8.5.sp)
            }
            Spacer(Modifier.height(8.dp))
            Sparkline(s.netHistory, Modifier.fillMaxWidth().height(52.dp), max = peak)
        }

        Panel("按应用流量", if (s.netReady) "${s.appNet.size} 个" else "未授权") {
            if (!s.netReady) {
                T("需要「使用情况访问」权限", C.dim, 10.sp, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                T("已尝试通过 root 自动授权；若无效请在系统设置中开启。", C.faint, 8.5.sp, maxLines = 3)
            } else {
                val top = s.appNet.sortedByDescending { it.rxRate + it.txRate }.take(10)
                val peak = (top.maxOfOrNull { it.rxRate + it.txRate } ?: 1.0).coerceAtLeast(1.0)
                top.forEach { a ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T(a.name, C.dim, 9.5.sp, modifier = Modifier.width(84.dp))
                        Bar(((a.rxRate + a.txRate) / peak).toFloat(), Modifier.weight(1f), 4.dp)
                        Spacer(Modifier.width(8.dp))
                        T("↓" + padStart(fmtRate(a.rxRate), 9), C.dim, 8.5.sp)
                        Spacer(Modifier.width(5.dp))
                        T("↑" + padStart(fmtRate(a.txRate), 9), C.faint, 8.5.sp)
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth()) {
                        T("", C.faint, 8.sp, modifier = Modifier.width(84.dp))
                        T("累计 ${fmtBytes((a.rxTotal + a.txTotal).toDouble())}", C.faint, 8.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        Panel("网络接口", "${s.net.size} 个") {
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
                    T("累计 收 ${fmtBytes(n.rx.toDouble())}  发 ${fmtBytes(n.tx.toDouble())}", C.faint, 8.sp)
                }
                Spacer(Modifier.height(7.dp))
            }
        }

        Panel("说明") {
            T("速率来自 /proc/net/dev 两次采样的差值", C.faint, 9.sp, maxLines = 2)
            T("含 lo / vpn / 蜂窝接口全部计数", C.faint, 9.sp, maxLines = 2)
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
        Panel("磁盘 IO") {
            Row(verticalAlignment = Alignment.Bottom) {
                Column {
                    T("读", C.faint, 8.5.sp)
                    T(fmtRate(rd), C.text, 17.sp, FontWeight.Medium)
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    T("写", C.faint, 8.5.sp)
                    T(fmtRate(wr), C.text, 17.sp, FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                T("peak ${fmtRate(peak.toDouble())}", C.faint, 8.5.sp)
            }
            Spacer(Modifier.height(8.dp))
            Sparkline(s.ioHistory, Modifier.fillMaxWidth().height(52.dp), max = peak)
        }

        if (s.appIo.isNotEmpty()) {
            Panel("按应用 IO", "top 10") {
                val top = s.appIo.sortedByDescending { it.readRate + it.writeRate }.take(10)
                val peak = (top.maxOfOrNull { it.readRate + it.writeRate } ?: 1.0).coerceAtLeast(1.0)
                top.forEach { a ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T(a.name, C.dim, 9.5.sp, modifier = Modifier.width(84.dp))
                        Bar(((a.readRate + a.writeRate) / peak).toFloat(), Modifier.weight(1f), 4.dp)
                        Spacer(Modifier.width(8.dp))
                        T("R" + padStart(fmtRate(a.readRate), 9), C.dim, 8.5.sp)
                        Spacer(Modifier.width(5.dp))
                        T("W" + padStart(fmtRate(a.writeRate), 9), C.faint, 8.5.sp)
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth()) {
                        T("", C.faint, 8.sp, modifier = Modifier.width(84.dp))
                        T("累计 R ${fmtBytes(a.readTotal.toDouble())}  W ${fmtBytes(a.writeTotal.toDouble())}", C.faint, 8.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        Panel("块设备", "${s.disks.size} 个") {
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

        Panel("内存明细") {
            KV("总量", fmtKb(s.mem.totalKb))
            KV("可用", fmtKb(s.mem.availKb))
            KV("空闲", fmtKb(s.mem.freeKb))
            KV("缓冲", fmtKb(s.mem.buffersKb))
            KV("缓存", fmtKb(s.mem.cachedKb))
            KV("脏页", fmtKb(s.mem.dirtyKb))
            KV("共享", fmtKb(s.mem.shmemKb))
            KV("交换", "${fmtKb(s.mem.swapUsedKb)} / ${fmtKb(s.mem.swapTotalKb)}")
            Spacer(Modifier.height(4.dp))
        }

        if (s.temps.isNotEmpty()) {
            Panel("温度", "${s.temps.size} 个") {
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

        Panel("系统") {
            KV("运行时长", fmtUptime(s.uptime))
            KV("负载", s.load.joinToString(" ") { String.format(Locale.US, "%.2f", it) })
            KV("PSI CPU", String.format(Locale.US, "%.2f%%", s.psiCpu))
            KV("PSI IO", String.format(Locale.US, "%.2f%%", s.psiIo))
            s.batt?.let { b ->
                KV("电池", "${b.capacity}% · ${b.status}")
                KV("电流", String.format(Locale.US, "%.0f mA", b.currentUa / 1000.0))
                KV("电压", String.format(Locale.US, "%.2f V", b.voltageUv / 1_000_000.0))
                KV("电池温度", String.format(Locale.US, "%.1f °C", b.tempC))
            }
            KV("核心数", "${s.coreCount}")
            KV("进程数", "${s.procs.size}")
        }
    }
}

@Composable
private fun NoteRow(text: String) {
    Box(Modifier.fillMaxWidth()) {
        T(text, C.faint, 9.sp, maxLines = 2)
    }
}
