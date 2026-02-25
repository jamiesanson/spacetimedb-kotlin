package dev.sanson.spacetimedb.transport

import dev.sanson.spacetimedb.bsatn.Bsatn
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

private const val CREDENTIALS_DIR = ".spacetimedb_client_credentials"

/**
 * Internal BSATN-serialized credential record.
 * Wire-compatible with the Rust SDK's `Credentials { token: String }`.
 */
@Serializable
private class CredentialRecord(val token: String)

/**
 * A file on disk which stores (or can store) a JWT for authenticating
 * with SpacetimeDB.
 *
 * The file does not necessarily exist or contain credentials.
 * If credentials have been stored previously, they can be retrieved with [load].
 * New credentials can be saved to disk with [save].
 *
 * @param key Filename within the credentials directory. Distinct applications
 *   on the same machine can share tokens by using the same key.
 * @param fileSystem The filesystem to use for I/O (inject [okio.fakefilesystem.FakeFileSystem]
 *   for testing).
 * @param baseDir The base directory containing the credentials directory.
 *   Defaults to the user's home directory.
 */
public class CredentialFile(
    private val key: String,
    private val fileSystem: FileSystem,
    private val baseDir: Path,
) {
    private val credentialsDir: Path get() = baseDir / CREDENTIALS_DIR
    private val filePath: Path get() = credentialsDir / key

    /**
     * Store the provided [token] to disk.
     *
     * Future calls to [load] on a [CredentialFile] with the same key can
     * retrieve the token.
     */
    public fun save(token: String) {
        fileSystem.createDirectories(credentialsDir)

        val bytes = Bsatn.encodeToByteArray(CredentialRecord.serializer(), CredentialRecord(token))
        fileSystem.write(filePath) {
            write(bytes)
        }
    }

    /**
     * Load a previously saved token from disk.
     *
     * Returns `null` if no credentials have been stored for this key.
     */
    public fun load(): String? {
        if (!fileSystem.exists(filePath)) return null

        val bytes = fileSystem.read(filePath) { readByteArray() }
        val record = Bsatn.decodeFromByteArray(CredentialRecord.serializer(), bytes)
        return record.token
    }

    public companion object {
        /**
         * Create a [CredentialFile] using the system filesystem and default
         * credentials directory (`~/.spacetimedb_client_credentials/`).
         *
         * @throws UnsupportedOperationException on platforms without filesystem access
         *   (e.g., browser JS).
         */
        public fun create(key: String): CredentialFile {
            val homeDir = defaultHomeDirectory()
                ?: throw UnsupportedOperationException(
                    "Cannot determine home directory on this platform. " +
                        "Use the CredentialFile constructor with explicit paths instead."
                )

            return CredentialFile(
                key = key,
                fileSystem = systemFileSystem(),
                baseDir = homeDir,
            )
        }
    }
}

/**
 * Returns the platform's system filesystem.
 */
internal expect fun systemFileSystem(): FileSystem

/**
 * Returns the user's home directory, or null if it cannot be determined.
 */
internal expect fun defaultHomeDirectory(): Path?
