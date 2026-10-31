package app.aesthory.coaching

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `successful model import streams exact bytes and returns digest`() {
        val destination = temporary.newFile("model.litertlm")
        val content = validContainer()
        val digest = importLocalModel(ByteArrayInputStream(content), destination)
        assertArrayEquals(content, destination.readBytes())
        assertEquals(64, digest.length)
        assertFalse(java.io.File(destination.parentFile, destination.name + ".partial").exists())
    }

    @Test fun `oversized and empty imports preserve the installed model`() {
        val destination = temporary.newFile("model.litertlm").apply { writeText("existing") }
        assertThrows(IllegalArgumentException::class.java) {
            importLocalModel(ByteArrayInputStream(ByteArray(1024)), destination, maxBytes = 512)
        }
        assertEquals("existing", destination.readText())
        assertThrows(IllegalArgumentException::class.java) {
            importLocalModel(ByteArrayInputStream(ByteArray(0)), destination)
        }
        assertEquals("existing", destination.readText())
    }

    @Test fun `interrupted import cleans temporary file without replacing existing bytes`() {
        val destination = temporary.newFile("model.litertlm").apply { writeText("existing") }
        val interrupted = object : InputStream() { override fun read(): Int = throw IOException("Test interruption") }
        assertThrows(IOException::class.java) { importLocalModel(interrupted, destination) }
        assertEquals("existing", destination.readText())
        assertFalse(java.io.File(destination.parentFile, destination.name + ".partial").exists())
    }

    @Test fun `invalid container and failed runtime validation preserve the installed model`() {
        val destination = temporary.newFile("model.litertlm").apply { writeText("existing") }
        assertThrows(IllegalArgumentException::class.java) {
            importLocalModel(ByteArrayInputStream(ByteArray(1024)), destination)
        }
        assertThrows(IllegalStateException::class.java) {
            importLocalModel(ByteArrayInputStream(validContainer()), destination, verify = { error("Runtime rejects fixture") })
        }
        assertEquals("existing", destination.readText())
    }

    private fun validContainer() = ByteArray(1024).apply {
        "LITERTLM".toByteArray(Charsets.US_ASCII).copyInto(this)
        this[8] = 1
    }
}
