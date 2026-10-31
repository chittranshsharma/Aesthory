package app.aesthory.coaching

import app.aesthory.security.GroqKeyStore
import java.net.URL
import java.io.ByteArrayOutputStream
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GroqConnection(private val keys: GroqKeyStore) {
    suspend fun test(): Set<String> = withContext(Dispatchers.IO) {
        val key = requireNotNull(keys.read()) { "Save your API key first." }
        val connection = URL("https://api.groq.com/openai/v1/models").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer $key")
            check(connection.responseCode == 200) {
                when (connection.responseCode) {
                    401, 403 -> "The key was not accepted. Check its access or replace it."
                    429 -> "Groq's request limit was reached. Try again later."
                    else -> "Groq is unavailable. Local features still work."
                }
            }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 512 * 1024) { "The model-list response was too large." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val data = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("data")
            require(data.length() <= 1000)
            buildSet { repeat(data.length()) { add(data.getJSONObject(it).getString("id")) } }
        } finally { connection.disconnect() }
    }
}
