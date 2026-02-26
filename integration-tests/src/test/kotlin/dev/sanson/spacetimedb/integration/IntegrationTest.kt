package dev.sanson.spacetimedb.integration

import dev.sanson.spacetimedb.Event
import dev.sanson.spacetimedb.Status
import dev.sanson.spacetimedb.integration.module.DbConnection
import dev.sanson.spacetimedb.integration.module.Player
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests that verify the SDK against a real SpacetimeDB instance.
 *
 * Each test is self-contained: it creates its own connection, subscribes,
 * performs operations, and disconnects. Tests use unique player names to
 * avoid conflicts when running in any order.
 */
@Testcontainers
class IntegrationTest {

    companion object {
        private const val DATABASE_NAME = "kotlin-sdk-test"

        @Container
        @JvmStatic
        val spacetime = SpacetimeDbContainer()

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @JvmStatic
        @BeforeAll
        fun publishModule() {
            val serverDir = findServerModule()
            spacetime.publishModule(
                modulePath = serverDir.absolutePath,
                databaseName = DATABASE_NAME,
            )
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            scope.cancel()
        }

        private fun findServerModule(): File {
            // Look for example/server relative to the project root
            val candidates = listOf(
                File("../example/server"),
                File("example/server"),
            )
            return candidates.firstOrNull { it.resolve("Cargo.toml").exists() }
                ?: error("Could not find example/server module. Searched: ${candidates.map { it.absolutePath }}")
        }

        private fun connect(
            scope: CoroutineScope,
            onConnect: CompletableDeferred<Unit> = CompletableDeferred(),
        ): DbConnection {
            return DbConnection.builder()
                .withUri(spacetime.wsUrl)
                .withDatabaseName(DATABASE_NAME)
                .onConnect { _, _, _ -> onConnect.complete(Unit) }
                .onConnectError { error -> onConnect.completeExceptionally(RuntimeException("Connect error: $error")) }
                .build(scope)
        }
    }

    @Test
    fun `connect fires onConnect with identity and token`() = runBlocking {
        withTimeout(30.seconds) {
            val connected = CompletableDeferred<Unit>()
            val conn = connect(scope, connected)

            connected.await()

            assertNotNull(conn.identity, "Identity should be set after connect")
            assertNotNull(conn.connectionId, "ConnectionId should be set after connect")
            assertTrue(conn.isActive, "Connection should be active")

            conn.disconnect()
        }
    }

    @Test
    fun `subscribe fires onApplied`() = runBlocking {
        withTimeout(30.seconds) {
            val connected = CompletableDeferred<Unit>()
            val conn = connect(scope, connected)
            connected.await()

            val applied = CompletableDeferred<Unit>()
            conn.subscriptionBuilder()
                .onApplied { applied.complete(Unit) }
                .subscribe("SELECT * FROM player", "SELECT * FROM message")

            applied.await()

            conn.disconnect()
        }
    }

    @Test
    fun `addPlayer inserts row and fires onInsert`() = runBlocking {
        withTimeout(30.seconds) {
            val connected = CompletableDeferred<Unit>()
            val conn = connect(scope, connected)
            connected.await()

            val applied = CompletableDeferred<Unit>()
            conn.subscriptionBuilder()
                .onApplied { applied.complete(Unit) }
                .subscribe("SELECT * FROM player")
            applied.await()

            val inserted = CompletableDeferred<Player>()
            conn.db.player.onInsert { _, player ->
                if (player.name == "Alice") {
                    inserted.complete(player)
                }
            }

            conn.reducers.addPlayer("Alice")

            val player = inserted.await()
            assertEquals("Alice", player.name)
            assertEquals(0u, player.score)

            // Verify row is in the cache
            val cached = conn.db.player.findByName("Alice")
            assertNotNull(cached, "Player should be in client cache")
            assertEquals("Alice", cached.name)

            conn.disconnect()
        }
    }

    @Test
    fun `reducer callback fires with committed status`() = runBlocking {
        withTimeout(30.seconds) {
            val connected = CompletableDeferred<Unit>()
            val conn = connect(scope, connected)
            connected.await()

            val applied = CompletableDeferred<Unit>()
            conn.subscriptionBuilder()
                .onApplied { applied.complete(Unit) }
                .subscribe("SELECT * FROM player")
            applied.await()

            val callbackFired = CompletableDeferred<Unit>()
            conn.reducers.onAddPlayer { event ->
                if (event.status is Status.Committed) {
                    callbackFired.complete(Unit)
                }
            }

            conn.reducers.addPlayer("Bob")

            callbackFired.await()

            conn.disconnect()
        }
    }

    @Test
    fun `setScore fires onUpdate with old and new row`() = runBlocking {
        withTimeout(30.seconds) {
            val connected = CompletableDeferred<Unit>()
            val conn = connect(scope, connected)
            connected.await()

            val applied = CompletableDeferred<Unit>()
            conn.subscriptionBuilder()
                .onApplied { applied.complete(Unit) }
                .subscribe("SELECT * FROM player")
            applied.await()

            // First insert a player
            val inserted = CompletableDeferred<Player>()
            conn.db.player.onInsert { _, player ->
                if (player.name == "Charlie") {
                    inserted.complete(player)
                }
            }
            conn.reducers.addPlayer("Charlie")
            val charlie = inserted.await()

            // Now update the score and observe onUpdate
            val updated = CompletableDeferred<Pair<Player, Player>>()
            conn.db.player.onUpdate { _, oldRow, newRow ->
                if (newRow.name == "Charlie") {
                    updated.complete(oldRow to newRow)
                }
            }

            conn.reducers.setScore(charlie.id, 42u)

            val (oldRow, newRow) = updated.await()
            assertEquals(0u, oldRow.score)
            assertEquals(42u, newRow.score)
            assertEquals("Charlie", newRow.name)

            conn.disconnect()
        }
    }

    @Test
    fun `second client observes changes via Event Transaction`() = runBlocking {
        withTimeout(30.seconds) {
            // Client 1: subscribe and add a player
            val conn1Connected = CompletableDeferred<Unit>()
            val conn1 = connect(scope, conn1Connected)
            conn1Connected.await()

            val applied1 = CompletableDeferred<Unit>()
            conn1.subscriptionBuilder()
                .onApplied { applied1.complete(Unit) }
                .subscribe("SELECT * FROM player")
            applied1.await()

            // Client 2: subscribe and observe
            val conn2Connected = CompletableDeferred<Unit>()
            val conn2 = connect(scope, conn2Connected)
            conn2Connected.await()

            val applied2 = CompletableDeferred<Unit>()
            conn2.subscriptionBuilder()
                .onApplied { applied2.complete(Unit) }
                .subscribe("SELECT * FROM player")
            applied2.await()

            // Client 2 watches for inserts
            val observedInsert = CompletableDeferred<Pair<Event<*>, Player>>()
            conn2.db.player.onInsert { event, player ->
                if (player.name == "Dave") {
                    observedInsert.complete(event to player)
                }
            }

            // Client 1 triggers an insert
            conn1.reducers.addPlayer("Dave")

            val (event, player) = observedInsert.await()
            assertEquals("Dave", player.name)
            // Client 2 should see this as a Transaction event (not Reducer)
            assertTrue(event is Event.Transaction, "Second client should observe Event.Transaction, got: $event")

            conn1.disconnect()
            conn2.disconnect()
        }
    }
}
