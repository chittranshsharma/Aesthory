package app.aesthory.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KeyValidationTest {
    @Test fun `trims copied whitespace but rejects embedded header injection`() {
        assertEquals("test_key_123456789012345", validateGroqKey("  test_key_123456789012345  "))
        assertThrows(IllegalArgumentException::class.java) { validateGroqKey("test_key_12345\r\nX-Test: value") }
    }
    @Test fun `empty short and oversized credentials are rejected`() {
        listOf("", "short", "x".repeat(513)).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { validateGroqKey(value) }
        }
    }
}
