package com.linuxwff.ptop

/** One sampling tick, already resolved to display units. */
data class Snapshot(
    val ts: Long,
    val cpuTotal: Float,
    val cores: List<Core>,
    val load: List<Float>,
    val uptime: Long,
    val mem: Mem,
    val procs: List<Proc>,
    val net: List<NetIface>,
    val disks: List<Disk>,
    val temps: List<Temp>,
    val batt: Batt?,
    val psiCpu: Float,
    val psiIo: Float,
    val history: List<Float>,
    val netHistory: List<Float>,
    val ioHistory: List<Float>,
    val appIo: List<AppIo> = emptyList(),
    val appNet: List<AppNet> = emptyList(),
    val netReady: Boolean = false,
) {
    val coreCount: Int get() = cores.size.coerceAtLeast(1)
}

data class Core(val idx: Int, val pct: Float, val freqMhz: Int)

data class Mem(
    val totalKb: Long,
    val freeKb: Long,
    val availKb: Long,
    val buffersKb: Long,
    val cachedKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
    val dirtyKb: Long,
    val shmemKb: Long,
) {
    val usedKb: Long get() = totalKb - availKb
    val swapUsedKb: Long get() = swapTotalKb - swapFreeKb
    val usedPct: Float get() = if (totalKb <= 0) 0f else usedKb.toFloat() / totalKb
    val swapPct: Float get() = if (swapTotalKb <= 0) 0f else swapUsedKb.toFloat() / swapTotalKb
}

data class Proc(
    val pid: Int,
    val comm: String,
    val state: String,
    val ppid: Int,
    val utime: Long,
    val stime: Long,
    val threads: Int,
    val rssPages: Long,
    val startTime: Long,
    val cpu: Float,
    val memBytes: Long,
    val uid: Int,
    val user: String,
    val pkg: String?,
    val userApp: Boolean = false,
)

data class NetIface(
    val name: String,
    val rx: Long,
    val tx: Long,
    val rxRate: Double,
    val txRate: Double,
)

data class Disk(
    val name: String,
    val readSectors: Long,
    val writeSectors: Long,
    val readRate: Double,
    val writeRate: Double,
)

data class Temp(val zone: String, val type: String, val c: Float)

/** Per-uid block IO from /proc/uid_io/stats. */
data class AppIo(
    val uid: Int,
    val name: String,
    val pkg: String?,
    val readRate: Double,
    val writeRate: Double,
    val readTotal: Long,
    val writeTotal: Long,
)

/** Per-uid network counters from NetworkStatsManager. */
data class AppNet(
    val uid: Int,
    val name: String,
    val pkg: String?,
    val rxRate: Double,
    val txRate: Double,
    val rxTotal: Long,
    val txTotal: Long,
)

data class Batt(
    val capacity: Int,
    val status: String,
    val currentUa: Long,
    val voltageUv: Long,
    val tempC: Float,
)

data class RawProc(
    val pid: Int,
    val comm: String,
    val state: String,
    val ppid: Int,
    val utime: Long,
    val stime: Long,
    val threads: Int,
    val rssPages: Long,
    val startTime: Long,
)

class Parsed {
    val cpu = ArrayList<CpuTimes>(10)
    var mem = Mem(0, 0, 0, 0, 0, 0, 0, 0, 0)
    var load: List<Float> = listOf(0f, 0f, 0f)
    var uptimeSec: Double = 0.0
    val procs = ArrayList<RawProc>(1024)
    val net = ArrayList<NetIface>(8)
    val disks = ArrayList<Disk>(8)
    val temps = ArrayList<Temp>(12)
    var batt: Batt? = null
    val freqs = ArrayList<Int>(16)
    var psiCpu = 0f
    var psiIo = 0f
    val uidIo = HashMap<Int, LongArray>(256)
}

data class CpuTimes(
    val user: Long,
    val nice: Long,
    val system: Long,
    val idle: Long,
    val iowait: Long,
    val irq: Long,
    val softirq: Long,
    val steal: Long,
) {
    val total: Long get() = user + nice + system + idle + iowait + irq + softirq + steal
    val busy: Long get() = total - idle - iowait
}
