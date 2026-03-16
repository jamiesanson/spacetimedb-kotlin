package dev.sanson.spacetimedb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DbCallbacksTest {

    data class User(val id: Int, val name: String)

    // -- Registration and invocation --

    @Test
    fun `onInsert callback fires for inserted rows`() {
        val callbacks = DbCallbacks()
        val received = mutableListOf<User>()

        callbacks.registerOnInsert<User>("users") { _, row -> received.add(row) }

        val diff =
            TableAppliedDiff(
                inserts = listOf(User(1, "alice"), User(2, "bob")),
                deletes = emptyList(),
            )
        callbacks.invokeCallbacks("users", diff, Event.SubscribeApplied)

        assertEquals(listOf(User(1, "alice"), User(2, "bob")), received)
    }

    @Test
    fun `onDelete callback fires for deleted rows`() {
        val callbacks = DbCallbacks()
        val received = mutableListOf<User>()

        callbacks.registerOnDelete<User>("users") { _, row -> received.add(row) }

        val diff = TableAppliedDiff(inserts = emptyList(), deletes = listOf(User(1, "alice")))
        callbacks.invokeCallbacks("users", diff, Event.SubscribeApplied)

        assertEquals(listOf(User(1, "alice")), received)
    }

    @Test
    fun `onUpdate callback fires for update pairs`() {
        val callbacks = DbCallbacks()
        val received = mutableListOf<Pair<User, User>>()

        callbacks.registerOnUpdate<User>("users") { _, old, new -> received.add(old to new) }

        val diff =
            TableAppliedDiff(
                inserts = emptyList(),
                deletes = emptyList(),
                updateDeletes = listOf(User(1, "alice")),
                updateInserts = listOf(User(1, "alicia")),
            )
        callbacks.invokeCallbacks("users", diff, Event.SubscribeApplied)

        assertEquals(listOf(User(1, "alice") to User(1, "alicia")), received)
    }

    @Test
    fun `callback receives correct event`() {
        val callbacks = DbCallbacks()
        val events = mutableListOf<Event<*>>()

        callbacks.registerOnInsert<User>("users") { event, _ -> events.add(event) }

        val diff = TableAppliedDiff(inserts = listOf(User(1, "alice")), deletes = emptyList())
        callbacks.invokeCallbacks("users", diff, Event.Transaction)

        assertEquals(1, events.size)
        assertTrue(events[0] is Event.Transaction)
    }

    // -- Multiple callbacks --

    @Test
    fun `multiple insert callbacks all fire`() {
        val callbacks = DbCallbacks()
        val received1 = mutableListOf<User>()
        val received2 = mutableListOf<User>()

        callbacks.registerOnInsert<User>("users") { _, row -> received1.add(row) }
        callbacks.registerOnInsert<User>("users") { _, row -> received2.add(row) }

        val diff = TableAppliedDiff(inserts = listOf(User(1, "alice")), deletes = emptyList())
        callbacks.invokeCallbacks("users", diff, Event.SubscribeApplied)

        assertEquals(1, received1.size)
        assertEquals(1, received2.size)
    }

    // -- Removal --

    @Test
    fun `removeOnInsert prevents callback from firing`() {
        val callbacks = DbCallbacks()
        val received = mutableListOf<User>()

        val id = callbacks.registerOnInsert<User>("users") { _, row -> received.add(row) }
        callbacks.removeOnInsert("users", id)

        val diff = TableAppliedDiff(inserts = listOf(User(1, "alice")), deletes = emptyList())
        callbacks.invokeCallbacks("users", diff, Event.SubscribeApplied)

        assertTrue(received.isEmpty())
    }

    @Test
    fun `removeOnDelete prevents callback from firing`() {
        val callbacks = DbCallbacks()
        val received = mutableListOf<User>()

        val id = callbacks.registerOnDelete<User>("users") { _, row -> received.add(row) }
        callbacks.removeOnDelete("users", id)

        val diff = TableAppliedDiff(inserts = emptyList(), deletes = listOf(User(1, "alice")))
        callbacks.invokeCallbacks("users", diff, Event.SubscribeApplied)

        assertTrue(received.isEmpty())
    }

    @Test
    fun `removeOnUpdate prevents callback from firing`() {
        val callbacks = DbCallbacks()
        val received = mutableListOf<Pair<User, User>>()

        val id =
            callbacks.registerOnUpdate<User>("users") { _, old, new -> received.add(old to new) }
        callbacks.removeOnUpdate("users", id)

        val diff =
            TableAppliedDiff(
                inserts = emptyList(),
                deletes = emptyList(),
                updateDeletes = listOf(User(1, "alice")),
                updateInserts = listOf(User(1, "bob")),
            )
        callbacks.invokeCallbacks("users", diff, Event.SubscribeApplied)

        assertTrue(received.isEmpty())
    }

    @Test
    fun `removing one callback does not affect others`() {
        val callbacks = DbCallbacks()
        val received1 = mutableListOf<User>()
        val received2 = mutableListOf<User>()

        val id1 = callbacks.registerOnInsert<User>("users") { _, row -> received1.add(row) }
        callbacks.registerOnInsert<User>("users") { _, row -> received2.add(row) }
        callbacks.removeOnInsert("users", id1)

        val diff = TableAppliedDiff(inserts = listOf(User(1, "alice")), deletes = emptyList())
        callbacks.invokeCallbacks("users", diff, Event.SubscribeApplied)

        assertTrue(received1.isEmpty())
        assertEquals(1, received2.size)
    }

    // -- Table isolation --

    @Test
    fun `callbacks for different tables are independent`() {
        val callbacks = DbCallbacks()
        val userInserts = mutableListOf<User>()
        val itemInserts = mutableListOf<String>()

        callbacks.registerOnInsert<User>("users") { _, row -> userInserts.add(row) }
        callbacks.registerOnInsert<String>("items") { _, row -> itemInserts.add(row) }

        callbacks.invokeCallbacks(
            "users",
            TableAppliedDiff(inserts = listOf(User(1, "alice")), deletes = emptyList()),
            Event.SubscribeApplied,
        )

        assertEquals(1, userInserts.size)
        assertTrue(itemInserts.isEmpty())
    }

    // -- Edge cases --

    @Test
    fun `invokeCallbacks with empty diff is a no-op`() {
        val callbacks = DbCallbacks()
        var called = false

        callbacks.registerOnInsert<User>("users") { _, _ -> called = true }

        val diff = TableAppliedDiff<User>(emptyList(), emptyList())
        callbacks.invokeCallbacks("users", diff, Event.SubscribeApplied)

        assertTrue(!called)
    }

    @Test
    fun `invokeCallbacks for unregistered table is a no-op`() {
        val callbacks = DbCallbacks()
        val diff = TableAppliedDiff(inserts = listOf(User(1, "alice")), deletes = emptyList())
        // Should not throw
        callbacks.invokeCallbacks("unknown", diff, Event.SubscribeApplied)
    }

    @Test
    fun `removing callback for non-existent table is a no-op`() {
        val callbacks = DbCallbacks()
        // Should not throw
        callbacks.removeOnInsert("unknown", CallbackId(999))
    }

    // -- Full pipeline: cache + callbacks --

    @Test
    fun `end-to-end cache update with callback dispatch`() {
        val cache = ClientCache()
        val callbacks = DbCallbacks()

        val table = cache.getOrCreateTable<User>("users")
        table.insert(byteArrayOf(1, 0), User(1, "alice"))

        val insertedRows = mutableListOf<User>()
        val deletedRows = mutableListOf<User>()
        val updatedRows = mutableListOf<Pair<User, User>>()

        callbacks.registerOnInsert<User>("users") { _, row -> insertedRows.add(row) }
        callbacks.registerOnDelete<User>("users") { _, row -> deletedRows.add(row) }
        callbacks.registerOnUpdate<User>("users") { _, old, new -> updatedRows.add(old to new) }

        // Simulate a transaction update: delete alice and insert alicia (same PK = update)
        val update =
            TableUpdate(
                inserts = listOf(RowWithBsatn(byteArrayOf(1, 1), User(1, "alicia"))),
                deletes = listOf(RowWithBsatn(byteArrayOf(1, 0), User(1, "alice"))),
            )

        val diff = table.applyDiff(update)
        val refined = diff.withUpdatesByPk { it.id }
        callbacks.invokeCallbacks("users", refined, Event.Transaction)

        assertTrue(insertedRows.isEmpty())
        assertTrue(deletedRows.isEmpty())
        assertEquals(1, updatedRows.size)
        assertEquals(User(1, "alice") to User(1, "alicia"), updatedRows[0])
    }
}
