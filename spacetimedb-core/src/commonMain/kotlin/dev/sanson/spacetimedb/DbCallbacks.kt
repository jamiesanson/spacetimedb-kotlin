package dev.sanson.spacetimedb

/**
 * Registry for table row-change callbacks.
 *
 * Manages per-table `on_insert`, `on_delete`, and `on_update` callbacks keyed by [CallbackId].
 * After a [TableAppliedDiff] is computed, call [invokeCallbacks] to fire all matching
 * callbacks for the affected rows.
 *
 * Used by SDK internals and generated code; most users register callbacks through the
 * generated [Table] implementations.
 */
public class DbCallbacks {
    private val tableCallbacks = mutableMapOf<String, TableCallbacks>()
    private var nextId = 0L

    /** Generate a new unique [CallbackId]. */
    internal fun nextCallbackId(): CallbackId = CallbackId(nextId++)

    // -- Registration --

    /**
     * Register an insert callback for [tableName].
     *
     * The [callback] will be invoked with each newly inserted row when
     * [invokeCallbacks] is called after a cache update.
     */
    public fun <Row : Any> registerOnInsert(
        tableName: String,
        callback: (event: Event<*>, row: Row) -> Unit,
    ): CallbackId {
        val id = nextCallbackId()
        getOrCreateTableCallbacks(tableName).onInsert[id] = eraseRowCallback(callback)
        return id
    }

    /**
     * Register a delete callback for [tableName].
     */
    public fun <Row : Any> registerOnDelete(
        tableName: String,
        callback: (event: Event<*>, row: Row) -> Unit,
    ): CallbackId {
        val id = nextCallbackId()
        getOrCreateTableCallbacks(tableName).onDelete[id] = eraseRowCallback(callback)
        return id
    }

    /**
     * Register an update callback for [tableName].
     *
     * The [callback] receives the old row and the new row for each PK-matched update.
     */
    public fun <Row : Any> registerOnUpdate(
        tableName: String,
        callback: (event: Event<*>, oldRow: Row, newRow: Row) -> Unit,
    ): CallbackId {
        val id = nextCallbackId()
        getOrCreateTableCallbacks(tableName).onUpdate[id] = eraseUpdateCallback(callback)
        return id
    }

    // -- Removal --

    /** Remove a previously registered insert callback. */
    public fun removeOnInsert(tableName: String, id: CallbackId) {
        tableCallbacks[tableName]?.onInsert?.remove(id)
    }

    /** Remove a previously registered delete callback. */
    public fun removeOnDelete(tableName: String, id: CallbackId) {
        tableCallbacks[tableName]?.onDelete?.remove(id)
    }

    /** Remove a previously registered update callback. */
    public fun removeOnUpdate(tableName: String, id: CallbackId) {
        tableCallbacks[tableName]?.onUpdate?.remove(id)
    }

    // -- Invocation --

    /**
     * Invoke all registered callbacks for the given [diff] on [tableName].
     *
     * Fires insert callbacks for [TableAppliedDiff.inserts], delete callbacks
     * for [TableAppliedDiff.deletes], and update callbacks for
     * [TableAppliedDiff.updateDeletes]/[TableAppliedDiff.updateInserts] pairs.
     */
    public fun <Row : Any> invokeCallbacks(
        tableName: String,
        diff: TableAppliedDiff<Row>,
        event: Event<*>,
    ) {
        if (diff.isEmpty) return

        val callbacks = tableCallbacks[tableName] ?: return

        for (row in diff.inserts) {
            for (cb in callbacks.onInsert.values) {
                cb(event, row)
            }
        }

        for (row in diff.deletes) {
            for (cb in callbacks.onDelete.values) {
                cb(event, row)
            }
        }

        for (i in diff.updateDeletes.indices) {
            for (cb in callbacks.onUpdate.values) {
                cb(event, diff.updateDeletes[i], diff.updateInserts[i])
            }
        }
    }

    private fun getOrCreateTableCallbacks(tableName: String): TableCallbacks {
        return tableCallbacks.getOrPut(tableName) { TableCallbacks() }
    }
}

// -- Internal types --

internal typealias RowCallback = (Event<*>, Any) -> Unit
internal typealias UpdateCallback = (Event<*>, Any, Any) -> Unit

internal class TableCallbacks {
    val onInsert = LinkedHashMap<CallbackId, RowCallback>()
    val onDelete = LinkedHashMap<CallbackId, RowCallback>()
    val onUpdate = LinkedHashMap<CallbackId, UpdateCallback>()
}

@Suppress("UNCHECKED_CAST")
private fun <Row : Any> eraseRowCallback(
    callback: (Event<*>, Row) -> Unit,
): RowCallback = { event, row -> callback(event, row as Row) }

@Suppress("UNCHECKED_CAST")
private fun <Row : Any> eraseUpdateCallback(
    callback: (Event<*>, Row, Row) -> Unit,
): UpdateCallback = { event, oldRow, newRow -> callback(event, oldRow as Row, newRow as Row) }
