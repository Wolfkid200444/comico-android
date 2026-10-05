package moe.comico.reader

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class UpdateAgeTest {
    @Test fun updateTimesHandleBoundariesAndMissingDates() {
        val now = Instant.parse("2026-10-04T12:00:00Z")
        assertNull(updateAge("", now))
        assertNull(updateAge("null", now))
        assertEquals("Just now", updateAge(now.plusSeconds(30).toString(), now))
        assertEquals("59m ago", updateAge(now.minusSeconds(3599).toString(), now))
        assertEquals("1h ago", updateAge(now.minusSeconds(3600).toString(), now))
        assertEquals("1d ago", updateAge(now.minusSeconds(86400).toString(), now))
        assertNotNull(updateAge(now.minusSeconds(604800).toString(), now))
        assertNotEquals("Just now", updateAge(now.plusSeconds(86400).toString(), now))
    }
}
