package app.aesthory

import android.app.Application
import app.aesthory.coaching.GroqConnection
import app.aesthory.coaching.OfflineCoach
import app.aesthory.data.AesthoryDatabase
import app.aesthory.data.AesthoryRepository
import app.aesthory.face.LocalFaceAnalyzer
import app.aesthory.security.GroqKeyStore
import app.aesthory.routines.RoutineFiles

class AesthoryApplication : Application() {
    private val database by lazy { AesthoryDatabase.open(this) }
    val repository by lazy { AesthoryRepository(database) }
    val faceAnalyzer by lazy { LocalFaceAnalyzer(this) }
    val offlineCoach by lazy { OfflineCoach(this) }
    val keys by lazy { GroqKeyStore(this) }
    val groq by lazy { GroqConnection(keys) }
    val routineFiles by lazy { RoutineFiles(this) }
}
