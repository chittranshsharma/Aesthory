package app.aesthory.coaching

import android.content.Context
import android.net.Uri
import app.aesthory.face.FaceReview
import app.aesthory.face.PresentationGoal
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OfflineCoach(private val context: Context) {
    private val model = File(context.filesDir, "local-coach/model.litertlm")
    private val lock = Mutex()
    fun isInstalled() = model.isFile

    suspend fun importModel(uri: Uri): String = lock.withLock {
        withContext(Dispatchers.IO) {
            val coroutine = currentCoroutineContext()
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                importLocalModel(input, model,
                    verify = { pending ->
                        coroutine.ensureActive()
                        Engine(EngineConfig(modelPath = pending.absolutePath, backend = Backend.CPU(), maxNumTokens = 2048,
                            cacheDir = context.cacheDir.absolutePath)).use { it.initialize() }
                        coroutine.ensureActive()
                    },
                    onProgress = { coroutine.ensureActive() },
                )
            }
        }
    }

    suspend fun removeModel() = lock.withLock {
        withContext(Dispatchers.IO) { check(!model.exists() || model.delete()) { "The local model could not be removed." } }
    }

    suspend fun review(report: FaceReview.Measured, goal: PresentationGoal): String = lock.withLock {
        withContext(Dispatchers.IO) {
            check(model.isFile) { "Import a compatible LiteRT-LM model first." }
            val common = context.assets.open("coaching/common.md").bufferedReader().use { it.readText() }
            val specialist = context.assets.open(CoachMode.FACIAL_AESTHETICS.promptAsset).bufferedReader().use { it.readText() }
            Engine(EngineConfig(
                modelPath = model.absolutePath, backend = Backend.CPU(),
                maxNumTokens = 2048, cacheDir = context.cacheDir.absolutePath,
            )).use { engine ->
                engine.initialize()
                engine.createConversation(ConversationConfig(
                    thinkingConfig = ThinkingConfig(enableThinking = false, thinkingTokenBudget = 0),
                    systemInstruction = Contents.of(common + "\n" + specialist +
                        "\nGive a short presentation-coaching draft in plain text. Do not invent or restate measurements. " +
                        "Do not diagnose, claim bone changes, or assign a PSL score. Offer reversible options and uncertainty."),
                )).use { conversation ->
                    val answer = StringBuilder()
                    conversation.sendMessageAsync(
                        "User-selected goal: ${goal.name}. Local 2D observations: ${report.metrics}. " +
                            "These are surface-landmark estimates, not bone measurements. " +
                            "Suggest up to three practical, reversible presentation experiments; no medical or surgical plan.",
                        maxOutputToken = 512,
                    ).collect { message ->
                        currentCoroutineContext().ensureActive()
                        message.contents.contents.filterIsInstance<Content.Text>().forEach { answer.append(it.text) }
                    }
                    answer.toString().take(8000).ifBlank { error("The model returned no coaching text.") }
                }
            }
        }
    }
}
