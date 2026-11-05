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
import app.aesthory.library.*
import app.aesthory.timing.*
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
    @Query("SELECT * FROM weekly_targets WHERE startDay = :day") fun observeWeeklyTarget(day: Long): Flow<app.aesthory.progress.WeeklyTarget?>
    @Query("SELECT * FROM weekly_targets") suspend fun allWeeklyTargets(): List<app.aesthory.progress.WeeklyTarget>
    @Upsert suspend fun saveWeeklyTarget(target: app.aesthory.progress.WeeklyTarget)
    @Query("SELECT e.id, e.habitId, e.dateDay, e.status, e.value, e.timedSeconds, v.tracking_kind AS kind FROM completion_events e JOIN habit_versions v ON e.habitId = v.habitId AND e.definitionVersion = v.version WHERE e.dateDay BETWEEN :start AND :end")
    fun observeWeeklyLogs(start: Long, end: Long): Flow<List<app.aesthory.progress.WeeklyLog>>

    @Query("SELECT * FROM active_timer WHERE id = 1") fun observeTimer(): Flow<ActiveTimer?>
    @Query("SELECT * FROM active_timer WHERE id = 1") suspend fun getTimer(): ActiveTimer?
    @Query("SELECT * FROM active_timer") suspend fun allTimers(): List<ActiveTimer>
    @Upsert suspend fun saveTimer(timer: ActiveTimer)
    @Query("DELETE FROM active_timer") suspend fun clearTimer()
    @Query("SELECT * FROM habit_reminders ORDER BY habitId") fun observeReminders(): Flow<List<HabitReminder>>
    @Query("SELECT * FROM habit_reminders") suspend fun allReminders(): List<HabitReminder>
    @Query("SELECT * FROM habit_reminders WHERE habitId = :id") suspend fun getReminder(id: String): HabitReminder?
    @Upsert suspend fun saveReminder(reminder: HabitReminder)
    @Query("SELECT * FROM reminder_preferences WHERE id = 1") fun observeReminderPreferences(): Flow<ReminderPreferences?>
    @Query("SELECT * FROM reminder_preferences WHERE id = 1") suspend fun getReminderPreferences(): ReminderPreferences?
    @Query("SELECT * FROM reminder_preferences") suspend fun allReminderPreferences(): List<ReminderPreferences>
    @Upsert suspend fun saveReminderPreferences(preferences: ReminderPreferences)
    @Query("SELECT * FROM reminder_runtime WHERE habitId = :id") suspend fun reminderRuntime(id: String): ReminderRuntime?
    @Query("SELECT * FROM reminder_runtime") suspend fun allReminderRuntime(): List<ReminderRuntime>
    @Query("SELECT v.*, h.position, h.state, h.routineId, r.archived AS routineArchived FROM habit_versions v JOIN habits h ON v.habitId = h.id AND v.version = h.currentVersion JOIN routines r ON r.id = h.routineId ORDER BY r.createdAtMillis, r.id, h.position, h.id")
    fun observeReminderActions(): Flow<List<ReminderAction>>
    @Upsert suspend fun saveReminderRuntime(runtime: ReminderRuntime)
    @Query("DELETE FROM reminder_runtime") suspend fun clearReminderRuntime()
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE, id") fun observeProducts(): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products") suspend fun allProducts(): List<ProductEntity>
    @Query("SELECT * FROM products WHERE id = :id") suspend fun getProduct(id: String): ProductEntity?
    @Upsert suspend fun saveProduct(product: ProductEntity)
    @Query("SELECT * FROM personal_references ORDER BY title COLLATE NOCASE, id") fun observeReferences(): Flow<List<ReferenceEntity>>
    @Query("SELECT * FROM personal_references") suspend fun allReferences(): List<ReferenceEntity>
    @Query("SELECT * FROM personal_references WHERE id = :id") suspend fun getReference(id: String): ReferenceEntity?
    @Upsert suspend fun saveReference(reference: ReferenceEntity)
    @Insert suspend fun insertObservation(observation: ProductObservation)
    @Query("SELECT * FROM product_observations ORDER BY day DESC, createdAtMillis DESC, id") fun observeObservations(): Flow<List<ProductObservation>>
    @Query("SELECT * FROM product_observations WHERE productId = :id ORDER BY day DESC, createdAtMillis DESC, id LIMIT 100") fun observationsFor(id: String): Flow<List<ProductObservation>>
    @Query("SELECT * FROM product_observations") suspend fun allObservations(): List<ProductObservation>
    @Insert suspend fun insertProductLink(link: HabitProductLink)
    @Insert suspend fun insertReferenceLink(link: HabitReferenceLink)
    @Query("SELECT * FROM habit_product_links WHERE habitId = :id AND version = :version ORDER BY name, productId") fun productLinks(id: String, version: Int): Flow<List<HabitProductLink>>
    @Query("SELECT * FROM habit_reference_links WHERE habitId = :id AND version = :version ORDER BY title, referenceId") fun referenceLinks(id: String, version: Int): Flow<List<HabitReferenceLink>>
    @Query("SELECT * FROM habit_product_links") suspend fun allProductLinks(): List<HabitProductLink>
    @Query("SELECT * FROM habit_reference_links") suspend fun allReferenceLinks(): List<HabitReferenceLink>
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
    @Query("SELECT * FROM completion_events WHERE habitId = :habitId ORDER BY id DESC LIMIT 200") suspend fun getRecentHistory(habitId: String): List<CompletionEventEntity>
    @Query("SELECT * FROM completion_events WHERE habitId = :id AND dateDay BETWEEN :fromDay AND :untilDay ORDER BY id DESC") suspend fun eventsBetween(id: String, fromDay: Long, untilDay: Long): List<CompletionEventEntity>
    @Query("SELECT * FROM completion_events WHERE dateDay = :day ORDER BY id DESC") fun observeEventsOn(day: Long): Flow<List<CompletionEventEntity>>
}

@Database(
    entities = [ProfileEntity::class, MeasurementEntity::class, CategoryEntity::class,
        RoutineEntity::class, HabitEntity::class, HabitVersionEntity::class, CompletionEventEntity::class, ProgressPhoto::class,
        ProductEntity::class, ReferenceEntity::class, ProductObservation::class, HabitProductLink::class, HabitReferenceLink::class,
        app.aesthory.progress.WeeklyTarget::class, ActiveTimer::class, HabitReminder::class, ReminderPreferences::class, ReminderRuntime::class],
    version = 5, exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class AesthoryDatabase : RoomDatabase() {
    abstract fun records(): AesthoryDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE weekly_targets (startDay INTEGER NOT NULL PRIMARY KEY, completed INTEGER, minutes INTEGER)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE completion_events ADD COLUMN timedSeconds INTEGER DEFAULT NULL")
                db.execSQL("CREATE TABLE active_timer (id INTEGER NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, habitId TEXT NOT NULL, definitionVersion INTEGER NOT NULL, day INTEGER NOT NULL, accumulatedMillis INTEGER NOT NULL, startedElapsedMillis INTEGER, startedWallMillis INTEGER, boot INTEGER NOT NULL, goalMillis INTEGER, goalAlerted INTEGER NOT NULL, recovery TEXT NOT NULL, FOREIGN KEY(habitId, definitionVersion) REFERENCES habit_versions(habitId, version) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX index_active_timer_habitId_definitionVersion ON active_timer(habitId, definitionVersion)")
                db.execSQL("CREATE TABLE habit_reminders (habitId TEXT NOT NULL PRIMARY KEY, minuteOfDay INTEGER NOT NULL, enabled INTEGER NOT NULL, FOREIGN KEY(habitId) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE TABLE reminder_preferences (id INTEGER NOT NULL PRIMARY KEY, quietEnabled INTEGER NOT NULL, quietStart INTEGER NOT NULL, quietEnd INTEGER NOT NULL, paused INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE reminder_runtime (habitId TEXT NOT NULL PRIMARY KEY, nextAtMillis INTEGER, nextDay INTEGER, token TEXT NOT NULL, snoozed INTEGER NOT NULL, deliveredDay INTEGER, deliveredToken TEXT, FOREIGN KEY(habitId) REFERENCES habit_reminders(habitId) ON UPDATE NO ACTION ON DELETE NO ACTION)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habit_versions ADD COLUMN purpose TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habit_versions ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE habit_versions ADD COLUMN shortInstructions TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE products (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, brand TEXT NOT NULL, kind TEXT NOT NULL, size TEXT NOT NULL, labelNotes TEXT NOT NULL, notes TEXT NOT NULL, url TEXT NOT NULL, openedDay INTEGER, expiryDay INTEGER, periodMonths INTEGER, stock TEXT NOT NULL, archived INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE personal_references (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, content TEXT NOT NULL, url TEXT NOT NULL, sourceDay INTEGER, archived INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE product_observations (id TEXT NOT NULL PRIMARY KEY, productId TEXT NOT NULL, productName TEXT NOT NULL, day INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, note TEXT NOT NULL, FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX index_product_observations_productId ON product_observations(productId)")
                db.execSQL("CREATE TABLE habit_product_links (habitId TEXT NOT NULL, version INTEGER NOT NULL, productId TEXT NOT NULL, name TEXT NOT NULL, brand TEXT NOT NULL, labelNotes TEXT NOT NULL, usageNote TEXT NOT NULL, PRIMARY KEY(habitId, version, productId), FOREIGN KEY(habitId, version) REFERENCES habit_versions(habitId, version) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX index_habit_product_links_productId ON habit_product_links(productId)")
                db.execSQL("CREATE TABLE habit_reference_links (habitId TEXT NOT NULL, version INTEGER NOT NULL, referenceId TEXT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, url TEXT NOT NULL, sourceDay INTEGER, PRIMARY KEY(habitId, version, referenceId), FOREIGN KEY(habitId, version) REFERENCES habit_versions(habitId, version) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(referenceId) REFERENCES personal_references(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX index_habit_reference_links_referenceId ON habit_reference_links(referenceId)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS progress_photos (id TEXT NOT NULL, profileId INTEGER NOT NULL, day INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, area TEXT NOT NULL, view TEXT NOT NULL, note TEXT NOT NULL, fileKey TEXT NOT NULL, sha256 TEXT NOT NULL, byteCount INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, deletedAtMillis INTEGER, PRIMARY KEY(id), FOREIGN KEY(profileId) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_progress_photos_profileId ON progress_photos(profileId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_progress_photos_fileKey ON progress_photos(fileKey)")
            }
        }
        fun open(context: Context) = Room.databaseBuilder(
            context.applicationContext, AesthoryDatabase::class.java, "aesthory.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
    }
}
