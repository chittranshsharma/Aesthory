package app.aesthory.library

import org.junit.Assert.*
import org.junit.Test

class ReferenceFilesTest {
    @Test fun textImportPreservesUnicodeAndMarkdownForReviewWithoutExecutingIt() {
        val text = "# A note\nनमस्ते\n<script>ordinary text</script>"
        assertEquals(text, ReferenceFiles.decode(("\uFEFF" + text).toByteArray(Charsets.UTF_8)))
    }
    @Test fun binaryMalformedBlankAndOversizedInputsAreRejected() {
        listOf(byteArrayOf(0xC3.toByte(), 0x28), "A\u0000B".toByteArray(), "  ".toByteArray(), "a".repeat(32001).toByteArray(), ByteArray(128 * 1024 + 1)).forEach {
            assertTrue(runCatching { ReferenceFiles.decode(it) }.isFailure)
        }
    }
}
