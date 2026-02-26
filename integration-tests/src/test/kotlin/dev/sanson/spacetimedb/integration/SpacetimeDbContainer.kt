package dev.sanson.spacetimedb.integration

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration

/**
 * Testcontainers wrapper for the SpacetimeDB server.
 *
 * Starts the `clockworklabs/spacetime` Docker image, waits for readiness,
 * and provides helpers for publishing modules via the host `spacetime` CLI.
 */
class SpacetimeDbContainer : GenericContainer<SpacetimeDbContainer>("clockworklabs/spacetime:latest") {

    init {
        withExposedPorts(3000, 80)
        withCommand("start")
        waitingFor(
            Wait.forHttp("/database/dns/system/system")
                .forPort(80)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofSeconds(60))
        )
    }

    /**
     * The WebSocket URL for SDK connections.
     */
    val wsUrl: String
        get() = "ws://${host}:${getMappedPort(80)}"

    /**
     * The HTTP URL for the SpacetimeDB REST API.
     */
    val httpUrl: String
        get() = "http://${host}:${getMappedPort(80)}"

    /**
     * Publish a SpacetimeDB module to this container.
     *
     * @param modulePath absolute path to the Rust module directory
     * @param databaseName name for the published database
     * @param skipClippy whether to skip linting (faster builds)
     */
    fun publishModule(
        modulePath: String,
        databaseName: String,
        skipClippy: Boolean = true,
    ) {
        val command = buildList {
            add("spacetime")
            add("publish")
            add(databaseName)
            add("--module-path")
            add(modulePath)
            add("--server")
            add(httpUrl)
            add("--delete-data=always")
            if (skipClippy) {
                add("--build-options=--lint-dir=")
            }
        }

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException(
                "spacetime publish failed (exit $exitCode):\n$output"
            )
        }
    }
}
