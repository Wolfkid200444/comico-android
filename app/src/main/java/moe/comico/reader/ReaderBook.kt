package moe.comico.reader

data class BookPanes(val left: Float, val gap: Float, val right: Float)

// Coordinates are in the reader's own space, in dp; leave a small gutter around the fold.
fun readerBookPanes(width: Float, foldStart: Float, foldEnd: Float): BookPanes? {
    if (!width.isFinite() || !foldStart.isFinite() || !foldEnd.isFinite() ||
        width < 600f || foldStart < 0f || foldEnd < foldStart || foldEnd > width) return null
    val left = foldStart - 4f
    val right = width - foldEnd - 4f
    if (left < 240f || right < 240f) return null
    return BookPanes(left, foldEnd - foldStart + 8f, right)
}

fun useFoldableBook(enabled: Boolean, mode: ReadingMode, panes: BookPanes?) =
    enabled && mode != ReadingMode.STRIP && panes != null

fun bookSpreadPages(pageCount: Int, first: Int, direction: ReadingDirection): List<Int?> {
    val slots = listOf(first.takeIf { it in 0 until pageCount }, (first + 1).takeIf { it in 0 until pageCount })
    return if (direction == ReadingDirection.RTL) slots.reversed() else slots
}
