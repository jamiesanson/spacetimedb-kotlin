package dev.sanson.spacetimedb

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TableFlowExtensionsTest {

    @Test
    fun `insertFlow emits rows and unregisters on cancel`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeTable<String>("items", callbacks)

        table.insertFlow().test {
            callbacks.invokeCallbacks("items", TableAppliedDiff(inserts = listOf("a"), deletes = emptyList()), Event.Transaction)
            val (_, row) = awaitItem()
            assertEquals("a", row)

            callbacks.invokeCallbacks("items", TableAppliedDiff(inserts = listOf("b"), deletes = emptyList()), Event.Transaction)
            assertEquals("b", awaitItem().row)

            cancel()
        }
    }

    @Test
    fun `deleteFlow emits deleted rows`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeTable<String>("items", callbacks)

        table.deleteFlow().test {
            callbacks.invokeCallbacks("items", TableAppliedDiff(inserts = emptyList(), deletes = listOf("x")), Event.Transaction)
            assertEquals("x", awaitItem().row)

            cancel()
        }
    }

    @Test
    fun `updateFlow emits old and new rows`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeTableWithPk<String>("items", callbacks)

        table.updateFlow().test {
            callbacks.invokeCallbacks(
                "items",
                TableAppliedDiff(
                    inserts = emptyList(),
                    deletes = emptyList(),
                    updateDeletes = listOf("old"),
                    updateInserts = listOf("new"),
                ),
                Event.Transaction,
            )

            val (_, oldRow, newRow) = awaitItem()
            assertEquals("old", oldRow)
            assertEquals("new", newRow)

            cancel()
        }
    }

    @Test
    fun `eventFlow emits event rows`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeEventTable<String>("events", callbacks)

        table.eventFlow().test {
            callbacks.invokeCallbacks("events", TableAppliedDiff(inserts = listOf("evt"), deletes = emptyList()), Event.Transaction)
            assertEquals("evt", awaitItem().row)

            cancel()
        }
    }

    // Minimal fakes that implement the interfaces
    private class FakeTable<Row : Any>(
        private val tableName: String,
        private val callbacks: DbCallbacks,
    ) : Table<Row> {
        override val count: Int get() = 0
        override fun iterator(): Iterator<Row> = emptyList<Row>().iterator()
        override fun onInsert(callback: (event: Event<*>, row: Row) -> Unit): CallbackId =
            callbacks.registerOnInsert(tableName, callback)
        override fun removeOnInsert(id: CallbackId) = callbacks.removeOnInsert(tableName, id)
        override fun onDelete(callback: (event: Event<*>, row: Row) -> Unit): CallbackId =
            callbacks.registerOnDelete(tableName, callback)
        override fun removeOnDelete(id: CallbackId) = callbacks.removeOnDelete(tableName, id)
    }

    private class FakeTableWithPk<Row : Any>(
        private val tableName: String,
        private val callbacks: DbCallbacks,
    ) : TableWithPrimaryKey<Row> {
        override val count: Int get() = 0
        override fun iterator(): Iterator<Row> = emptyList<Row>().iterator()
        override fun onInsert(callback: (event: Event<*>, row: Row) -> Unit): CallbackId =
            callbacks.registerOnInsert(tableName, callback)
        override fun removeOnInsert(id: CallbackId) = callbacks.removeOnInsert(tableName, id)
        override fun onDelete(callback: (event: Event<*>, row: Row) -> Unit): CallbackId =
            callbacks.registerOnDelete(tableName, callback)
        override fun removeOnDelete(id: CallbackId) = callbacks.removeOnDelete(tableName, id)
        override fun onUpdate(callback: (event: Event<*>, oldRow: Row, newRow: Row) -> Unit): CallbackId =
            callbacks.registerOnUpdate(tableName, callback)
        override fun removeOnUpdate(id: CallbackId) = callbacks.removeOnUpdate(tableName, id)
    }

    private class FakeEventTable<Row : Any>(
        private val tableName: String,
        private val callbacks: DbCallbacks,
    ) : EventTable<Row> {
        override fun onEvent(callback: (event: Event<*>, row: Row) -> Unit): CallbackId =
            callbacks.registerOnInsert(tableName, callback)
        override fun removeOnEvent(id: CallbackId) = callbacks.removeOnInsert(tableName, id)
    }
}
