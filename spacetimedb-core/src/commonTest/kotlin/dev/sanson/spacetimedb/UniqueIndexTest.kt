package dev.sanson.spacetimedb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UniqueIndexTest {

    data class User(val id: Int, val name: String)

    // -- Standalone UniqueIndex --

    @Test
    fun `find returns null for empty index`() {
        val index = UniqueIndex<User, Int>(getColumn = { it.id })
        assertNull(index.find(1))
    }

    @Test
    fun `find returns row after add`() {
        val index = UniqueIndex<User, Int>(getColumn = { it.id })
        val user = User(1, "alice")
        index.add(user)
        assertEquals(user, index.find(1))
    }

    @Test
    fun `find returns null after remove`() {
        val index = UniqueIndex<User, Int>(getColumn = { it.id })
        val user = User(1, "alice")
        index.add(user)
        index.remove(user)
        assertNull(index.find(1))
    }

    @Test
    fun `add duplicate key throws`() {
        val index = UniqueIndex<User, Int>(getColumn = { it.id })
        index.add(User(1, "alice"))
        assertFailsWith<IllegalStateException> {
            index.add(User(1, "bob"))
        }
        // Original row should still be present after failed add
        assertEquals("alice", index.find(1)?.name)
    }

    @Test
    fun `remove non-present row throws`() {
        val index = UniqueIndex<User, Int>(getColumn = { it.id })
        assertFailsWith<IllegalStateException> {
            index.remove(User(99, "ghost"))
        }
    }

    @Test
    fun `size tracks entries`() {
        val index = UniqueIndex<User, Int>(getColumn = { it.id })
        assertEquals(0, index.size)
        index.add(User(1, "alice"))
        assertEquals(1, index.size)
        index.add(User(2, "bob"))
        assertEquals(2, index.size)
        index.remove(User(1, "alice"))
        assertEquals(1, index.size)
    }

    @Test
    fun `clear empties the index`() {
        val index = UniqueIndex<User, Int>(getColumn = { it.id })
        index.add(User(1, "alice"))
        index.add(User(2, "bob"))
        index.clear()
        assertEquals(0, index.size)
        assertNull(index.find(1))
    }

    // -- UniqueIndex integrated with TableCache --

    @Test
    fun `index is populated on register from existing rows`() {
        val cache = TableCache<User>()
        cache.insert(byteArrayOf(1), User(1, "alice"))
        cache.insert(byteArrayOf(2), User(2, "bob"))

        val index = cache.registerUniqueIndex(UniqueIndex(getColumn = { it.id }))
        assertEquals(User(1, "alice"), index.find(1))
        assertEquals(User(2, "bob"), index.find(2))
    }

    @Test
    fun `index auto-updates on insert`() {
        val cache = TableCache<User>()
        val index = cache.registerUniqueIndex(UniqueIndex(getColumn = { it.id }))

        cache.insert(byteArrayOf(1), User(1, "alice"))
        assertEquals(User(1, "alice"), index.find(1))
    }

    @Test
    fun `index auto-updates on delete`() {
        val cache = TableCache<User>()
        val index = cache.registerUniqueIndex(UniqueIndex(getColumn = { it.id }))

        cache.insert(byteArrayOf(1), User(1, "alice"))
        cache.delete(byteArrayOf(1))
        assertNull(index.find(1))
    }

    @Test
    fun `index not updated on ref count bump`() {
        val cache = TableCache<User>()
        val index = cache.registerUniqueIndex(UniqueIndex(getColumn = { it.id }))

        cache.insert(byteArrayOf(1), User(1, "alice"))
        // Second insert bumps ref count but should not duplicate in index
        cache.insert(byteArrayOf(1), User(1, "alice"))
        assertEquals(1, index.size)
    }

    @Test
    fun `index not updated on ref count decrement`() {
        val cache = TableCache<User>()
        val index = cache.registerUniqueIndex(UniqueIndex(getColumn = { it.id }))

        cache.insert(byteArrayOf(1), User(1, "alice"))
        cache.insert(byteArrayOf(1), User(1, "alice")) // ref_count = 2

        // First delete only decrements — index should keep the row
        cache.delete(byteArrayOf(1))
        assertNotNull(index.find(1))

        // Second delete fully removes — index should drop the row
        cache.delete(byteArrayOf(1))
        assertNull(index.find(1))
    }

    @Test
    fun `cache clear also clears indexes`() {
        val cache = TableCache<User>()
        val index = cache.registerUniqueIndex(UniqueIndex(getColumn = { it.id }))

        cache.insert(byteArrayOf(1), User(1, "alice"))
        cache.clear()

        assertEquals(0, index.size)
        assertNull(index.find(1))
    }

    @Test
    fun `multiple indexes on same table`() {
        val cache = TableCache<User>()
        val byId = cache.registerUniqueIndex(UniqueIndex(getColumn = { it.id }))
        val byName = cache.registerUniqueIndex(UniqueIndex(getColumn = { it.name }))

        cache.insert(byteArrayOf(1), User(1, "alice"))
        assertEquals(User(1, "alice"), byId.find(1))
        assertEquals(User(1, "alice"), byName.find("alice"))

        cache.delete(byteArrayOf(1))
        assertNull(byId.find(1))
        assertNull(byName.find("alice"))
    }
}
