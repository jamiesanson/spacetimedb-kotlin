package dev.sanson.spacetimedb

import dev.drewhamilton.poko.Poko
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * A [Flow] of inserted rows. The callback is automatically unregistered when the
 * flow collector is cancelled.
 *
 * ```kotlin
 * conn.db.player.insertFlow().collect { (event, row) ->
 *     println("Inserted: $row")
 * }
 * ```
 */
public fun <Row : Any> Table<Row>.insertFlow(): Flow<RowEvent<Row>> = callbackFlow {
    val id = onInsert { event, row -> trySend(RowEvent(event, row)) }
    awaitClose { removeOnInsert(id) }
}

/**
 * A [Flow] of deleted rows. The callback is automatically unregistered when the
 * flow collector is cancelled.
 */
public fun <Row : Any> Table<Row>.deleteFlow(): Flow<RowEvent<Row>> = callbackFlow {
    val id = onDelete { event, row -> trySend(RowEvent(event, row)) }
    awaitClose { removeOnDelete(id) }
}

/**
 * A [Flow] of row updates (old → new). The callback is automatically unregistered
 * when the flow collector is cancelled.
 *
 * ```kotlin
 * conn.db.player.updateFlow().collect { (event, oldRow, newRow) ->
 *     println("${oldRow.name} score: ${oldRow.score} → ${newRow.score}")
 * }
 * ```
 */
public fun <Row : Any> TableWithPrimaryKey<Row>.updateFlow(): Flow<RowUpdateEvent<Row>> = callbackFlow {
    val id = onUpdate { event, oldRow, newRow -> trySend(RowUpdateEvent(event, oldRow, newRow)) }
    awaitClose { removeOnUpdate(id) }
}

/**
 * A [Flow] of event table rows. The callback is automatically unregistered when the
 * flow collector is cancelled.
 */
public fun <Row : Any> EventTable<Row>.eventFlow(): Flow<RowEvent<Row>> = callbackFlow {
    val id = onEvent { event, row -> trySend(RowEvent(event, row)) }
    awaitClose { removeOnEvent(id) }
}

/**
 * A [Flow] of the current list of rows in this table.
 *
 * The flow emits the initial snapshot of all cached rows, then re-emits the full
 * list whenever rows are inserted or deleted. Intermediate snapshots during rapid
 * changes (e.g. initial subscription apply) are automatically conflated so collectors
 * always see the latest state without processing every intermediate list.
 *
 * For tables with a primary key, prefer the [TableWithPrimaryKey] overload which
 * also tracks row updates.
 *
 * ```kotlin
 * // One line per table — replaces manual onInsert/onDelete/onApplied boilerplate
 * val players: Flow<List<Player>> = conn.db.player.rowsFlow()
 *
 * // Convert to StateFlow if needed:
 * val players: StateFlow<List<Player>> = conn.db.player.rowsFlow()
 *     .stateIn(scope, SharingStarted.Eagerly, emptyList())
 * ```
 */
public fun <Row : Any> Table<Row>.rowsFlow(): Flow<List<Row>> = callbackFlow {
    send(toList())
    val insertId = onInsert { _, _ -> trySend(toList()) }
    val deleteId = onDelete { _, _ -> trySend(toList()) }
    awaitClose {
        removeOnInsert(insertId)
        removeOnDelete(deleteId)
    }
}.conflate()

/**
 * A [Flow] of the current list of rows in this table with primary key.
 *
 * Behaves identically to [Table.rowsFlow] but also re-emits when rows are
 * updated (a PK-matched delete + insert within the same transaction).
 *
 * ```kotlin
 * val messages: Flow<List<Message>> = conn.db.message.rowsFlow()
 *
 * // Compose naturally with Flow operators:
 * val sorted: Flow<List<Message>> = conn.db.message.rowsFlow()
 *     .map { it.sortedBy { m -> m.createdAt } }
 * ```
 */
public fun <Row : Any> TableWithPrimaryKey<Row>.rowsFlow(): Flow<List<Row>> = callbackFlow {
    send(toList())
    val insertId = onInsert { _, _ -> trySend(toList()) }
    val deleteId = onDelete { _, _ -> trySend(toList()) }
    val updateId = onUpdate { _, _, _ -> trySend(toList()) }
    awaitClose {
        removeOnInsert(insertId)
        removeOnDelete(deleteId)
        removeOnUpdate(updateId)
    }
}.conflate()

/**
 * An insert or delete event paired with its row.
 */
@Poko
public class RowEvent<Row : Any>(
    public val event: Event<*>,
    public val row: Row,
) {
    public operator fun component1(): Event<*> = event
    public operator fun component2(): Row = row
}

/**
 * An update event with the old and new row values.
 */
@Poko
public class RowUpdateEvent<Row : Any>(
    public val event: Event<*>,
    public val oldRow: Row,
    public val newRow: Row,
) {
    public operator fun component1(): Event<*> = event
    public operator fun component2(): Row = oldRow
    public operator fun component3(): Row = newRow
}
