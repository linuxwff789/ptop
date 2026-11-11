package com.linuxwff.ptop

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxwff.ptop.ui.C
import com.linuxwff.ptop.ui.DiskPage
import com.linuxwff.ptop.ui.NetworkPage
import com.linuxwff.ptop.ui.OverviewPage
import com.linuxwff.ptop.ui.Panel
import com.linuxwff.ptop.ui.ProcDetail
import com.linuxwff.ptop.ui.ProcessPage
import com.linuxwff.ptop.ui.Sparkline
import com.linuxwff.ptop.ui.T
import com.linuxwff.ptop.ui.fmtPct
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val w = window
        @Suppress("DEPRECATION")
        run {
            w.statusBarColor = android.graphics.Color.BLACK
            w.navigationBarColor = android.graphics.Color.BLACK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            w.isNavigationBarContrastEnforced = false
        }
        setContent { AppRoot() }
    }

    override fun onStart() {
        super.onStart()
        (application as PtopApplication).monitor.start()
    }

    override fun onStop() {
        super.onStop()
        if (!MonitorService.running && !MonitorService.requested) {
            (application as PtopApplication).monitor.stop()
        }
    }

    override fun onDestroy() {
        if (!MonitorService.running && !MonitorService.requested) Root.close()
        super.onDestroy()
    }
}

private val TABS = listOf("概览", "进程", "网络", "磁盘")
private val INTERVALS = listOf(1000L, 1500L, 2000L, 3000L, 5000L)

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    val monitor = (ctx.applicationContext as PtopApplication).monitor
    var tab by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<Proc?>(null) }
    var intervalIdx by remember { mutableStateOf(1) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { TABS.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { tab = it }
    }

    val phase by monitor.phase
    val snap by monitor.snap
    val error by monitor.error

    LaunchedEffect(intervalIdx) { monitor.intervalMs = INTERVALS[intervalIdx] }


    var showExit by remember { mutableStateOf(false) }
    BackHandler(enabled = detail == null && !showExit) { showExit = true }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Header(
                snap = snap,
                interval = INTERVALS[intervalIdx],
                onInterval = { intervalIdx = (intervalIdx + 1) % INTERVALS.size },
                phase = phase,
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (phase) {
                    Phase.CHECKING -> Centered("正在申请 root…", "首次启动会弹出 KernelSU / Magisk 授权框")
                    Phase.NEED_ROOT -> RootGuide(error) { monitor.requestRootPermission() }
                    Phase.ERROR -> Centered(
                        "采样失败",
                        error.ifEmpty { "未知错误" },
                        actionLabel = "重试",
                        onAction = { monitor.requestRootPermission() },
                    )
                    Phase.READY -> {
                        val s = snap
                        if (s == null) Centered("正在读取 /proc…", "")
                        else HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            when (page) {
                                0 -> OverviewPage(s, onProc = { detail = it })
                                1 -> ProcessPage(s, onProc = { detail = it })
                                2 -> NetworkPage(s)
                                else -> DiskPage(s)
                            }
                        }
                    }
                }
            }

            TabBar(tab) { i -> scope.launch { pagerState.animateScrollToPage(i) } }
        }

        if (showExit) {
            ExitSheet(
                onCancel = { showExit = false },
                onExit = {
                    showExit = false
                    activity?.finish()
                },
                onBackground = {
                    showExit = false
                    MonitorService.requested = true
                    activity?.let { act ->
                        if (Build.VERSION.SDK_INT >= 33 &&
                            act.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            act.requestPermissions(
                                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 41
                            )
                        }
                        val svc = Intent(act, MonitorService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            act.startForegroundService(svc)
                        } else {
                            act.startService(svc)
                        }
                        act.moveTaskToBack(true)
                    }
                },
            )
        }

        detail?.let { p ->
            ProcDetail(
                proc = p,
                totalMem = (snap?.mem?.totalKb ?: 1L) * 1024,
                onClose = { detail = null },
                onKilled = { detail = null },
            )
        }
    }
}

@Composable
private fun Header(
    snap: Snapshot?,
    interval: Long,
    onInterval: () -> Unit,
    phase: Phase,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T("ptop", C.text, 14.sp, FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        T("进程监视器", C.faint, 8.5.sp)
        Spacer(Modifier.weight(1f))
        if (snap != null) {
            T("CPU " + fmtPct(snap.cpuTotal), C.dim, 9.5.sp)
            Spacer(Modifier.width(10.dp))
        }
        Box(
            Modifier
                .background(C.ghost)
                .clickable { onInterval() }
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            T("${interval / 1000.0}s", C.dim, 9.sp)
        }
        Spacer(Modifier.width(8.dp))
        T(
            if (phase == Phase.READY) "root" else "no root",
            if (phase == Phase.READY) C.dim else C.faint,
            9.sp,
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TABS.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) C.text else C.ghost)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                T(label, if (on) C.bg else C.dim, 10.sp, if (on) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun Centered(
    title: String,
    sub: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T(title, C.text, 14.sp, FontWeight.Medium)
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            T(sub, C.faint, 10.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (actionLabel != null) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .background(C.text)
                    .clickable { onAction?.invoke() }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) { T(actionLabel, C.bg, 11.sp) }
        }
    }
}

@Composable
private fun RootGuide(error: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        T("需要 root 权限", C.text, 16.sp, FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        T(
            "ptop 通过 su 读取 /proc，才能看到全部 ${'~'}850 个进程、其他应用的内存与线程。\n\n" +
                "点击下方按钮后，请在 KernelSU / Magisk 弹窗中点击「允许」。",
            C.dim, 10.sp, maxLines = 10,
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            T("last error: $error", C.faint, 9.sp, maxLines = 3)
        }
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .background(C.text)
                .clickable { onRetry() }
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            T("申请 root 权限", C.bg, 11.sp, FontWeight.Medium)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun ExitSheet(onCancel: () -> Unit, onExit: () -> Unit, onBackground: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(C.bg.copy(alpha = 0.92f)).clickable { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .background(C.panel)
                .clickable { }
                .padding(20.dp)
        ) {
            T("退出 ptop？", C.text, 15.sp, FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            T("「后台运行」会持有唤醒锁继续采样，可在通知栏随时停止。", C.faint, 9.5.sp, maxLines = 3)
            Spacer(Modifier.height(18.dp))
            Choice("直接退出", "结束进程，不再采样", onExit)
            Spacer(Modifier.height(8.dp))
            Choice("后台运行", "唤醒锁保活，继续刷新", onBackground)
            Spacer(Modifier.height(8.dp))
            Choice("取消", "", onCancel)
        }
    }
}

@Composable
private fun Choice(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(C.ghost)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        T(title, C.text, 12.sp)
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            T(sub, C.faint, 9.sp)
        }
    }
}
