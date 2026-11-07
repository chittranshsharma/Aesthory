package app.aesthory.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aesthory.ui.AesthoryTheme

const val STEP_PRIVACY = "Aesthory reads your daily step totals and contributing app names from Health Connect to show your recent activity. Reading happens when you open Daily steps or tap Refresh. Totals are held in memory while you use this screen. They are not saved in backups, uploaded, shared with AI, sold, or used for advertising. Health Connect keeps the source records. You can change or remove access in Health Connect settings at any time."

class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AesthoryTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Your steps, your choice", style = MaterialTheme.typography.headlineLarge)
                Text(STEP_PRIVACY)
                Button(onClick = { finish() }) { Text("Done") }
            }
        } } }
    }
}
