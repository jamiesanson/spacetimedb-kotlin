package dev.sanson.spacetimedb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClientCacheTest {

    // -- TableCache basic operations --

    @Test
    fun `insert new row returns the row`() {
        val cache = TableCache<String>()
        val result = cache.insert(byteArrayOf(1, 2, 3), "hello")
        assertEquals("hello", result)
    }

    @Test
    fun `insert duplicate row returns null`() {
        val cache = TableCache<String>()
        cache.insert(byteArrayOf(1, 2, 3), "hello")
        val result = cache.insert(byteArrayOf(1, 2, 3), "hello")
        assertNull(result)
    }

    @Test
    fun `count reflects distinct rows`() {
        val cache = TableCache<String>()
        assertEquals(0, cache.count)

        cache.insert(byteArrayOf(1), "a")
        assertEquals(1, cache.count)

        cache.insert(byteArrayOf(2), "b")
        assertEquals(2, cache.count)

        // Duplicate doesn't increase count
        cache.insert(byteArrayOf(1), "a")
        assertEquals(2, cache.count)
    }

    @Test
    fun `iter yields all cached rows`() {
        val cache = TableCache<String>()
        cache.insert(byteArrayOf(1), "a")
        cache.insert(byteArrayOf(2), "b")
        cache.insert(byteArrayOf(3), "c")

        val rows = cache.iter().asSequence().toSet()
        assertEquals(setOf("a", "b", "c"), rows)
    }

    @Test
    fun `delete removes row and returns it`() {
        val cache = TableCache<String>()
        cache.insert(byteArrayOf(1, 2, 3), "hello")

        val result = cache.delete(byteArrayOf(1, 2, 3))
        assertEquals("hello", result)
        assertEquals(0, cache.count)
    }

    @Test
    fun `delete unknown row returns null`() {
        val cache = TableCache<String>()
        val result = cache.delete(byteArrayOf(1, 2, 3))
        assertNull(result)
    }

    // -- Reference counting --

    @Test
    fun `ref count prevents premature deletion`() {
        val cache = TableCache<String>()

        // Two overlapping subscriptions insert the same row
        assertNotNull(cache.insert(byteArrayOf(1), "row"))
        assertNull(cache.insert(byteArrayOf(1), "row"))

        // First delete only decrements ref count
        assertNull(cache.delete(byteArrayOf(1)))
        assertEquals(1, cache.count)

        // Second delete fully removes the row
        assertEquals("row", cache.delete(byteArrayOf(1)))
        assertEquals(0, cache.count)
    }

    @Test
    fun `ref count increments correctly for many subscriptions`() {
        val cache = TableCache<String>()
        val bytes = byteArrayOf(42)

        // 5 overlapping subscriptions
        repeat(5) { cache.insert(bytes, "shared") }
        assertEquals(1, cache.count)

        // First 4 deletes only decrement
        repeat(4) { assertNull(cache.delete(bytes)) }
        assertEquals(1, cache.count)

        // 5th delete removes the row
        assertEquals("shared", cache.delete(bytes))
        assertEquals(0, cache.count)
    }

    // -- ClientCache multi-table --

    @Test
    fun `getOrCreateTable creates on first access`() {
        val cache = ClientCache()
        val table = cache.getOrCreateTable<String>("users")
        assertNotNull(table)
        assertEquals(0, table.count)
    }

    @Test
    fun `getOrCreateTable returns same instance`() {
        val cache = ClientCache()
        val t1 = cache.getOrCreateTable<String>("users")
        val t2 = cache.getOrCreateTable<String>("users")
        assertEquals(t1, t2) // Same reference
    }

    @Test
    fun `getTable returns null for unknown table`() {
        val cache = ClientCache()
        assertNull(cache.getTable<String>("unknown"))
    }

    @Test
    fun `tables are independent`() {
        val cache = ClientCache()
        val users = cache.getOrCreateTable<String>("users")
        val items = cache.getOrCreateTable<Int>("items")

        users.insert(byteArrayOf(1), "alice")
        items.insert(byteArrayOf(1), 42)

        assertEquals(1, users.count)
        assertEquals(1, items.count)
    }

    @Test
    fun `tableNames lists all created tables`() {
        val cache = ClientCache()
        cache.getOrCreateTable<String>("users")
        cache.getOrCreateTable<Int>("items")
        assertEquals(setOf("users", "items"), cache.tableNames)
    }

    @Test
    fun `clear empties all tables`() {
        val cache = ClientCache()
        cache.getOrCreateTable<String>("users").insert(byteArrayOf(1), "alice")
        cache.getOrCreateTable<Int>("items").insert(byteArrayOf(2), 42)

        cache.clear()

        assertEquals(0, cache.getOrCreateTable<String>("users").count)
        assertEquals(0, cache.getOrCreateTable<Int>("items").count)
    }

    // -- Edge cases --

    @Test
    fun `different byte keys for same row value are distinct`() {
        val cache = TableCache<String>()
        cache.insert(byteArrayOf(1), "same")
        cache.insert(byteArrayOf(2), "same")
        assertEquals(2, cache.count)
    }

    @Test
    fun `empty byte array works as key`() {
        val cache = TableCache<String>()
        assertNotNull(cache.insert(byteArrayOf(), "empty"))
        assertEquals(1, cache.count)
        assertEquals("empty", cache.delete(byteArrayOf()))
    }

    @Test
    fun `clear resets ref counts`() {
        val cache = TableCache<String>()
        cache.insert(byteArrayOf(1), "row")
        cache.insert(byteArrayOf(1), "row") // ref_count = 2

        cache.clear()
        assertEquals(0, cache.count)

        // Re-insert after clear should behave as fresh
        assertNotNull(cache.insert(byteArrayOf(1), "row"))
        assertEquals("row", cache.delete(byteArrayOf(1)))
    }
}
