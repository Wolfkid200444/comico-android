package moe.comico.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelProgressTest {
    @Test fun matchesWebsiteExample() {
        assertEquals(LevelProgress(4, 414, 864), levelProgress(2234))
    }
    @Test fun handlesLevelBoundariesAndMaximum() {
        assertEquals(LevelProgress(1, 0, 500), levelProgress(-1))
        assertEquals(LevelProgress(2, 0, 600), levelProgress(500))
        assertEquals(LevelProgress(100, 1, 1), levelProgress(Long.MAX_VALUE))
    }
}
