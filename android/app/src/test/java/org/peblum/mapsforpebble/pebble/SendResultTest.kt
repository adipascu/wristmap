package org.peblum.mapsforpebble.pebble

import org.junit.Assert.assertEquals
import org.junit.Test

class SendResultTest {
    @Test
    fun describesEveryOutcome() {
        assertEquals("ok", SendResult.Ok.toString())
        assertEquals("Pebble app not installed", SendResult.NoPebbleApp.toString())
        assertEquals("no watch connected", SendResult.NoWatch.toString())
        assertEquals("watchapp not open", SendResult.AppNotOpen.toString())
        assertEquals("companion not allowed by watchapp", SendResult.NoPermission.toString())
        assertEquals("watch rejected message", SendResult.Nacked.toString())
        assertEquals("timeout", SendResult.Timeout.toString())
        assertEquals("boom", SendResult.Failed("boom").toString())
    }
}
