package de.freeway.mrr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PatchVersionTest {

    @Test
    void parsesFullVersion() {
        PatchVersion v = PatchVersion.parse("A1.0.0");
        assertEquals(1, v.getMajor());
        assertEquals(0, v.getMinor());
        assertEquals(0, v.getPatch());
    }

    @Test
    void parsesWithoutPrefixAndLowercasePrefix() {
        assertEquals(PatchVersion.parse("A1.2.3"), PatchVersion.parse("1.2.3"));
        assertEquals(PatchVersion.parse("A1.2.3"), PatchVersion.parse("a1.2.3"));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals(PatchVersion.parse("A2.0.1"), PatchVersion.parse("  A2.0.1 "));
    }

    @Test
    void rejectsMalformedInput() {
        assertNull(PatchVersion.parse(null));
        assertNull(PatchVersion.parse(""));
        assertNull(PatchVersion.parse("banana"));
        assertNull(PatchVersion.parse("A1.2"));
        assertNull(PatchVersion.parse("A1.2.x"));
        assertNull(PatchVersion.parse("B1.0.0"));
        assertNull(PatchVersion.parse("1.2.3.4"));
    }

    @Test
    void comparesNumericallyNotLexically() {
        PatchVersion v19 = PatchVersion.parse("A1.9.0");
        PatchVersion v110 = PatchVersion.parse("A1.10.0");
        assertTrue(v19.compareTo(v110) < 0);
        assertTrue(v110.compareTo(v19) > 0);
    }

    @Test
    void comparesAcrossComponents() {
        PatchVersion older = PatchVersion.parse("A1.0.0");
        PatchVersion newerPatch = PatchVersion.parse("A1.0.1");
        PatchVersion newerMinor = PatchVersion.parse("A1.1.0");
        PatchVersion newerMajor = PatchVersion.parse("A2.0.0");
        assertTrue(older.compareTo(newerPatch) < 0);
        assertTrue(newerPatch.compareTo(newerMinor) < 0);
        assertTrue(newerMinor.compareTo(newerMajor) < 0);
        assertEquals(0, older.compareTo(PatchVersion.parse("A1.0.0")));
        assertEquals(0, older.compareTo(PatchVersion.parse("1.0.0")));
    }

    @Test
    void formatsWithAPrefix() {
        assertEquals("A1.0.0", PatchVersion.parse("1.0.0").toString());
        assertEquals("A0.9.7", PatchVersion.parse("A0.9.7").toString());
    }
}
