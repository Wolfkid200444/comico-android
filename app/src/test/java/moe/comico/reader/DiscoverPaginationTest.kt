package moe.comico.reader
import org.junit.Assert.*
import org.junit.Test
class DiscoverPaginationTest {
    @Test fun pageDefaultsAllowFirstLoad() {
        val page = DiscoverPage()
        assertTrue(page.hasMore)
        assertEquals(0, page.offset)
        assertFalse(page.loading)
    }
}
