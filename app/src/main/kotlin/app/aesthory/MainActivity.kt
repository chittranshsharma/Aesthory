package app.aesthory

import android.os.Bundle
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
                MainViewModel(app.repository, app.faceAnalyzer, app.offlineCoach, app.keys, app.groq)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AesthoryRoot(model) }
    }
    override fun onResume() { super.onResume(); model.refreshDate() }
}
