package app.aesthory.backup

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Password-derived portable encryption; no device key or keyset is exported. */
object BackupCipher {
    private val magic = "AESTHBK1".toByteArray(Charsets.US_ASCII)
    private const val ROUNDS = 600_000
    private const val SEGMENT = 1024 * 1024
    private const val HEADER_SIZE = 32

    fun encrypt(output: OutputStream, password: CharArray, write: (OutputStream) -> Unit) {
        require(password.size in 12..256) { "Use 12–256 characters for your backup password." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER_SIZE).put(magic).putInt(1).putInt(ROUNDS).put(salt).array()
        val key = derive(password, salt)
        try {
            output.write(header)
            AesGcmHkdfStreaming(key, "HmacSha256", 32, SEGMENT, 0).newEncryptingStream(output, header).use(write)
        } finally { key.fill(0) }
    }

    fun decrypt(input: InputStream, password: CharArray, read: (InputStream) -> Unit) {
        require(password.size in 12..256)
        val header = ByteArray(HEADER_SIZE).also { DataInputStream(input).readFully(it) }
        val parsed = ByteBuffer.wrap(header)
        val prefix = ByteArray(8).also { parsed.get(it) }
        if (!prefix.contentEquals(magic)) throw VaultUserError("That isn't a supported Aesthory backup file.")
        if (parsed.int != 1 || parsed.int != ROUNDS) throw VaultUserError("This backup version isn't supported here. Keep the file and update Aesthory before trying again.")
        val salt = ByteArray(16).also { parsed.get(it) }
        val key = derive(password, salt)
        try {
            AesGcmHkdfStreaming(key, "HmacSha256", 32, SEGMENT, 0).newDecryptingStream(input, header).use { decrypted ->
                read(decrypted)
                require(decrypted.read() == -1) { "The backup was not read completely." }
            }
        } finally { key.fill(0) }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ROUNDS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
