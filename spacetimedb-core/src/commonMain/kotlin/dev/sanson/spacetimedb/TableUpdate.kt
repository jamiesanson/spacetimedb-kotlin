package dev.sanson.spacetimedb

/**
 * A row paired with its BSATN-serialized bytes.
 *
 * Used in [TableUpdate] to carry both the deserialized row and the raw bytes
 * needed as the [TableCache] key.
 */
public class RowWithBsatn<Row : Any>(
    /** BSATN-serialized bytes of this row (used as the cache key). */
    public val bsatn: ByteArray,
    /** The deserialized row. */
    public val row: Row,
)

/**
 * A batch of row inserts and deletes for a single table within one transaction.
 *
 * Constructed by the protocol handler after deserializing wire-format messages.
 * Passed to [TableCache.applyDiff] to update the client cache.
 */
public class TableUpdate<Row : Any>(
    public val inserts: List<RowWithBsatn<Row>>,
    public val deletes: List<RowWithBsatn<Row>>,
) {
    public val isEmpty: Boolean get() = inserts.isEmpty() && deletes.isEmpty()
}

/**
 * The result of applying a [TableUpdate] to a [TableCache].
 *
 * Contains the rows that were actually inserted or deleted from the cache
 * (after ref-count deduplication). Call [withUpdatesByPk] to further refine
 * by matching delete+insert pairs that share a primary key into "update" pairs.
 *
 * @param inserts rows that were newly added to the cache (ref count 0→1)
 * @param deletes rows that were fully evicted from the cache (ref count →0)
 * @param updateDeletes the old rows of PK-matched updates (parallel with [updateInserts])
 * @param updateInserts the new rows of PK-matched updates (parallel with [updateDeletes])
 */
public class TableAppliedDiff<Row : Any>(
    public val inserts: List<Row>,
    public val deletes: List<Row>,
    public val updateDeletes: List<Row> = emptyList(),
    public val updateInserts: List<Row> = emptyList(),
) {
    public val isEmpty: Boolean
        get() = inserts.isEmpty() && deletes.isEmpty() && updateDeletes.isEmpty()

    /**
     * Match delete+insert pairs that share the same primary key, reclassifying
     * them as updates.
     *
     * Returns a new [TableAppliedDiff] where matched pairs have been moved from
     * [inserts]/[deletes] into [updateInserts]/[updateDeletes].
     */
    public fun <PK> withUpdatesByPk(getPk: (Row) -> PK): TableAppliedDiff<Row> {
        if (inserts.isEmpty() || deletes.isEmpty()) return this

        // Index deletes by PK for matching
        val deletesByPk = LinkedHashMap<PK, MutableList<Row>>()
        for (del in deletes) {
            deletesByPk.getOrPut(getPk(del)) { mutableListOf() }.add(del)
        }

        val pureInserts = mutableListOf<Row>()
        val matchedDeletes = mutableListOf<Row>()
        val matchedInserts = mutableListOf<Row>()

        for (ins in inserts) {
            val pk = getPk(ins)
            val matchingDeletes = deletesByPk[pk]
            if (matchingDeletes != null && matchingDeletes.isNotEmpty()) {
                matchedDeletes.add(matchingDeletes.removeFirst())
                matchedInserts.add(ins)
            } else {
                pureInserts.add(ins)
            }
        }

        val pureDeletes = deletesByPk.values.flatMap { it }

        return TableAppliedDiff(
            inserts = pureInserts,
            deletes = pureDeletes,
            updateDeletes = updateDeletes + matchedDeletes,
            updateInserts = updateInserts + matchedInserts,
        )
    }
}
