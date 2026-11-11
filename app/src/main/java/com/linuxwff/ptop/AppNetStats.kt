package com.linuxwff.ptop

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import android.os.SystemClock

/**
 * Per-uid network counters.
 *
 * There is no /proc equivalent on Android 13 (xt_qtaguid is gone and the eBPF
 * maps are not exposed), so this goes through NetworkStatsManager. It needs the
 * "usage access" app-op; the monitor grants it through root on first start.
 * Counters are cumulative-since-boot, so two samples give a rate.
 */
class AppNetStats(private val ctx: Context, private val owners: Owners) {
    private val nsm: NetworkStatsManager? =
        runCatching { ctx.getSystemService(NetworkStatsManager::class.java) }.getOrNull()

    private var prev = HashMap<Int, LongArray>(128)
    private var lastTs = 0L
    private var lastError = ""
    var ready = false
        private set

    fun error(): String = lastError

    fun tick(): List<AppNet> {
        val mgr = nsm ?: return emptyList()
        val now = System.currentTimeMillis()
        val start = now - SystemClock.elapsedRealtime()
        val acc = HashMap<Int, LongArray>(256)
        var ok = false
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                @Suppress("DEPRECATION")
                val stats = mgr.querySummary(type, null, start, now) ?: continue
                val b = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(b)
                    if (b.uid == Process.INVALID_UID) continue
                    val e = acc.getOrPut(b.uid) { LongArray(2) }
                    e[0] += b.rxBytes
                    e[1] += b.txBytes
                }
                ok = true
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
            }
        }
        if (!ok || acc.isEmpty()) return emptyList()
        ready = true
        lastError = ""

        val dt = if (lastTs == 0L) 0.0 else (now - lastTs).coerceAtLeast(1) / 1000.0
        val out = ArrayList<AppNet>(acc.size)
        for ((uid, v) in acc) {
            val old = prev[uid]
            val rx = if (old != null && dt > 0) (v[0] - old[0]).coerceAtLeast(0) / dt else 0.0
            val tx = if (old != null && dt > 0) (v[1] - old[1]).coerceAtLeast(0) / dt else 0.0
            if (v[0] + v[1] == 0L) continue
            out.add(
                AppNet(
                    uid = uid,
                    name = owners.short(uid),
                    pkg = owners.pkg(uid),
                    rxRate = rx,
                    txRate = tx,
                    rxTotal = v[0],
                    txTotal = v[1],
                )
            )
        }
        prev = acc
        lastTs = now
        return out
    }
}
