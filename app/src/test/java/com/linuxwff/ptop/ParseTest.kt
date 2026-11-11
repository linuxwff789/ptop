package com.linuxwff.ptop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseTest {
    private fun load(): Parsed {
        val text = javaClass.classLoader!!.getResource("snap.txt")!!.readText()
        return Parse.snapshot(text)
    }

    @Test
    fun systemSections() {
        val p = load()
        assertEquals(9, p.cpu.size)
        assertTrue(p.cpu[1].busy > 0)
        assertTrue(p.mem.totalKb > 0)
        assertTrue(p.procs.size > 100)
    }

    @Test
    fun uidIoSection() {
        val p = load()
        assertTrue("uidIo=${p.uidIo.size}", p.uidIo.isNotEmpty())
        assertTrue(p.uidIo.values.any { it[0] > 0 || it[1] > 0 })
    }

    @Test
    fun perCorePercentNonZero() {
        val p = load()
        val total = p.cpu[1].total
        val pct = p.cpu[1].busy.toFloat() / total
        assertTrue("pct=$pct", pct > 0f && pct <= 1f)
    }
}
