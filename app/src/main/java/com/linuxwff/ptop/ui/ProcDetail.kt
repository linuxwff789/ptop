package com.linuxwff.ptop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxwff.ptop.Proc
import com.linuxwff.ptop.Root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ProcDetail(proc: Proc, totalMem: Long, onClose: () -> Unit, onKilled: () -> Unit) {
    var raw by remember(proc.pid) { mutableStateOf<String?>(null) }
    var confirm by remember(proc.pid) { mutableStateOf<String?>(null) }
    var nonce by remember(proc.pid) { mutableStateOf(0) }
    BackHandler { onClose() }

    LaunchedEffect(proc.pid, nonce) {
        raw = withContext(Dispatchers.IO) { Root.detail(proc.pid) }
    }

    val sec = remember(raw) { splitSections(raw ?: "") }
    val status = sec["STATUS"].orEmpty()
    val cmd = sec["CMD"].orEmpty().firstOrNull()?.trim().orEmpty()
    val exe = sec["EXE"].orEmpty().firstOrNull().orEmpty()
    val cwd = sec["CWD"].orEmpty().firstOrNull().orEmpty()
    val fds = sec["FDS"].orEmpty().firstOrNull()?.trim().orEmpty()
    val thrCount = sec["THR"].orEmpty().firstOrNull()?.trim().orEmpty()

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(C.ghost)
                        .clickable { onClose() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) { T("‹ 返回", C.dim, 10.sp) }
                Spacer(Modifier.width(10.dp))
                T("PID ${proc.pid}", C.faint, 10.sp)
                Spacer(Modifier.weight(1f))
                T("${fds.ifEmpty { "-" }} fd", C.faint, 9.sp)
            }

            Spacer(Modifier.height(14.dp))
            T(proc.comm, C.text, 20.sp, FontWeight.Medium, maxLines = 2)
            Spacer(Modifier.height(3.dp))
            T(
                buildString {
                    append(proc.user)
                    if (proc.pkg != null) append("  ").append(proc.pkg)
                },
                C.faint, 9.5.sp, maxLines = 2,
            )

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                BigStat("CPU", String.format(Locale.US, "%.1f", proc.cpu) + "%")
                BigStat("内存", String.format(Locale.US, "%.1f", proc.memBytes.toFloat() / totalMem.coerceAtLeast(1) * 100) + "%")
                BigStat("常驻", fmtBytes(proc.memBytes.toDouble()))
                BigStat("线程", thrCount.ifEmpty { "${proc.threads}" })
            }

            Spacer(Modifier.height(16.dp))
            HLine()
            Spacer(Modifier.height(10.dp))

            KV("状态", proc.state, vColor = C.dim)
            KV("父进程", "${proc.ppid}")
            if (exe.isNotEmpty()) KV("路径", exe)
            if (cwd.isNotEmpty()) KV("工作目录", cwd)
            KV("用户时间", "${proc.utime} ticks")
            KV("内核时间", "${proc.stime} ticks")

            if (cmd.isNotEmpty() && cmd != proc.comm) {
                Spacer(Modifier.height(10.dp))
                T("命令行", C.faint, 8.5.sp)
                Spacer(Modifier.height(3.dp))
                T(cmd, C.dim, 10.sp, maxLines = 6)
            }

            Spacer(Modifier.height(16.dp))
            T("发送信号", C.faint, 8.5.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SigButton("TERM 终止") { confirm = "TERM" }
                SigButton("KILL 强杀", danger = true) { confirm = "KILL" }
                SigButton("STOP 暂停") { confirm = "STOP" }
                SigButton("CONT 继续") { confirm = "CONT" }
            }

            if (status.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HLine()
                Spacer(Modifier.height(10.dp))
                T("状态", C.faint, 8.5.sp)
                Spacer(Modifier.height(4.dp))
                status.take(14).forEach { line ->
                    T(line, C.dim, 9.5.sp, maxLines = 2)
                }
            }

            val threads = sec["THREADS"].orEmpty()
            if (threads.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                T("线程 (${threads.size})", C.faint, 8.5.sp)
                Spacer(Modifier.height(4.dp))
                threads.take(24).forEach { line ->
                    T(line, C.dim, 9.5.sp)
                }
            }

            val maps = sec["MAPS"].orEmpty()
            if (maps.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                T("内存映射 (${maps.size}) · 可上下左右滑动", C.faint, 8.5.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(C.ghost.copy(alpha = 0.35f))
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(Modifier.padding(6.dp)) {
                        maps.forEach { line ->
                            T(line, C.faint, 8.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }

        if (confirm != null) {
            val sig = confirm!!
            Box(
                Modifier
                    .fillMaxSize()
                    .background(C.bg.copy(alpha = 0.88f))
                    .clickable { confirm = null },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth(0.82f)
                        .background(C.panel)
                        .padding(18.dp)
                ) {
                    T("发送 SIG$sig", C.text, 14.sp, FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    T("pid ${proc.pid} · ${proc.comm}", C.dim, 10.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(
                            Modifier
                                .clickable { confirm = null }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) { T("取消", C.faint, 11.sp) }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .background(C.text)
                                .clickable {
                                    confirm = null
                                    Thread {
                                        Root.signal(proc.pid, sig)
                                        nonce++
                                    }.start()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { T("确认", C.bg, 11.sp, FontWeight.Medium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String) {
    Column {
        T(label, C.faint, 8.5.sp)
        T(value, C.text, 15.sp, FontWeight.Medium)
    }
}

@Composable
private fun SigButton(sig: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (danger) C.text else C.ghost)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        T(sig, if (danger) C.bg else C.dim, 10.sp)
    }
}

internal fun splitSections(text: String): Map<String, List<String>> {
    val out = HashMap<String, List<String>>(8)
    var key: String? = null
    var buf = ArrayList<String>(8)
    fun flush() {
        val k = key ?: return
        out[k] = buf
    }
    for (line in text.lineSequence()) {
        if (line.startsWith("@@")) {
            flush()
            key = line.substring(2).trim()
            buf = ArrayList(8)
        } else if (key != null) {
            buf.add(line)
        }
    }
    flush()
    return out
}
