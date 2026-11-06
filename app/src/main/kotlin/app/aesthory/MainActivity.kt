package app.aesthory

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.aesthory.ui.AesthoryRoot
import app.aesthory.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val model by viewModels<MainViewModel> {
        viewModelFactory {
            initializer {
                val app = application as AesthoryApplication
                MainViewModel(app.repository, app.faceAnalyzer, app.offlineCoach, app.keys, app.groq, app.routineFiles, app.vault, app.referenceFiles, app.timing, app.nutritionOcr, app.workoutFiles)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AesthoryRoot(model) }
        routeNotification(intent)
    }
    override fun onResume() { super.onResume(); model.refreshDate() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); routeNotification(intent) }
    private fun routeNotification(intent: Intent?) {
        when (intent?.action) { "aesthory.OPEN_TIMER" -> model.requestTimer(); "aesthory.OPEN_TODAY" -> model.requestToday() }
    }
}
