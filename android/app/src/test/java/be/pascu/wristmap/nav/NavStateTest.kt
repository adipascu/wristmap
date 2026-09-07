package be.pascu.wristmap.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStateTest {
    @Test
    fun instructionPresenceAndEquality() {
        val left = NavState(active = true, maneuver = Maneuver.TURN_LEFT, street = "Rue Neuve", instruction = "Turn left onto Rue Neuve")
        assertTrue(left.hasInstruction)
        assertFalse(NavState.STOPPED.hasInstruction)
        assertTrue(NavState(active = true, street = "Rue Neuve").hasInstruction)
        assertTrue(left.sameInstruction(left.copy(distance = "20 m")))
        assertFalse(left.sameInstruction(left.copy(maneuver = Maneuver.TURN_RIGHT)))
        assertFalse(left.sameInstruction(left.copy(street = "Rue Vieille")))
        assertFalse(left.sameInstruction(left.copy(instruction = "Turn left now")))
        assertFalse(NavState.STOPPED.active)
    }
}
