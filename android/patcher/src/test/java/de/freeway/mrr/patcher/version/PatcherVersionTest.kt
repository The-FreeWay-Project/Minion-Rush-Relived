package de.freeway.mrr.patcher.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatcherVersionTest {

    @Test
    fun parsesFullVersion() {
        assertEquals(PatcherVersion(1, 0, 0), PatcherVersion.parse("A1.0.0"))
        assertEquals(1, PatcherVersion.parse("A1.0.0")?.major)
        assertEquals(0, PatcherVersion.parse("A1.0.0")?.minor)
        assertEquals(0, PatcherVersion.parse("A1.0.0")?.patch)
    }

    @Test
    fun parsesWithoutPrefixAndLowercasePrefix() {
        assertEquals(PatcherVersion.parse("A1.2.3"), PatcherVersion.parse("1.2.3"))
        assertEquals(PatcherVersion.parse("A1.2.3"), PatcherVersion.parse("a1.2.3"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals(PatcherVersion.parse("A2.0.1"), PatcherVersion.parse("  A2.0.1 "))
    }

    @Test
    fun rejectsMalformedInput() {
        assertNull(PatcherVersion.parse(null))
        assertNull(PatcherVersion.parse(""))
        assertNull(PatcherVersion.parse("banana"))
        assertNull(PatcherVersion.parse("A1.2"))
        assertNull(PatcherVersion.parse("A1.2.x"))
        assertNull(PatcherVersion.parse("B1.0.0"))
        assertNull(PatcherVersion.parse("1.2.3.4"))
    }

    @Test
    fun comparesNumericallyNotLexically() {
        val v19 = PatcherVersion.parse("A1.9.0")!!
        val v110 = PatcherVersion.parse("A1.10.0")!!
        assertTrue(v19 < v110)
        assertTrue(v110 > v19)
    }

    @Test
    fun comparesAcrossComponents() {
        val older = PatcherVersion.parse("A1.0.0")!!
        assertTrue(older < PatcherVersion.parse("A1.0.1")!!)
        assertTrue(older < PatcherVersion.parse("A1.1.0")!!)
        assertTrue(older < PatcherVersion.parse("A2.0.0")!!)
        assertEquals(0, older.compareTo(PatcherVersion.parse("A1.0.0")!!))
    }

    @Test
    fun formatsWithAPrefix() {
        assertEquals("A1.0.0", PatcherVersion.parse("1.0.0").toString())
        assertEquals("A0.9.7", PatcherVersion.parse("A0.9.7").toString())
    }

    @Test
    fun compareVersionsMapsToStatus() {
        assertEquals(UpdateStatus.UP_TO_DATE, compareVersions("A1.0.0", "A1.0.0"))
        assertEquals(UpdateStatus.UP_TO_DATE, compareVersions("A2.0.0", "A1.0.0"))
        assertEquals(UpdateStatus.UPDATE_AVAILABLE, compareVersions("A0.9.0", "A1.0.0"))
        assertEquals(UpdateStatus.UPDATE_AVAILABLE, compareVersions("A1.9.0", "A1.10.0"))
        assertEquals(UpdateStatus.INVALID, compareVersions("banana", "A1.0.0"))
        assertEquals(UpdateStatus.INVALID, compareVersions("A1.0.0", "banana"))
        assertEquals(UpdateStatus.INVALID, compareVersions(null, "A1.0.0"))
        assertEquals(UpdateStatus.INVALID, compareVersions("A1.0.0", null))
    }
}
