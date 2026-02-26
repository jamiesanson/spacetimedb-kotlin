package com.example.game

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val ctx = DbConnection.builder()
        .withUri("http://localhost:3000")
        .withDatabaseName("example")
        .onConnect { identity, token, connectionId ->
            println("Connected as $identity (connection=$connectionId)")
        }
        .build(this)

    // Subscribe to all players and messages
    ctx.subscriptionBuilder()
        .subscribe(listOf("SELECT * FROM player", "SELECT * FROM message"))

    // Give the subscription a moment to sync
    delay(1_000)

    // Add some players
    ctx.reducers.addPlayer("Alice")
    ctx.reducers.addPlayer("Bob")
    delay(500)

    // Set a score
    val alice = ctx.db.player.findByName("Alice")
    if (alice != null) {
        ctx.reducers.setScore(alice.id, 100u)
        println("Set Alice's score to 100")
    }

    // Send a message
    ctx.reducers.sendMessage("Alice", "Hello from Kotlin!")
    delay(500)

    // Print all players
    println("\nPlayers:")
    for (player in ctx.db.player) {
        println("  ${player.name} (score=${player.score})")
    }

    // Print all messages
    println("\nMessages:")
    for (message in ctx.db.message) {
        println("  [${message.sender}] ${message.text}")
    }

    // Observe future inserts
    ctx.db.player.onInsert { _, player ->
        println("New player: ${player.name}")
    }

    ctx.reducers.addPlayer("Charlie")
    delay(500)

    ctx.disconnect()
    println("\nDisconnected.")
}
