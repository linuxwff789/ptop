package com.linuxwff.ptop

/** Parses the tab-separated output of assets/ptop_snap.sh. */
object Parse {

    fun snapshot(text: String): Parsed {
        val out = Parsed()
        for (line in text.lineSequence()) {
            if (line.isEmpty()) continue
            when {
                line.startsWith("@@C\t") -> {
                    // "@@C\tcpu  1 2 3 ..."
                    val rest = line.substring(4).trim()
                    val f = rest.split(' ')
                    var i = 0
                    while (i < f.size && f[i].isEmpty()) i++
                    if (i + 9 > f.size) continue
                    out.cpu.add(
                        CpuTimes(
                            user = f[i + 1].toLongOrNull() ?: 0,
                            nice = f[i + 2].toLongOrNull() ?: 0,
                            system = f[i + 3].toLongOrNull() ?: 0,
                            idle = f[i + 4].toLongOrNull() ?: 0,
                            iowait = f[i + 5].toLongOrNull() ?: 0,
                            irq = f[i + 6].toLongOrNull() ?: 0,
                            softirq = f[i + 7].toLongOrNull() ?: 0,
                            steal = f[i + 8].toLongOrNull() ?: 0,
                        )
                    )
                }
                line.startsWith("@@M\t") -> {
                    val f = line.substring(4).split('\t')
                    if (f.size >= 9) {
                        out.mem = Mem(
                            totalKb = f[0].toLongOrNull() ?: 0,
                            freeKb = f[1].toLongOrNull() ?: 0,
                            availKb = f[2].toLongOrNull() ?: 0,
                            buffersKb = f[3].toLongOrNull() ?: 0,
                            cachedKb = f[4].toLongOrNull() ?: 0,
                            swapTotalKb = f[5].toLongOrNull() ?: 0,
                            swapFreeKb = f[6].toLongOrNull() ?: 0,
                            dirtyKb = f[7].toLongOrNull() ?: 0,
                            shmemKb = f[8].toLongOrNull() ?: 0,
                        )
                    }
                }
                line.startsWith("@@L\t") -> {
                    val f = line.substring(4).split('\t')
                    if (f.size >= 3) out.load = listOf(
                        f[0].toFloatOrNull() ?: 0f,
                        f[1].toFloatOrNull() ?: 0f,
                        f[2].toFloatOrNull() ?: 0f,
                    )
                }
                line.startsWith("@@U\t") -> out.uptimeSec = line.substring(4).trim().toDoubleOrNull() ?: 0.0
                line.startsWith("@@P\t") -> {
                    val f = line.split('\t')
                    if (f.size >= 10) {
                        val pid = f[1].toIntOrNull()
                        if (pid != null) {
                            out.procs.add(
                                RawProc(
                                pid = pid,
                                comm = f[2],
                                state = f[3],
                                ppid = f[4].toIntOrNull() ?: 0,
                                utime = f[5].toLongOrNull() ?: 0,
                                stime = f[6].toLongOrNull() ?: 0,
                                threads = f[7].toIntOrNull() ?: 0,
                                rssPages = f[8].toLongOrNull() ?: 0,
                                startTime = f[9].toLongOrNull() ?: 0,
                            )
                        )
                        }
                    }
                }
                line.startsWith("@@N\t") -> {
                    val f = line.split('\t')
                    if (f.size >= 4) out.net.add(
                        NetIface(f[1], f[2].toLongOrNull() ?: 0, f[3].toLongOrNull() ?: 0, 0.0, 0.0)
                    )
                }
                line.startsWith("@@D\t") -> {
                    val f = line.split('\t')
                    if (f.size >= 5) out.disks.add(
                        Disk(f[1], f[3].toLongOrNull() ?: 0, f[4].toLongOrNull() ?: 0, 0.0, 0.0)
                    )
                }
                line.startsWith("@@T\t") -> {
                    val f = line.split('\t')
                    if (f.size >= 4) out.temps.add(
                        Temp(f[1], f[2], f[3].toFloatOrNull() ?: 0f)
                    )
                }
                line.startsWith("@@B\t") -> {
                    val f = line.split('\t')
                    if (f.size >= 6) out.batt = Batt(
                        capacity = f[1].toIntOrNull() ?: 0,
                        status = f[2],
                        currentUa = f[3].toLongOrNull() ?: 0,
                        voltageUv = f[4].toLongOrNull() ?: 0,
                        tempC = (f[5].toLongOrNull() ?: 0L) / 10f,
                    )
                }
                line.startsWith("@@F\t") -> {
                    val f = line.split('\t')
                    if (f.size >= 3) out.freqs.add((f[2].toLongOrNull() ?: 0L).toInt() / 1000)
                }
                line.startsWith("@@PC\t") -> out.psiCpu = line.substring(5).trim().toFloatOrNull() ?: 0f
                line.startsWith("@@PI\t") -> out.psiIo = line.substring(5).trim().toFloatOrNull() ?: 0f
            }
        }
        return out
    }
}
