package dev.sanson.spacetimedb

/**
 * Client-side replica of subscribed table rows.
 *
 * Manages a set of named [TableCache] instances, one per table. The cache is populated
 * by the connection's protocol handler as subscription data and transaction updates arrive.
 *
 * This class is used by generated code and SDK internals; most users interact with
 * tables through the generated [Table] implementations instead.
 */
public class ClientCache {
    private val tables = mutableMapOf<String, TableCache<*>>()

    /**
     * Returns the [TableCache] for [tableName], creating one if it doesn't already exist.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <Row : Any> getOrCreateTable(tableName: String): TableCache<Row> {
        return tables.getOrPut(tableName) { TableCache<Row>() } as TableCache<Row>
    }

    /**
     * Returns the [TableCache] for [tableName], or `null` if it hasn't been created yet.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <Row : Any> getTable(tableName: String): TableCache<Row>? {
        return tables[tableName] as? TableCache<Row>
    }

    /** Names of all tables currently in the cache. */
    public val tableNames: Set<String> get() = tables.keys

    /** Remove all rows from all tables. */
    public fun clear() {
        tables.values.forEach { it.clear() }
    }
}

/**
 * Cache for a single table's rows, keyed by their BSATN-serialized bytes.
 *
 * Supports reference counting so that overlapping subscriptions sharing the same row
 * only trigger insert/delete events at the boundaries (first subscription adds, last removes).
 *
 * @param Row the deserialized row type
 */
public class TableCache<Row : Any> : Iterable<Row> {
    private val entries = LinkedHashMap<RowKey, RowEntry<Row>>()
    private val uniqueIndexes = mutableListOf<UniqueIndex<Row, *>>()

    /** Number of distinct rows currently cached. */
    public val count: Int get() = entries.size

    override fun iterator(): Iterator<Row> =
        entries.values.asSequence().map { it.row }.iterator()

    /**
     * Register a [UniqueIndex] to be maintained automatically on insert/delete.
     *
     * The index is immediately populated with all currently cached rows.
     * Typically called once during table setup by generated code.
     */
    public fun <Col : Any> registerUniqueIndex(index: UniqueIndex<Row, Col>): UniqueIndex<Row, Col> {
        for (entry in entries.values) {
            index.add(entry.row)
        }
        uniqueIndexes.add(index)
        return index
    }

    /**
     * Insert a row into the cache.
     *
     * If the row (identified by [rowBytes]) is already present, its reference count is
     * incremented and `null` is returned. If it's new, the [row] is stored and returned
     * to signal that insert callbacks should fire.
     *
     * @param rowBytes BSATN-serialized row bytes (used as the cache key)
     * @param row the deserialized row object
     * @return the [row] if it was newly inserted, or `null` if only the ref count was bumped
     */
    public fun insert(rowBytes: ByteArray, row: Row): Row? {
        val key = RowKey(rowBytes)
        val existing = entries[key]
        if (existing != null) {
            existing.refCount++
            return null
        }
        entries[key] = RowEntry(row, refCount = 1)
        for (index in uniqueIndexes) {
            index.add(row)
        }
        return row
    }

    /**
     * Delete a row from the cache.
     *
     * Decrements the reference count for the row identified by [rowBytes]. If the count
     * reaches zero, the row is removed and returned to signal that delete callbacks should
     * fire. Otherwise returns `null`.
     *
     * @param rowBytes BSATN-serialized row bytes (used as the cache key)
     * @return the removed row if it was fully evicted, or `null` if only the ref count was decremented
     */
    public fun delete(rowBytes: ByteArray): Row? {
        val key = RowKey(rowBytes)
        val entry = entries[key] ?: return null
        entry.refCount--
        if (entry.refCount <= 0) {
            entries.remove(key)
            for (index in uniqueIndexes) {
                index.remove(entry.row)
            }
            return entry.row
        }
        return null
    }

    /** Remove all entries and clear all registered indexes. */
    public fun clear() {
        entries.clear()
        for (index in uniqueIndexes) {
            index.clear()
        }
    }

    /**
     * Apply a [TableUpdate] to this cache, returning the effective diff.
     *
     * Processes inserts first (so a row appearing in both inserts and deletes
     * of the same update cancels out via ref counting), then deletes.
     *
     * @return a [TableAppliedDiff] containing rows that were actually added or removed
     */
    public fun applyDiff(update: TableUpdate<Row>): TableAppliedDiff<Row> {
        if (update.isEmpty) return TableAppliedDiff(emptyList(), emptyList())

        val actualInserts = mutableListOf<Row>()
        val actualDeletes = mutableListOf<Row>()

        for (ins in update.inserts) {
            val inserted = insert(ins.bsatn, ins.row)
            if (inserted != null) {
                actualInserts.add(inserted)
            }
        }

        for (del in update.deletes) {
            val deleted = delete(del.bsatn)
            if (deleted != null) {
                actualDeletes.add(deleted)
                // If this row was also just inserted in the same update,
                // remove it from inserts (net no-op for callbacks).
                actualInserts.remove(deleted)
            }
        }

        return TableAppliedDiff(inserts = actualInserts, deletes = actualDeletes)
    }
}

/**
 * A cached row with its subscription reference count.
 */
internal class RowEntry<Row>(
    val row: Row,
    var refCount: Int,
)

/**
 * ByteArray wrapper with content-based [equals] and [hashCode], used as a HashMap key.
 *
 * Necessary because [ByteArray] uses reference equality by default.
 */
internal class RowKey(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RowKey) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()
}
