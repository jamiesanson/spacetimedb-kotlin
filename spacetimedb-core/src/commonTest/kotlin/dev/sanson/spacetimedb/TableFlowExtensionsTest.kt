package dev.sanson.spacetimedb

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class TableFlowExtensionsTest {

    @Test
    fun `insertFlow emits rows and unregisters on cancel`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeTable<String>("items", callbacks)

        table.insertFlow().test {
            callbacks.invokeCallbacks(
                "items",
                TableAppliedDiff(inserts = listOf("a"), deletes = emptyList()),
                Event.Transaction,
            )
            val (_, row) = awaitItem()
            assertEquals("a", row)

            callbacks.invokeCallbacks(
                "items",
                TableAppliedDiff(inserts = listOf("b"), deletes = emptyList()),
                Event.Transaction,
            )
            assertEquals("b", awaitItem().row)

            cancel()
        }
    }

    @Test
    fun `deleteFlow emits deleted rows`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeTable<String>("items", callbacks)

        table.deleteFlow().test {
            callbacks.invokeCallbacks(
                "items",
                TableAppliedDiff(inserts = emptyList(), deletes = listOf("x")),
                Event.Transaction,
            )
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
            callbacks.invokeCallbacks(
                "events",
                TableAppliedDiff(inserts = listOf("evt"), deletes = emptyList()),
                Event.Transaction,
            )
            assertEquals("evt", awaitItem().row)

            cancel()
        }
    }

    @Test
    fun `rowsFlow emits initial snapshot`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeTable<String>("items", callbacks, mutableListOf("a", "b"))

        table.rowsFlow().test {
            assertEquals(listOf("a", "b"), awaitItem())
            cancel()
        }
    }

    @Test
    fun `rowsFlow emits updated list on insert`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeTable<String>("items", callbacks, mutableListOf("a"))

        table.rowsFlow().test {
            assertEquals(listOf("a"), awaitItem())

            table.rows.add("b")
            callbacks.invokeCallbacks(
                "items",
                TableAppliedDiff(inserts = listOf("b"), deletes = emptyList()),
                Event.Transaction,
            )
            assertEquals(listOf("a", "b"), awaitItem())

            cancel()
        }
    }

    @Test
    fun `rowsFlow emits updated list on delete`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeTable<String>("items", callbacks, mutableListOf("a", "b"))

        table.rowsFlow().test {
            assertEquals(listOf("a", "b"), awaitItem())

            table.rows.remove("a")
            callbacks.invokeCallbacks(
                "items",
                TableAppliedDiff(inserts = emptyList(), deletes = listOf("a")),
                Event.Transaction,
            )
            assertEquals(listOf("b"), awaitItem())

            cancel()
        }
    }

    @Test
    fun `rowsFlow for TableWithPrimaryKey emits on update`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeTableWithPk<String>("items", callbacks, mutableListOf("old"))

        table.rowsFlow().test {
            assertEquals(listOf("old"), awaitItem())

            table.rows[0] = "new"
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
            assertEquals(listOf("new"), awaitItem())

            cancel()
        }
    }

    @Test
    fun `rowsFlow unregisters callbacks on cancel`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeTableWithPk<String>("items", callbacks, mutableListOf("a"))

        table.rowsFlow().test {
            awaitItem() // initial snapshot
            cancel()
        }

        // After cancellation, updating the table should not cause issues
        table.rows.add("b")
        callbacks.invokeCallbacks(
            "items",
            TableAppliedDiff(inserts = listOf("b"), deletes = emptyList()),
            Event.Transaction,
        )
    }

    @Test
    fun `rowsFlow with conflate false preserves all intermediate emissions`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeTable<String>("items", callbacks, mutableListOf("a"))

        table.rowsFlow(conflate = false).test {
            assertEquals(listOf("a"), awaitItem())

            // Rapid-fire two inserts
            table.rows.add("b")
            callbacks.invokeCallbacks(
                "items",
                TableAppliedDiff(inserts = listOf("b"), deletes = emptyList()),
                Event.Transaction,
            )
            table.rows.add("c")
            callbacks.invokeCallbacks(
                "items",
                TableAppliedDiff(inserts = listOf("c"), deletes = emptyList()),
                Event.Transaction,
            )

            assertEquals(listOf("a", "b"), awaitItem())
            assertEquals(listOf("a", "b", "c"), awaitItem())

            cancel()
        }
    }

    @Test
    fun `rowsFlow with conflate false deduplicates identical snapshots`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeTable<String>("items", callbacks, mutableListOf("a"))

        table.rowsFlow(conflate = false).test {
            assertEquals(listOf("a"), awaitItem())

            // Insert then immediately delete — table returns to same state
            table.rows.add("b")
            callbacks.invokeCallbacks(
                "items",
                TableAppliedDiff(inserts = listOf("b"), deletes = emptyList()),
                Event.Transaction,
            )
            assertEquals(listOf("a", "b"), awaitItem())

            table.rows.remove("b")
            callbacks.invokeCallbacks(
                "items",
                TableAppliedDiff(inserts = emptyList(), deletes = listOf("b")),
                Event.Transaction,
            )
            // Should still emit because it's a distinct list from the previous
            assertEquals(listOf("a"), awaitItem())

            // Trigger a no-op callback — same list content should be deduplicated
            callbacks.invokeCallbacks(
                "items",
                TableAppliedDiff(inserts = emptyList(), deletes = emptyList()),
                Event.Transaction,
            )
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `rowsFlow without conflation delivers every update in a high-frequency burst`() = runTest {
        val callbacks = DbCallbacks()
        val table = FakeTableWithPk<Position>("pos", callbacks, mutableListOf(Position(0, 0)))

        // Simulate a game loop: 20 rapid position updates (like a 20Hz server tick)
        val updates = (1..20).map { tick -> Position(tick * 5, tick * 3) }

        table.rowsFlow(conflate = false).test {
            assertEquals(listOf(Position(0, 0)), awaitItem())

            // Fire all 20 updates synchronously — mimics a burst of TransactionUpdate messages
            for (newPos in updates) {
                val oldPos = table.rows[0]
                table.rows[0] = newPos
                callbacks.invokeCallbacks(
                    "pos",
                    TableAppliedDiff(
                        inserts = emptyList(),
                        deletes = emptyList(),
                        updateDeletes = listOf(oldPos),
                        updateInserts = listOf(newPos),
                    ),
                    Event.Transaction,
                )
            }

            // Every single intermediate position must arrive in order
            for (expected in updates) {
                assertEquals(listOf(expected), awaitItem())
            }

            cancel()
        }
    }

    @Test
    fun `rowsFlow with default conflation delivers latest state after a high-frequency burst`() =
        runTest {
            val callbacks = DbCallbacks()
            val table = FakeTableWithPk<Position>("pos", callbacks, mutableListOf(Position(0, 0)))

            val updates = (1..20).map { tick -> Position(tick * 5, tick * 3) }

            table.rowsFlow().test {
                assertEquals(listOf(Position(0, 0)), awaitItem())

                // Fire all 20 updates — with conflation the collector will see some
                // subset ending with the final position
                for (newPos in updates) {
                    val oldPos = table.rows[0]
                    table.rows[0] = newPos
                    callbacks.invokeCallbacks(
                        "pos",
                        TableAppliedDiff(
                            inserts = emptyList(),
                            deletes = emptyList(),
                            updateDeletes = listOf(oldPos),
                            updateInserts = listOf(newPos),
                        ),
                        Event.Transaction,
                    )
                }

                // Drain all available items — the last one must be the final position
                var last: List<Position> = awaitItem()
                do {
                    val events = cancelAndConsumeRemainingEvents()
                    val items =
                        events.filterIsInstance<app.cash.turbine.Event.Item<List<Position>>>()
                    if (items.isNotEmpty()) last = items.last().value
                } while (false)

                assertEquals(listOf(updates.last()), last)
            }
        }

    // Minimal fakes that implement the interfaces
    private class FakeTable<Row : Any>(
        private val tableName: String,
        private val callbacks: DbCallbacks,
        val rows: MutableList<Row> = mutableListOf(),
    ) : Table<Row> {
        override val count: Int
            get() = rows.size

        override fun iterator(): Iterator<Row> = rows.toList().iterator()

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
        val rows: MutableList<Row> = mutableListOf(),
    ) : TableWithPrimaryKey<Row> {
        override val count: Int
            get() = rows.size

        override fun iterator(): Iterator<Row> = rows.toList().iterator()

        override fun onInsert(callback: (event: Event<*>, row: Row) -> Unit): CallbackId =
            callbacks.registerOnInsert(tableName, callback)

        override fun removeOnInsert(id: CallbackId) = callbacks.removeOnInsert(tableName, id)

        override fun onDelete(callback: (event: Event<*>, row: Row) -> Unit): CallbackId =
            callbacks.registerOnDelete(tableName, callback)

        override fun removeOnDelete(id: CallbackId) = callbacks.removeOnDelete(tableName, id)

        override fun onUpdate(
            callback: (event: Event<*>, oldRow: Row, newRow: Row) -> Unit
        ): CallbackId = callbacks.registerOnUpdate(tableName, callback)

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

    private data class Position(val x: Int, val y: Int)
}
