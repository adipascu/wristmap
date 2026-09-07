package be.pascu.mapsforpebble.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NamesTest {
    @Test
    fun normalisesAccentsAndPunctuation() {
        assertEquals("rue de la regence", Names.normalize("Rue de la Régence"))
        assertEquals("rue de la loi wetstraat", Names.normalize("Rue de la Loi - Wetstraat"))
    }

    @Test
    fun matchesBilingualOsmNames() {
        val candidates = listOf(Names.normalize("Rue de la Loi - Wetstraat"))
        assertTrue(Names.matches(candidates, Names.normalize("Rue de la Loi")))
        assertTrue(Names.matches(candidates, Names.normalize("Wetstraat")))
        assertFalse(Names.matches(candidates, Names.normalize("Rue Neuve")))
    }
}
