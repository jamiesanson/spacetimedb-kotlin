package dev.sanson.spacetimedb.protocol

import dev.sanson.spacetimedb.ConnectionId
import dev.sanson.spacetimedb.Identity
import dev.sanson.spacetimedb.TimeDuration
import dev.sanson.spacetimedb.Timestamp
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// -- BsatnRowList --

/**
 * Describes how to slice [BsatnRowList.rowsData] into individual rows.
 */
@Serializable
internal sealed class RowSizeHint {
    /** All rows are exactly [size] bytes. */
    @Serializable
    @SerialName("FixedSize")
    data class FixedSize(val size: UShort) : RowSizeHint()

    /** Each entry marks the byte offset where a row starts within the row data buffer. */
    @Serializable
    @SerialName("RowOffsets")
    data class RowOffsets(val offsets: List<ULong>) : RowSizeHint()
}

/**
 * A packed list of BSATN-encoded rows with a [RowSizeHint] for slicing.
 */
@Serializable
internal data class BsatnRowList(
    val sizeHint: RowSizeHint,
    val rowsData: ByteArray,
) {
    /** Slices [rowsData] into individual row byte arrays according to [sizeHint]. */
    fun rows(): List<ByteArray> = when (val hint = sizeHint) {
        is RowSizeHint.FixedSize -> {
            val size = hint.size.toInt()
            if (size == 0) {
                emptyList()
            } else {
                (0 until rowsData.size / size).map { i ->
                    rowsData.copyOfRange(i * size, (i + 1) * size)
                }
            }
        }
        is RowSizeHint.RowOffsets -> {
            val offsets = hint.offsets
            offsets.indices.map { i ->
                val start = offsets[i].toInt()
                val end = if (i + 1 < offsets.size) offsets[i + 1].toInt() else rowsData.size
                rowsData.copyOfRange(start, end)
            }
        }
    }
}

// -- Query rows --

@Serializable
internal data class SingleTableRows(
    val table: String,
    val rows: BsatnRowList,
)

@Serializable
internal data class QueryRows(
    val tables: List<SingleTableRows>,
)

// -- Wire table update --

@Serializable(with = TableUpdateRowsSerializer::class)
internal sealed class TableUpdateRows {
    @Serializable
    data class PersistentTable(
        val inserts: BsatnRowList,
        val deletes: BsatnRowList,
    ) : TableUpdateRows()

    @Serializable
    data class EventTable(
        val events: BsatnRowList,
    ) : TableUpdateRows()
}

internal object TableUpdateRowsSerializer : KSerializer<TableUpdateRows> by TaggedSumSerializer(
    "TableUpdateRows",
    arrayOf(
        variant(TableUpdateRows.PersistentTable.serializer()),
        variant(TableUpdateRows.EventTable.serializer()),
    ),
)

@Serializable
internal data class TableUpdate(
    val tableName: String,
    val rows: List<TableUpdateRows>,
)

@Serializable
internal data class QuerySetUpdate(
    val querySetId: QuerySetId,
    val tables: List<TableUpdate>,
)

/**
 * A batch of cache updates across one or more query sets.
 *
 * Used both as a [ServerMessage] variant payload and inside [ReducerOutcome.Ok].
 */
@Serializable
internal data class TransactionUpdate(
    val querySets: List<QuerySetUpdate>,
)

// -- Reducer outcome --

@Serializable(with = ReducerOutcomeSerializer::class)
internal sealed class ReducerOutcome {
    @Serializable
    data class Ok(
        val retValue: ByteArray,
        val transactionUpdate: TransactionUpdate,
    ) : ReducerOutcome()

    @Serializable
    data object OkEmpty : ReducerOutcome()

    @Serializable
    data class Err(val error: ByteArray) : ReducerOutcome()

    @Serializable
    data class InternalError(val message: String) : ReducerOutcome()
}

internal object ReducerOutcomeSerializer : KSerializer<ReducerOutcome> by TaggedSumSerializer(
    "ReducerOutcome",
    arrayOf(
        variant(ReducerOutcome.Ok.serializer()),
        variant(ReducerOutcome.OkEmpty.serializer()),
        variant(ReducerOutcome.Err.serializer()),
        variant(ReducerOutcome.InternalError.serializer()),
    ),
)

// -- One-off query result --

@Serializable(with = QueryResultSerializer::class)
internal sealed class QueryResult {
    @Serializable
    data class Ok(val rows: QueryRows) : QueryResult()

    @Serializable
    data class Err(val error: String) : QueryResult()
}

internal object QueryResultSerializer : KSerializer<QueryResult> by TaggedSumSerializer(
    "QueryResult",
    arrayOf(
        variant(QueryResult.Ok.serializer()),
        variant(QueryResult.Err.serializer()),
    ),
)

// -- Procedure status --

@Serializable(with = ProcedureStatusSerializer::class)
internal sealed class ProcedureStatus {
    @Serializable
    data class Returned(val value: ByteArray) : ProcedureStatus()

    @Serializable
    data class InternalError(val message: String) : ProcedureStatus()
}

internal object ProcedureStatusSerializer : KSerializer<ProcedureStatus> by TaggedSumSerializer(
    "ProcedureStatus",
    arrayOf(
        variant(ProcedureStatus.Returned.serializer()),
        variant(ProcedureStatus.InternalError.serializer()),
    ),
)

// -- Server messages --

/**
 * Messages received from the SpacetimeDB server over WebSocket v2.
 *
 * Uses [ServerMessageSerializer] for explicit tag ordering matching the Rust enum.
 */
@Serializable(with = ServerMessageSerializer::class)
internal sealed class ServerMessage {
    @Serializable
    data class InitialConnection(
        val identity: Identity,
        val connectionId: ConnectionId,
        val token: String,
    ) : ServerMessage()

    @Serializable
    data class SubscribeApplied(
        val requestId: UInt,
        val querySetId: QuerySetId,
        val rows: QueryRows,
    ) : ServerMessage()

    @Serializable
    data class UnsubscribeApplied(
        val requestId: UInt,
        val querySetId: QuerySetId,
        val rows: QueryRows?,
    ) : ServerMessage()

    @Serializable
    data class SubscriptionError(
        val requestId: UInt?,
        val querySetId: QuerySetId,
        val error: String,
    ) : ServerMessage()

    /** Wraps [TransactionUpdate] to avoid name conflict with the standalone type. */
    @Serializable
    data class TransactionUpdateMsg(
        val update: TransactionUpdate,
    ) : ServerMessage()

    @Serializable
    data class OneOffQueryResult(
        val requestId: UInt,
        val result: QueryResult,
    ) : ServerMessage()

    @Serializable
    data class ReducerResult(
        val requestId: UInt,
        val timestamp: Timestamp,
        val result: ReducerOutcome,
    ) : ServerMessage()

    @Serializable
    data class ProcedureResult(
        val requestId: UInt,
        val timestamp: Timestamp,
        val totalHostExecutionDuration: TimeDuration,
        val status: ProcedureStatus,
    ) : ServerMessage()
}

internal object ServerMessageSerializer : KSerializer<ServerMessage> by TaggedSumSerializer(
    "ServerMessage",
    arrayOf(
        variant(ServerMessage.InitialConnection.serializer()),
        variant(ServerMessage.SubscribeApplied.serializer()),
        variant(ServerMessage.UnsubscribeApplied.serializer()),
        variant(ServerMessage.SubscriptionError.serializer()),
        variant(ServerMessage.TransactionUpdateMsg.serializer()),
        variant(ServerMessage.OneOffQueryResult.serializer()),
        variant(ServerMessage.ReducerResult.serializer()),
        variant(ServerMessage.ProcedureResult.serializer()),
    ),
)
