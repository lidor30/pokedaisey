package com.pokedaisy.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

class SaveBackupsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val flash = Random(7).nextBytes(131072)

    @Test
    fun backsUpOnlyWhenTheSaveChanged() {
        val save = tmp.newFile("Pokemon Unbound.srm").apply { writeBytes(flash) }
        val first = SaveBackups.snapshot(save, now = 1_000_000L)
        assertNotNull(first)
        assertArrayEquals(flash, first!!.readBytes())
        assertEquals(SaveBackups.DIR_NAME, first.parentFile!!.name)
        assertTrue(first.name.startsWith("Pokemon Unbound.backup-") && first.name.endsWith(".srm"))

        // Same data, only mGBA's RTC footer moved on: no new copy.
        save.writeBytes(flash + ByteArray(16) { 1 })
        assertNull(SaveBackups.snapshot(save, now = 2_000_000L))

        save.writeBytes(flash.copyOf().also { it[100] = (it[100] + 1).toByte() })
        assertNotNull(SaveBackups.snapshot(save, now = 3_000_000L))
        assertEquals(2, SaveBackups.list(save).size)
    }

    @Test
    fun keepsTheNewest() {
        val save = tmp.newFile("game.sav")
        repeat(SaveBackups.KEEP + 3) { i ->
            save.writeBytes(flash.copyOf().also { it[0] = i.toByte() })
            SaveBackups.snapshot(save, now = 1_000_000L * (i + 1))
        }
        val kept = SaveBackups.list(save)
        assertEquals(SaveBackups.KEEP, kept.size)
        assertEquals((SaveBackups.KEEP + 2).toByte(), kept.first().readBytes()[0])
    }

    @Test
    fun skipsMissingOrEmptySaves() {
        assertNull(SaveBackups.snapshot(tmp.root.resolve("none.sav")))
        assertNull(SaveBackups.snapshot(tmp.newFile("empty.sav")))
        assertFalse(SaveBackups.dir(tmp.root.resolve("x.sav")).exists())
    }
}
