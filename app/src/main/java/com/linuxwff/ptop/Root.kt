package com.linuxwff.ptop

import android.content.Context
import android.util.Base64
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/**
 * All privileged work goes through here.
 *
 * A single long-lived `su` shell is kept alive so a 1s refresh does not pay
 * process spawn + root-manager handshake every tick. If the shell cannot be
 * established (old su, SELinux, broken pipes) every call degrades to a
 * one-shot `su -c`.
 */
object Root {
    const val DIR = "/data/local/tmp/ptop"
    const val SNAP = "$DIR/snap.sh"
    const val DETAIL = "$DIR/detail.sh"
    const val UIDMAP = "$DIR/uidmap.sh"

    @Volatile
    var granted: Boolean = false
        private set

    @Volatile
    var lastError: String = ""
        private set

    private var shell: Process? = null
    private var shellIn: OutputStreamWriter? = null
    private var shellOut: BufferedReader? = null
    private val marker = AtomicLong(0)
    private val lock = Any()
    private val readerExec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ptop-root-reader").apply { isDaemon = true }
    }

    /** One-shot `su -c`. Returns stdout on exit 0, null otherwise. */
    fun exec(cmd: String, timeoutSec: Long = 20): String? = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        runCatching { p.outputStream.close() }
        val out = p.inputStream.bufferedReader().use { it.readText() }
        if (p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            if (p.exitValue() == 0) out
            else {
                lastError = out.trim().takeLast(160).ifEmpty { "exit ${p.exitValue()}" }
                null
            }
        } else {
            p.destroyForcibly()
            lastError = "timeout"
            null
        }
    } catch (t: Throwable) {
        lastError = t.message ?: t.javaClass.simpleName
        null
    }

    /** Copy an asset script to /data/local/tmp and chmod it. */
    fun deploy(ctx: Context, asset: String, target: String): Boolean {
        val b64 = Base64.encodeToString(ctx.assets.open(asset).readBytes(), Base64.NO_WRAP)
        val out = exec(
            "mkdir -p $DIR && printf '%s' '$b64' | base64 -d > $target && chmod 755 $target && echo DEPLOYED",
            timeoutSec = 25,
        )
        return out?.contains("DEPLOYED") == true
    }

    fun ensureDeployed(ctx: Context): Boolean {
        val probe = exec("test -x $SNAP -a -x $DETAIL -a -x $UIDMAP && echo READY")
        if (probe?.contains("READY") == true) {
            granted = true
            return true
        }
        granted = deploy(ctx, "ptop_snap.sh", SNAP) &&
            deploy(ctx, "ptop_detail.sh", DETAIL) &&
            deploy(ctx, "ptop_uidmap.sh", UIDMAP)
        return granted
    }

    /** Snapshot script over the persistent shell, falling back to one-shot su. */
    fun snapshot(timeoutMs: Long = 6000): String? = synchronized(lock) {
        if (shell?.isAlive != true && !startShellLocked()) {
            return exec("sh $SNAP", timeoutSec = 8)
        }
        val r = writeAndRead("sh $SNAP", timeoutMs)
        if (r == null) {
            stopShellLocked()
            return exec("sh $SNAP", timeoutSec = 8)
        }
        r
    }

    fun detail(pid: Int): String? = synchronized(lock) {
        if (shell?.isAlive != true && !startShellLocked()) {
            return exec("sh $DETAIL $pid", timeoutSec = 8)
        }
        val r = writeAndRead("sh $DETAIL $pid", 5000)
        if (r == null) {
            stopShellLocked()
            return exec("sh $DETAIL $pid", timeoutSec = 8)
        }
        r
    }

    /** uid per pid, used for the user column. Cheap enough to call rarely. */
    fun uidMap(): Map<Int, Int> {
        val out = synchronized(lock) {
            if (shell?.isAlive != true && !startShellLocked()) null
            else writeAndRead("sh $UIDMAP", 5000).also { if (it == null) stopShellLocked() }
        } ?: exec("sh $UIDMAP", timeoutSec = 8) ?: return emptyMap()
        val map = HashMap<Int, Int>(out.length / 12 + 8)
        for (line in out.lineSequence()) {
            val sp = line.indexOf(' ')
            if (sp <= 0) continue
            val uid = line.substring(0, sp).toIntOrNull() ?: continue
            var p = line.substring(sp + 1)
            if (p.startsWith("/proc/")) p = p.substring(6)
            val pid = p.toIntOrNull() ?: continue
            map[pid] = uid
        }
        return map
    }

    fun signal(pid: Int, sig: String): Boolean {
        val out = exec("kill -$sig $pid 2>&1; echo RC=\$?", timeoutSec = 6) ?: return false
        return out.contains("RC=0")
    }

    fun close() = synchronized(lock) { stopShellLocked() }

    private fun startShellLocked(): Boolean {
        stopShellLocked()
        val p = try {
            ProcessBuilder("su").start()
        } catch (t: Throwable) {
            lastError = t.message ?: "su failed"
            return false
        }
        shell = p
        shellOut = p.inputStream.bufferedReader(Charsets.UTF_8)
        shellIn = OutputStreamWriter(p.outputStream, Charsets.UTF_8)
        val err = p.errorStream.bufferedReader(Charsets.UTF_8)
        Thread {
            runCatching { while (err.readLine() != null) Unit }
        }.apply { isDaemon = true }.start()

        val probe = writeAndRead("id", 10_000)
        if (probe != null && probe.contains("uid=0")) {
            granted = true
            return true
        }
        lastError = if (probe == null) "root shell timeout" else probe.trim().take(120)
        stopShellLocked()
        return false
    }

    private fun writeAndRead(cmd: String, timeoutMs: Long): String? {
        val w = shellIn ?: return null
        val r = shellOut ?: return null
        val m = "__PTOP_${marker.incrementAndGet()}__"
        try {
            w.write(cmd)
            w.write("\n")
            w.write("echo $m\n")
            w.flush()
        } catch (t: Throwable) {
            return null
        }
        val sb = StringBuilder(16 * 1024)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) return null
            val line = readLine(r, left) ?: return null
            if (line == m) return sb.toString()
            sb.append(line).append('\n')
        }
    }

    private fun readLine(r: BufferedReader, timeoutMs: Long): String? {
        val f = readerExec.submit<String?> { r.readLine() }
        return try {
            f.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (t: TimeoutException) {
            f.cancel(true)
            null
        } catch (t: Throwable) {
            null
        }
    }

    private fun stopShellLocked() {
        runCatching { shellIn?.close() }
        runCatching { shell?.destroy() }
        shell = null
        shellIn = null
        shellOut = null
    }
}
