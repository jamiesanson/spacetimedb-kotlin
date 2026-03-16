package dev.sanson.spacetimedb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableUpdateTest {

    data class User(val id: Int, val name: String)

    // -- TableCache.applyDiff --

    @Test
    fun `applyDiff inserts new rows`() {
        val cache = TableCache<User>()
        val update =
            TableUpdate(
                inserts =
                    listOf(
                        RowWithBsatn(byteArrayOf(1), User(1, "alice")),
                        RowWithBsatn(byteArrayOf(2), User(2, "bob")),
                    ),
                deletes = emptyList(),
            )

        val diff = cache.applyDiff(update)

        assertEquals(2, diff.inserts.size)
        assertEquals(2, cache.count)
        assertTrue(diff.deletes.isEmpty())
    }

    @Test
    fun `applyDiff deletes existing rows`() {
        val cache = TableCache<User>()
        cache.insert(byteArrayOf(1), User(1, "alice"))
        cache.insert(byteArrayOf(2), User(2, "bob"))

        val update =
            TableUpdate<User>(
                inserts = emptyList(),
                deletes = listOf(RowWithBsatn(byteArrayOf(1), User(1, "alice"))),
            )

        val diff = cache.applyDiff(update)

        assertEquals(1, diff.deletes.size)
        assertEquals(User(1, "alice"), diff.deletes[0])
        assertEquals(1, cache.count)
    }

    @Test
    fun `applyDiff empty update produces empty diff`() {
        val cache = TableCache<User>()
        val diff = cache.applyDiff(TableUpdate(emptyList(), emptyList()))
        assertTrue(diff.isEmpty)
    }

    @Test
    fun `applyDiff ref count bump produces no insert`() {
        val cache = TableCache<User>()
        cache.insert(byteArrayOf(1), User(1, "alice"))

        val update =
            TableUpdate(
                inserts = listOf(RowWithBsatn(byteArrayOf(1), User(1, "alice"))),
                deletes = emptyList(),
            )

        val diff = cache.applyDiff(update)
        assertTrue(diff.inserts.isEmpty())
        assertEquals(1, cache.count)
    }

    @Test
    fun `applyDiff ref count decrement produces no delete`() {
        val cache = TableCache<User>()
        cache.insert(byteArrayOf(1), User(1, "alice"))
        cache.insert(byteArrayOf(1), User(1, "alice")) // ref_count = 2

        val update =
            TableUpdate<User>(
                inserts = emptyList(),
                deletes = listOf(RowWithBsatn(byteArrayOf(1), User(1, "alice"))),
            )

        val diff = cache.applyDiff(update)
        assertTrue(diff.deletes.isEmpty())
        assertEquals(1, cache.count)
    }

    @Test
    fun `applyDiff with same row in inserts and deletes cancels out`() {
        val cache = TableCache<User>()
        cache.insert(byteArrayOf(1), User(1, "alice"))

        // Row inserted then deleted in same transaction — net no-op
        val update =
            TableUpdate(
                inserts = listOf(RowWithBsatn(byteArrayOf(1), User(1, "alice"))),
                deletes = listOf(RowWithBsatn(byteArrayOf(1), User(1, "alice"))),
            )

        val diff = cache.applyDiff(update)
        assertTrue(diff.inserts.isEmpty())
        assertTrue(diff.deletes.isEmpty())
        assertEquals(1, cache.count)
    }

    @Test
    fun `applyDiff updates unique indexes`() {
        val cache = TableCache<User>()
        val index = cache.registerUniqueIndex(UniqueIndex(getColumn = { it.id }))

        val update =
            TableUpdate(
                inserts = listOf(RowWithBsatn(byteArrayOf(1), User(1, "alice"))),
                deletes = emptyList(),
            )

        cache.applyDiff(update)
        assertEquals(User(1, "alice"), index.find(1))
    }

    // -- TableAppliedDiff.withUpdatesByPk --

    @Test
    fun `withUpdatesByPk matches delete-insert pairs by PK`() {
        val diff =
            TableAppliedDiff(inserts = listOf(User(1, "bob")), deletes = listOf(User(1, "alice")))

        val refined = diff.withUpdatesByPk { it.id }

        assertTrue(refined.inserts.isEmpty())
        assertTrue(refined.deletes.isEmpty())
        assertEquals(1, refined.updateDeletes.size)
        assertEquals(1, refined.updateInserts.size)
        assertEquals(User(1, "alice"), refined.updateDeletes[0])
        assertEquals(User(1, "bob"), refined.updateInserts[0])
    }

    @Test
    fun `withUpdatesByPk separates updates from pure inserts and deletes`() {
        val diff =
            TableAppliedDiff(
                inserts = listOf(User(1, "bob"), User(3, "charlie")),
                deletes = listOf(User(1, "alice"), User(2, "dave")),
            )

        val refined = diff.withUpdatesByPk { it.id }

        // PK=1: delete(alice) + insert(bob) → update
        assertEquals(listOf(User(1, "alice")), refined.updateDeletes)
        assertEquals(listOf(User(1, "bob")), refined.updateInserts)

        // PK=3: pure insert
        assertEquals(listOf(User(3, "charlie")), refined.inserts)

        // PK=2: pure delete
        assertEquals(listOf(User(2, "dave")), refined.deletes)
    }

    @Test
    fun `withUpdatesByPk with no matching PKs returns same diff`() {
        val diff =
            TableAppliedDiff(inserts = listOf(User(1, "alice")), deletes = listOf(User(2, "bob")))

        val refined = diff.withUpdatesByPk { it.id }

        assertEquals(diff.inserts, refined.inserts)
        assertEquals(diff.deletes, refined.deletes)
        assertTrue(refined.updateDeletes.isEmpty())
    }

    @Test
    fun `withUpdatesByPk with empty inserts is no-op`() {
        val diff = TableAppliedDiff(inserts = emptyList<User>(), deletes = listOf(User(1, "alice")))

        val refined = diff.withUpdatesByPk { it.id }
        assertEquals(diff.deletes, refined.deletes)
        assertTrue(refined.inserts.isEmpty())
    }

    @Test
    fun `withUpdatesByPk with empty deletes is no-op`() {
        val diff = TableAppliedDiff(inserts = listOf(User(1, "alice")), deletes = emptyList())

        val refined = diff.withUpdatesByPk { it.id }
        assertEquals(diff.inserts, refined.inserts)
        assertTrue(refined.deletes.isEmpty())
    }

    @Test
    fun `isEmpty is true when all lists are empty`() {
        val diff = TableAppliedDiff<User>(emptyList(), emptyList())
        assertTrue(diff.isEmpty)
    }

    @Test
    fun `isEmpty is false with inserts`() {
        val diff = TableAppliedDiff(listOf(User(1, "alice")), emptyList())
        assertTrue(!diff.isEmpty)
    }

    @Test
    fun `isEmpty is false with updates`() {
        val diff =
            TableAppliedDiff(
                inserts = emptyList<User>(),
                deletes = emptyList(),
                updateDeletes = listOf(User(1, "alice")),
                updateInserts = listOf(User(1, "bob")),
            )
        assertTrue(!diff.isEmpty)
    }
}
