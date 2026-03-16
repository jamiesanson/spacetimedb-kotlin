package dev.sanson.spacetimedb.transport

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class CredentialFileTest {
    private val fakeFs = FakeFileSystem()
    private val baseDir = "/home/user".toPath()

    @AfterTest
    fun tearDown() {
        fakeFs.checkNoOpenFiles()
    }

    private fun credentialFile(key: String = "test_app") =
        CredentialFile(key = key, fileSystem = fakeFs, baseDir = baseDir)

    @Test
    fun `load returns null when no credentials stored`() {
        assertNull(credentialFile().load())
    }

    @Test
    fun `save then load round-trips token`() {
        val file = credentialFile()
        file.save("my-secret-token")

        val loaded = credentialFile().load()
        assertEquals("my-secret-token", loaded)
    }

    @Test
    fun `save creates credentials directory`() {
        credentialFile().save("token")
        assertTrue(fakeFs.exists(baseDir / ".spacetimedb_client_credentials"))
    }

    @Test
    fun `save overwrites existing credentials`() {
        val file = credentialFile()
        file.save("old-token")
        file.save("new-token")

        assertEquals("new-token", credentialFile().load())
    }

    @Test
    fun `different keys store different credentials`() {
        credentialFile("app_a").save("token-a")
        credentialFile("app_b").save("token-b")

        assertEquals("token-a", credentialFile("app_a").load())
        assertEquals("token-b", credentialFile("app_b").load())
    }

    @Test
    fun `empty token round-trips`() {
        credentialFile().save("")
        assertEquals("", credentialFile().load())
    }

    @Test
    fun `long token round-trips`() {
        val longToken = "x".repeat(10_000)
        credentialFile().save(longToken)
        assertEquals(longToken, credentialFile().load())
    }

    @Test
    fun `credentials stored in BSATN format`() {
        credentialFile().save("hello")

        // Verify the file exists at the expected path
        val path = baseDir / ".spacetimedb_client_credentials" / "test_app"
        assertTrue(fakeFs.exists(path))

        // Read raw bytes and verify BSATN format:
        // BSATN string = u32_le(length) + utf8_bytes
        // Credential struct has one field (token), so it's just the string encoding
        val bytes = fakeFs.read(path) { readByteArray() }
        val expectedLength = 4 + 5 // u32 length prefix + "hello"
        assertEquals(expectedLength, bytes.size)
    }
}
