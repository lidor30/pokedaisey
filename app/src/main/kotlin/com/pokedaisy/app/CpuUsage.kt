package com.pokedaisy.app

import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * The app's CPU use for SETTINGS > FPS / CPU: this process's user + system time from /proc/self/stat
 * (readable by the app itself), as a share of the whole CPU - all cores - since the previous [sample].
 */
class CpuUsage {
    private val ticksPerSecond = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(100L).coerceAtLeast(1L)
    private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private var lastTicks = -1L
    private var lastNanos = 0L

    /** 0..100, or null on the first call (nothing to compare with yet) or when /proc can't be read. */
    fun sample(): Int? {
        val ticks = processTicks() ?: return null
        val now = System.nanoTime()
        val prev = lastTicks
        val prevNanos = lastNanos
        lastTicks = ticks
        lastNanos = now
        if (prev < 0 || now <= prevNanos) return null
        val cpuSeconds = (ticks - prev).toDouble() / ticksPerSecond
        val wallSeconds = (now - prevNanos) / 1e9
        return (cpuSeconds / wallSeconds / cores * 100).toInt().coerceIn(0, 100)
    }

    /** utime + stime: fields 14 and 15, counted after the command name's closing parenthesis. */
    private fun processTicks(): Long? = runCatching {
        val stat = File("/proc/self/stat").readText()
        val fields = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
        fields[11].toLong() + fields[12].toLong()
    }.getOrNull()
}
