package app.aesthory.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class BackupCipherTest {
    private val password = "test-only-long-password".toCharArray()
    private fun encrypt(bytes: ByteArray, pass: CharArray = password): ByteArray = ByteArrayOutputStream().also { output -> BackupCipher.encrypt(output, pass) { it.write(bytes) } }.toByteArray()
    private fun decrypt(bytes: ByteArray, pass: CharArray = password): ByteArray = ByteArrayOutputStream().also { output -> BackupCipher.decrypt(ByteArrayInputStream(bytes), pass) { it.copyTo(output) } }.toByteArray()

    @Test fun `portable password roundtrip spans multiple authenticated segments`() {
        val payload = ByteArray(2 * 1024 * 1024 + 123) { (it % 251).toByte() }
        assertArrayEquals(payload, decrypt(encrypt(payload)))
    }
    @Test fun `same password produces different ciphertext and supports Unicode`() {
        val payload = "synthetic private records".toByteArray()
        val pass = "test-only नमस्ते password".toCharArray()
        val first = encrypt(payload, pass)
        assertFalse(first.contentEquals(encrypt(payload, pass)))
        assertArrayEquals(payload, decrypt(first, pass))
        assertFalse(first.toString(Charsets.ISO_8859_1).contains(payload.toString(Charsets.UTF_8)))
    }
    @Test fun `wrong password tampering and truncation never authenticate`() {
        val cipher = encrypt(ByteArray(1024 * 1024 + 500) { 42 })
        assertTrue(runCatching { decrypt(cipher, "wrong-test-password".toCharArray()) }.isFailure)
        listOf(0, 15, 20, 60, cipher.lastIndex).forEach { index ->
            val changed = cipher.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertTrue("Changed byte $index must fail", runCatching { decrypt(changed) }.isFailure)
        }
        listOf(0, 20, 500, cipher.size - 1).forEach { size -> assertTrue(runCatching { decrypt(cipher.copyOf(size)) }.isFailure) }
    }
    @Test fun `short password and unsupported header are rejected`() {
        assertTrue(runCatching { encrypt(byteArrayOf(1), "short".toCharArray()) }.isFailure)
        val unsupported = encrypt(byteArrayOf(1)).also { it[11] = 2 }
        assertTrue(runCatching { decrypt(unsupported) }.isFailure)
    }
}
