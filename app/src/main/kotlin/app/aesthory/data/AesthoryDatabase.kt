package app.aesthory.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.aesthory.progress.ProgressPhoto
import app.aesthory.coaching.CoachMode
import app.aesthory.routines.HabitState
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

class DatabaseConverters {
    @TypeConverter fun coachesToText(values: Set<CoachMode>): String = values.sortedBy { it.name }.joinToString(",") { it.name }
    @TypeConverter fun textToCoaches(text: String): Set<CoachMode> =
        if (text.isEmpty()) emptySet() else text.split(',').map { CoachMode.valueOf(it) }.toSet()
    @TypeConverter fun stringsToText(values: List<String>): String = JSONArray(values).toString()
    @TypeConverter fun textToStrings(text: String): List<String> = JSONArray(text).let { json -> List(json.length()) { json.getString(it) } }
    @TypeConverter fun intsToText(values: List<Int>): String = JSONArray(values).toString()
    @TypeConverter fun textToInts(text: String): List<Int> = JSONArray(text).let { json -> List(json.length()) { json.getInt(it) } }
}

@Dao
interface AesthoryDao {
    @Query("SELECT * FROM progress_photos ORDER BY day DESC, createdAtMillis DESC, id") fun observePhotos(): Flow<List<ProgressPhoto>>
    @Query("SELECT * FROM progress_photos") suspend fun allPhotos(): List<ProgressPhoto>
    @Insert suspend fun insertPhoto(photo: ProgressPhoto)
    @Update suspend fun updatePhoto(photo: ProgressPhoto)
    @Query("UPDATE progress_photos SET deletedAtMillis = :at WHERE id = :id") suspend fun setPhotoDeleted(id: String, at: Long?)
    @Query("DELETE FROM progress_photos WHERE id = :id") suspend fun deletePhoto(id: String)
    @Query("SELECT * FROM profiles") suspend fun allProfiles(): List<ProfileEntity>
    @Query("SELECT * FROM measurements") suspend fun allMeasurements(): List<MeasurementEntity>
    @Query("SELECT * FROM routines") suspend fun allRoutines(): List<RoutineEntity>
    @Query("SELECT * FROM habits") suspend fun allHabits(): List<HabitEntity>
    @Query("SELECT * FROM habit_versions") suspend fun allVersions(): List<HabitVersionEntity>
    @Query("SELECT * FROM completion_events") suspend fun allEvents(): List<CompletionEventEntity>
    @Upsert suspend fun saveProfile(profile: ProfileEntity)
    @Query("SELECT * FROM profiles WHERE id = 1") fun observeProfile(): Flow<ProfileEntity?>
    @Query("SELECT * FROM profiles WHERE id = 1") suspend fun getProfile(): ProfileEntity?
    @Insert suspend fun insertMeasurement(measurement: MeasurementEntity): Long
    @Query("SELECT * FROM measurements ORDER BY recordedAtMillis DESC, id DESC") fun observeMeasurements(): Flow<List<MeasurementEntity>>
    @Insert suspend fun insertCategory(category: CategoryEntity)
    @Query("SELECT * FROM categories ORDER BY name") fun observeCategories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories ORDER BY name") suspend fun getCategories(): List<CategoryEntity>
    @Insert suspend fun insertRoutine(routine: RoutineEntity)
    @Query("SELECT * FROM routines WHERE archived = 0 ORDER BY createdAtMillis, id") fun observeRoutines(): Flow<List<RoutineEntity>>
    @Query("UPDATE routines SET archived = 1 WHERE id = :id") suspend fun archiveRoutine(id: String)
    @Query("UPDATE routines SET archived = 0 WHERE id = :id") suspend fun restoreRoutine(id: String)
    @Query("SELECT * FROM routines WHERE id = :id") suspend fun getRoutine(id: String): RoutineEntity?
    @Insert suspend fun insertHabit(habit: HabitEntity)
    @Insert suspend fun insertVersion(version: HabitVersionEntity)
    @Query("SELECT * FROM habits WHERE id = :id") suspend fun getHabit(id: String): HabitEntity?
    @Query("SELECT * FROM habits WHERE routineId = :routineId ORDER BY position, id") suspend fun getHabits(routineId: String): List<HabitEntity>
    @Query("SELECT v.*, h.position, h.state, h.routineId FROM habit_versions v JOIN habits h ON v.habitId = h.id AND v.version = h.currentVersion WHERE h.routineId = :routineId ORDER BY h.position, h.id")
    fun observeCurrentHabits(routineId: String): Flow<List<CurrentHabit>>
    @Query("SELECT v.*, h.position, h.state, h.routineId FROM habit_versions v JOIN habits h ON v.habitId = h.id AND v.version = h.currentVersion JOIN routines r ON h.routineId = r.id WHERE h.state = 'ACTIVE' AND r.archived = 0 ORDER BY r.createdAtMillis, r.id, h.position, h.id")
    fun observeActiveHabits(): Flow<List<CurrentHabit>>
    @Query("SELECT * FROM habit_versions WHERE habitId = :habitId AND version = :version") suspend fun getVersion(habitId: String, version: Int): HabitVersionEntity?
    @Query("UPDATE habits SET currentVersion = :version WHERE id = :id") suspend fun setVersion(id: String, version: Int)
    @Query("UPDATE habits SET position = :position WHERE id = :id") suspend fun setPosition(id: String, position: Int)
    @Query("UPDATE habits SET state = :state WHERE id = :id") suspend fun setState(id: String, state: HabitState)
    @Insert suspend fun insertEvent(event: CompletionEventEntity): Long
    @Query("SELECT * FROM completion_events WHERE habitId = :habitId ORDER BY id") suspend fun getHistory(habitId: String): List<CompletionEventEntity>
    @Query("SELECT * FROM completion_events WHERE dateDay = :day ORDER BY id DESC") fun observeEventsOn(day: Long): Flow<List<CompletionEventEntity>>
}

@Database(
    entities = [ProfileEntity::class, MeasurementEntity::class, CategoryEntity::class,
        RoutineEntity::class, HabitEntity::class, HabitVersionEntity::class, CompletionEventEntity::class, ProgressPhoto::class],
    version = 2, exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class AesthoryDatabase : RoomDatabase() {
    abstract fun records(): AesthoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS progress_photos (id TEXT NOT NULL, profileId INTEGER NOT NULL, day INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, area TEXT NOT NULL, view TEXT NOT NULL, note TEXT NOT NULL, fileKey TEXT NOT NULL, sha256 TEXT NOT NULL, byteCount INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, deletedAtMillis INTEGER, PRIMARY KEY(id), FOREIGN KEY(profileId) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_progress_photos_profileId ON progress_photos(profileId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_progress_photos_fileKey ON progress_photos(fileKey)")
            }
        }
        fun open(context: Context) = Room.databaseBuilder(
            context.applicationContext, AesthoryDatabase::class.java, "aesthory.db",
        ).addMigrations(MIGRATION_1_2).build()
    }
}
