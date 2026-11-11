package com.linuxwff.ptop

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

enum class Phase { CHECKING, NEED_ROOT, READY, ERROR }

/** uid -> readable owner, with package names for app uids. */
class Owners(private val ctx: Context) {
    private val pm = ctx.packageManager
    private val pkgs = HashMap<Int, String?>()
    private val short = HashMap<Int, String>()

    fun pkg(uid: Int): String? = pkgs.getOrPut(uid) {
        runCatching {
            @Suppress("DEPRECATION")
            pm.getPackagesForUid(uid)?.minByOrNull { it.length }
        }.getOrNull()
    }

    fun short(uid: Int): String = short.getOrPut(uid) {
        when {
            uid == 0 -> "root"
            uid == 1000 -> "system"
            uid == 1001 -> "radio"
            uid == 1013 -> "media"
            uid == 1020 -> "dhcp"
            uid == 2000 -> "shell"
            uid == 9999 -> "nobody"
            uid in 10000..19999 -> "u0_a${uid - 10000}"
            uid < 0 -> "?"
            else -> {
                val p = pkg(uid)
                if (p != null) p.substringAfterLast('.').take(10)
                else "u${uid / 100000}_a${uid % 100000 - 10000}"
            }
        }
    }
}

/**
 * Samples /proc via root and turns two consecutive ticks into per-process
 * CPU percentages, byte rates and a small history ring for the sparkline.
 */
class Monitor(private val ctx: Context) {

    val snap = mutableStateOf<Snapshot?>(null)
    val phase = mutableStateOf(Phase.CHECKING)
    val error = mutableStateOf("")
    var intervalMs: Long = 1500

    private val owners = Owners(ctx)
    private val appNetStats = AppNetStats(ctx, owners)
    private var prevUidIo = HashMap<Int, LongArray>(256)
    private var netAge = 0
    private var cachedNet: List<AppNet> = emptyList()
    private var usageGranted = false
    private var prevTicks = HashMap<Int, LongArray>(1024)
    private var prevCpuTotal = 0L
    private var prevWall = 0L
    private var prevNet = HashMap<String, LongArray>(8)
    private var prevDisk = HashMap<String, LongArray>(8)
    private var uidPid = HashMap<Int, Int>(1024)
    private var uidAge = 0
    private val hist = ArrayDeque<Float>(64)
    private val netHist = ArrayDeque<Float>(64)
    private val ioHist = ArrayDeque<Float>(64)
    private val hz = 100.0
    private val pageSize = 4096L
    private var lastGood = 0L

    suspend fun sampleLoop(interval: () -> Long) {
        while (currentCoroutineContext().isActive) {
            val t0 = SystemClock.elapsedRealtime()
            withContext(Dispatchers.IO) { runCatching { tick() }.onFailure { error.value = it.message ?: "tick" } }
            val rest = interval() - (SystemClock.elapsedRealtime() - t0)
            if (rest > 0) delay(rest)
        }
    }

    fun requestRootPermission() {
        phase.value = Phase.CHECKING
        Thread {
            val ok = Root.ensureDeployed(ctx)
            phase.value = if (ok) Phase.READY else Phase.NEED_ROOT
            if (!ok) error.value = Root.lastError
        }.apply { isDaemon = true }.start()
    }

    private fun tick() {
        if (!Root.granted) {
            if (Root.ensureDeployed(ctx).not()) {
                phase.value = Phase.NEED_ROOT
                error.value = Root.lastError
                return
            }
        }
        val text = Root.snapshot() ?: run {
            phase.value = Phase.ERROR
            error.value = Root.lastError.ifEmpty { "snapshot failed" }
            return
        }
        val p = Parse.snapshot(text)
        if (p.cpu.isEmpty()) {
            phase.value = Phase.ERROR
            error.value = "no /proc/stat data"
            return
        }

        val now = SystemClock.elapsedRealtime()
        val cores = p.cpu.size - 1
        val ncores = cores.coerceAtLeast(1)
        val totalCpu = p.cpu[0]
        val totalDelta = (totalCpu.total - prevCpuTotal).coerceAtLeast(0)
        val wall = if (prevWall == 0L) 0.0 else (now - prevWall).coerceAtLeast(1).toDouble()

        if (uidPid.isEmpty() || uidAge++ % 5 == 0) {
            val fresh = Root.uidMap()
            if (fresh.isNotEmpty()) uidPid = HashMap(fresh)
        }

        val procs = ArrayList<Proc>(p.procs.size)
        val newTicks = HashMap<Int, LongArray>(p.procs.size * 2)
        for (rp in p.procs) {
            val ticks = rp.utime + rp.stime
            val old = prevTicks[rp.pid]
            val cpu = when {
                old != null && old[0] == rp.startTime && totalDelta > 0 ->
                    ((ticks - old[1]).coerceAtLeast(0) * ncores * 100.0 / totalDelta).toFloat()
                old != null && old[0] == rp.startTime && wall > 0 ->
                    ((ticks - old[1]).coerceAtLeast(0) / (wall / 1000.0 * hz) * 100.0).toFloat()
                else -> {
                    val life = p.uptimeSec * hz - rp.startTime
                    if (life < 50.0) 0f
                    else (ticks * ncores * 100.0 / life).toFloat().coerceIn(0f, 100f)
                }
            }
            newTicks[rp.pid] = longArrayOf(rp.startTime, ticks)
            val uid = uidPid[rp.pid] ?: -1
            val pkgName = if (uid >= 10000) owners.pkg(uid) else null
            procs.add(
                Proc(
                    pid = rp.pid,
                    comm = pkgName ?: rp.comm.ifBlank { "[${rp.pid}]" },
                    state = rp.state,
                    ppid = rp.ppid,
                    utime = rp.utime,
                    stime = rp.stime,
                    threads = rp.threads,
                    rssPages = rp.rssPages,
                    startTime = rp.startTime,
                    cpu = cpu.coerceIn(0f, 800f),
                    memBytes = rp.rssPages * pageSize,
                    uid = uid,
                    user = owners.short(uid),
                    pkg = pkgName,
                )
            )
        }
        prevTicks = newTicks
        prevCpuTotal = totalCpu.total
        prevWall = now

        val coreList = ArrayList<Core>(ncores)
        for (i in 1..cores) {
            val c = p.cpu[i]
            val prev = prevCore.getOrNull(i)
            val v = if (prev == null) {
                c.busy.toFloat() / c.total.coerceAtLeast(1)
            } else {
                val dt = (c.total - prev.total).coerceAtLeast(0)
                if (dt > 0) (c.busy - prev.busy).toFloat() / dt else 0f
            }
            coreList.add(Core(i - 1, v.coerceIn(0f, 1f), p.freqs.getOrElse(i - 1) { 0 }))
        }
        prevCore = p.cpu

        val busy = totalCpu.total - totalCpu.idle - totalCpu.iowait
        val cpuShown = (if (prevBusy < 0) {
            busy.toFloat() / totalCpu.total.coerceAtLeast(1)
        } else if (totalDelta > 0) {
            (busy - prevBusy).toFloat() / totalDelta
        } else 0f).coerceIn(0f, 1f)
        prevBusy = busy

        hist.addLast(cpuShown)
        while (hist.size > 60) hist.removeFirst()

        val nets = ArrayList<NetIface>(p.net.size)
        val newNet = HashMap<String, LongArray>(p.net.size * 2)
        for (n in p.net) {
            val old = prevNet[n.name]
            val rx = if (old != null && wall > 0) (n.rx - old[0]).coerceAtLeast(0) / (wall / 1000.0) else 0.0
            val tx = if (old != null && wall > 0) (n.tx - old[1]).coerceAtLeast(0) / (wall / 1000.0) else 0.0
            newNet[n.name] = longArrayOf(n.rx, n.tx)
            nets.add(NetIface(n.name, n.rx, n.tx, rx, tx))
        }
        prevNet = newNet

        val disks = ArrayList<Disk>(p.disks.size)
        val newDisk = HashMap<String, LongArray>(p.disks.size * 2)
        for (d in p.disks) {
            val old = prevDisk[d.name]
            val rr = if (old != null && wall > 0) (d.readSectors - old[0]).coerceAtLeast(0) * 512.0 / (wall / 1000.0) else 0.0
            val wr = if (old != null && wall > 0) (d.writeSectors - old[1]).coerceAtLeast(0) * 512.0 / (wall / 1000.0) else 0.0
            newDisk[d.name] = longArrayOf(d.readSectors, d.writeSectors)
            disks.add(Disk(d.name, d.readSectors, d.writeSectors, rr, wr))
        }
        prevDisk = newDisk

        val appIo = ArrayList<AppIo>(p.uidIo.size)
        val newUidIo = HashMap<Int, LongArray>(p.uidIo.size * 2)
        for ((uid, v) in p.uidIo) {
            val old = prevUidIo[uid]
            val rr = if (old != null && wall > 0) (v[0] - old[0]).coerceAtLeast(0) / (wall / 1000.0) else 0.0
            val wr = if (old != null && wall > 0) (v[1] - old[1]).coerceAtLeast(0) / (wall / 1000.0) else 0.0
            newUidIo[uid] = v
            if (v[0] > 0 || v[1] > 0) {
                appIo.add(AppIo(uid, owners.short(uid), owners.pkg(uid), rr, wr, v[0], v[1]))
            }
        }
        prevUidIo = newUidIo
        if (!usageGranted) {
            usageGranted = true
            Thread { Root.exec("appops set ${ctx.packageName} GET_USAGE_STATS allow") }.apply { isDaemon = true }.start()
        }
        if (netAge++ % 3 == 0) cachedNet = appNetStats.tick()
        netHist.addLast(nets.sumOf { it.rxRate + it.txRate }.toFloat())
        while (netHist.size > 60) netHist.removeFirst()
        ioHist.addLast(disks.sumOf { it.readRate + it.writeRate }.toFloat())
        while (ioHist.size > 60) ioHist.removeFirst()

        snap.value = Snapshot(
            ts = System.currentTimeMillis(),
            cpuTotal = cpuShown,
            cores = coreList,
            load = p.load,
            uptime = p.uptimeSec.toLong(),
            mem = p.mem,
            procs = procs,
            net = nets,
            disks = disks,
            temps = p.temps.sortedByDescending { it.c }.take(6),
            batt = p.batt,
            psiCpu = p.psiCpu,
            psiIo = p.psiIo,
            history = hist.toList(),
            netHistory = netHist.toList(),
            ioHistory = ioHist.toList(),
            appIo = appIo,
            appNet = cachedNet,
            netReady = appNetStats.ready,
        )
        lastGood = System.currentTimeMillis()
        phase.value = Phase.READY
        error.value = ""
    }

    private var prevCore: List<CpuTimes> = emptyList()
    private var prevBusy = -1L
}
