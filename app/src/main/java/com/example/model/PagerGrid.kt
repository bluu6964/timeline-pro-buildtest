package com.example.model

/**
 * Build 238 — the selected-tools pager's page-3 grid layout rule.
 *
 * Page 3 of SelectedToolsPager219 used to render a FIXED 2 rows × 3 columns
 * grid, indexing tools[r * 3 + c] for indices 0..5. Build 237 removed the
 * Captions and Roto Brush pager tiles, shrinking page 3 from 6 tools to 5 —
 * the fixed grid then read index 5 of a 5-element list and crashed with
 * ArrayIndexOutOfBoundsException (length=5, index=5) as soon as a user
 * swiped to page 3.
 *
 * The grid is now derived from the list itself: the tools are chunked into
 * rows of [columns], and the last row is padded with nulls (rendered as
 * empty slots of the same weight) so the geometry is unchanged for any
 * tool count — no list size can index out of bounds.
 */
object PagerGrid {

    /**
     * Rows of exactly [columns] cells for the page-3 grid. Non-null cells
     * preserve the input order; nulls appear only as trailing padding in
     * the last row. An empty input yields no rows.
     */
    fun <T> rows(items: List<T>, columns: Int = 3): List<List<T?>> {
        require(columns > 0) { "columns must be positive, was $columns" }
        return items.chunked(columns).map { row ->
            if (row.size == columns) {
                val full: List<T?> = row
                full
            } else {
                val padded: List<T?> = row + List(columns - row.size) { null }
                padded
            }
        }
    }
}
