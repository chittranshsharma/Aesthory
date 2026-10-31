package app.aesthory

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aesthory.face.FaceReview
import app.aesthory.face.CaptureIssue
import app.aesthory.face.LocalFaceAnalyzer
import app.aesthory.face.PresentationGoal
import app.aesthory.security.GroqKeyStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun encryptedKeyRoundTripAndDeletionUseAnIsolatedTestNamespace() {
        val testKey = "test_key_12345678901234567890"
        val store = GroqKeyStore(context, "instrumentation_test")
        try {
            store.save(testKey)
            assertEquals(testKey, GroqKeyStore(context, "instrumentation_test").read())
            val saved = context.getSharedPreferences("encrypted_credentials_instrumentation_test", Context.MODE_PRIVATE).getString("groq", null)!!
            assertFalse(saved.contains(testKey))
            store.delete()
            assertFalse(store.isConfigured())
            assertNull(store.read())
        } finally { store.delete() }
    }

    @Test fun bundledModelMatchesPinnedOfficialArtifact() {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open("models/face_landmarker.task").use { input ->
            val bytes = ByteArray(8192)
            while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
        }
        assertEquals("64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff", digest.digest().joinToString("") { "%02x".format(it) })
    }

    @Test fun nativeFaceModelRunsLocallyAndRejectsAnImageWithoutAFace() = runBlocking {
        val file = File(context.cacheDir, "instrumentation_blank_face.png")
        try {
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val photo = LocalFaceAnalyzer(context).analyze(Uri.fromFile(file), PresentationGoal.DOCUMENT)
            assertEquals(FaceReview.Recapture(listOf(CaptureIssue.ONE_FACE_REQUIRED)), photo.review)
            assertFalse("Preview must survive closing the native inference image", photo.bitmap.isRecycled)
            val canvasBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(canvasBitmap).drawBitmap(photo.bitmap, 0f, 0f, null)
            canvasBitmap.recycle()
            photo.bitmap.recycle()
        } finally { file.delete() }
    }
}
