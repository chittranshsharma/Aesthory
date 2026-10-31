package app.aesthory.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun validateGroqKey(value: String): String {
    val key = value.trim()
    require(key.length in 20..512 && key.none { it.isISOControl() }) { "Enter a valid API key without line breaks." }
    return key
}

class GroqKeyStore(context: Context, namespace: String = "groq") {
    private val preferences = context.getSharedPreferences("encrypted_credentials_$namespace", Context.MODE_PRIVATE)
    private val alias = "aesthory.$namespace.v1"
    fun isConfigured() = preferences.contains("groq")

    fun save(value: String) {
        val plain = validateGroqKey(value).toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
            val encrypted = cipher.doFinal(plain)
            val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
            check(preferences.edit().putString("groq", encoded).commit()) { "Key could not be saved." }
        } finally { plain.fill(0) }
    }

    fun read(): String? {
        val encoded = preferences.getString("groq", null) ?: return null
        val parts = encoded.split(':')
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        val plain = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
        return try { plain.toString(Charsets.UTF_8) } finally { plain.fill(0) }
    }

    fun delete() {
        check(preferences.edit().remove("groq").commit()) { "Key could not be removed." }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); if (containsAlias(alias)) deleteEntry(alias) }
    }

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
