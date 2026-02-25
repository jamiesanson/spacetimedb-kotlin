package dev.sanson.spacetimedb

/**
 * An index over a unique column of a table, enabling efficient point lookups.
 *
 * Maintained automatically by [TableCache] — rows are indexed on insert and
 * de-indexed on delete. Generated table code creates one [UniqueIndex] per
 * unique or primary-key column.
 *
 * @param Row the deserialized row type
 * @param Col the column type (must have correct [equals]/[hashCode])
 * @param getColumn extracts the indexed column value from a row
 */
public class UniqueIndex<Row : Any, Col : Any>(
    private val getColumn: (Row) -> Col,
) {
    private val index = LinkedHashMap<Col, Row>()

    /**
     * Look up a row by its unique column value.
     *
     * @return the row matching [key], or `null` if no such row is cached
     */
    public fun find(key: Col): Row? = index[key]

    /**
     * Add a row to the index.
     *
     * @throws IllegalStateException if a row with the same column value already exists
     */
    internal fun add(row: Row) {
        val col = getColumn(row)
        val prev = index.put(col, row)
        if (prev != null) {
            // Restore previous entry before throwing
            index[col] = prev
            error("Duplicate unique index key: $col")
        }
    }

    /**
     * Remove a row from the index.
     *
     * @throws IllegalStateException if no row with the column value exists
     */
    internal fun remove(row: Row) {
        val col = getColumn(row)
        val removed = index.remove(col)
        check(removed != null) { "Removing non-present row from unique index: $col" }
    }

    /** Number of entries in this index. */
    public val size: Int get() = index.size

    /** Remove all entries. */
    internal fun clear() {
        index.clear()
    }
}
