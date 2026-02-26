package dev.sanson.spacetimedb

import dev.drewhamilton.poko.Poko
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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
