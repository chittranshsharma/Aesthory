package app.aesthory.coaching

import app.aesthory.data.TodayHabit
import app.aesthory.profile.AgeBand
import app.aesthory.routines.CompletionStatus

data class StarterStep(val title: String, val instructions: String, val minutes: Int)
data class StarterRoutine(val id: String, val name: String, val description: String, val focus: CoachMode, val steps: List<StarterStep>) {
    val minutes get() = steps.sumOf { it.minutes }
}

object StarterRoutines {
    val all = listOf(
        StarterRoutine("skin-basics", "Skin, kept simple", "A gentle starting point. No ten-step routine.", CoachMode.SKIN, listOf(
            StarterStep("Cleanse gently", "Use a gentle cleanser, then pat dry. Keep any clinician-prescribed routine in place; skip a product that irritates you.", 2),
            StarterStep("Moisturize & protect", "Use a moisturizer that suits your skin. For daytime sun protection, follow your sunscreen label and reapply as directed. Add products slowly so you can notice irritation.", 2),
        )),
        StarterRoutine("hair-care", "A good hair day", "Care first. Then try one styling change.", CoachMode.HAIR, listOf(
            StarterStep("Check in with your hair", "Does your scalp or hair need a wash today? Match washing to your hair and scalp rather than forcing a daily wash. Condition when you shampoo; handle hair gently.", 2),
            StarterStep("Try your preferred style", "Spend a few minutes on a hairstyle you enjoy. Compare one change at a time; styling is personal, not a test of a perfect face.", 3),
        )),
        StarterRoutine("face-style", "Find your look", "Explore presentation without chasing a face score.", CoachMode.FACIAL_AESTHETICS, listOf(
            StarterStep("Choose one style experiment", "Pick one: hair shape, grooming, or glasses you already own. Try a reversible change. You do not need to buy anything or change your bones.", 3),
            StarterStep("Notice what you prefer", "Use the same mirror, lighting and distance. Note what you like before changing something else. Camera angles and shadows can change apparent proportions.", 1),
        )),
        StarterRoutine("daily-move", "Move a little", "An easy movement break alongside your own training.", CoachMode.PHYSIQUE, listOf(
            StarterStep("Take a comfortable movement break", "Walk or choose an accessible movement you enjoy at a comfortable pace. Adapt to your ability and existing care advice. This small break is not a complete training program.", 5),
            StarterStep("Plan your next workout", "Check your existing workout plan and recovery. Keep a rest day if that is what your plan calls for; there is no need to add an extra session.", 1),
        )),
        StarterRoutine("daily-reset", "The everyday reset", "Make tomorrow easier, without adding more pressure.", CoachMode.LOOKSMAX_MENTOR, listOf(
            StarterStep("Pick tomorrow's one priority", "Choose one manageable action for skin, hair, style or movement. Use what you already have; consistency does not require a shopping list.", 2),
            StarterStep("Set up your evening", "Put what you need for tomorrow within reach and choose a realistic wind-down time. Leave room for rest and things you enjoy.", 2),
        )),
    )
    fun suggested(focus: Set<CoachMode>) = all.sortedBy { if (it.focus in focus) 0 else 1 }
}

data class DailyGuidance(val title: String, val detail: String, val next: TodayHabit?, val done: Int, val total: Int)

/** Uses saved tasks, not fabricated streaks or a generated plan. Unknown duration stays unknown. */
fun guideToday(items: List<TodayHabit>, availableMinutes: Int = 5): DailyGuidance {
    require(availableMinutes > 0)
    val done = items.count { it.event?.status == CompletionStatus.DONE }
    val remaining = items.filter { it.event?.status !in setOf(CompletionStatus.DONE, CompletionStatus.SKIPPED) }
    val heads = remaining.distinctBy { it.habit.routineId }
    val next = heads.filter { it.habit.definition.estimatedMinutes?.let { minutes -> minutes <= availableMinutes } == true }
        .minByOrNull { it.habit.definition.estimatedMinutes!! }
        ?: heads.firstOrNull()
    val known = remaining.mapNotNull { it.habit.definition.estimatedMinutes }.sumOf { it.toLong() }
    return when {
        items.isEmpty() -> DailyGuidance("Start with something small", "Choose a ready-made routine below, or make your own. You can change it later.", null, done, 0)
        remaining.isEmpty() && done == items.size -> DailyGuidance("You showed up today.", "Everything on your list is done. Enjoy the rest of your day—more is not always better.", null, done, items.size)
        remaining.isEmpty() -> DailyGuidance("You're checked in.", "You've finished or skipped today's actions. Tomorrow is a fresh start.", null, done, items.size)
        known > 20 -> DailyGuidance("Keep today manageable", "Your unfinished actions add up to over 20 minutes. Start with one; you can pause habits that don't fit.", next, done, items.size)
        else -> DailyGuidance("Your next good move", next!!.habit.definition.estimatedMinutes?.let { "About $it min. One action at a time is enough." } ?: "Take this at your own pace. No time estimate has been added.", next, done, items.size)
    }
}

enum class GuideTopic(val title: String) { SKIN("Skin"), HAIR("Hair"), FACE("Face & style"), BODY("Fitness"), FORUM("Looksmaxing terms"), PROCEDURES("Procedures") }
data class GuideSource(val name: String, val url: String)
data class CoachGuide(val id: String, val topic: GuideTopic, val title: String, val summary: String, val actions: List<String>, val note: String, val sources: List<GuideSource> = emptyList(), val adultOnly: Boolean = false)

object CoachLibrary {
    private val skin = GuideSource("American Academy of Dermatology · skin basics", "https://www.aad.org/public/everyday-care/skin-care-basics/care/skin-care-budget")
    private val hair = GuideSource("American Academy of Dermatology · hair care", "https://www.aad.org/public/everyday-care/hair-scalp-care/hair/healthy-hair-tips")
    private val jaw = GuideSource("American Association of Orthodontists · mewing", "https://aaoinfo.org/whats-trending/is-mewing-bad-for-you/")
    private val surgery = GuideSource("American Society of Plastic Surgeons · patient safety", "https://www.plasticsurgery.org/patient-safety")
    private val movement = GuideSource("CDC · adult activity guidance", "https://www.cdc.gov/physical-activity-basics/guidelines/adults.html")
    val all = listOf(
        CoachGuide("skin-start", GuideTopic.SKIN, "Where do I start with skincare?", "Start with cleansing, moisture and sun protection.", listOf("Keep the routine short and affordable.", "Use products you tolerate; add one change at a time."), "General care education. Persistent or painful skin problems need individual care.", listOf(skin)),
        CoachGuide("hair-start", GuideTopic.HAIR, "How do I care for my hair?", "Your scalp and hair needs matter more than a fixed wash-day rule.", listOf("Match washing to your hair and scalp.", "Use conditioner when you shampoo, and handle hair gently."), "This is care guidance, not a diagnosis or a hair-regrowth promise.", listOf(hair)),
        CoachGuide("style", GuideTopic.FACE, "What can I change without a procedure?", "Explore hair, grooming and frames as personal style choices.", listOf("Choose one reversible experiment.", "Compare it in the same lighting and distance.", "Keep what you prefer, rather than chasing an ideal ratio."), "Presentation advice, not a medical finding. A photo cannot reveal hidden bone anatomy."),
        CoachGuide("photo", GuideTopic.FACE, "Why do I look different in every photo?", "Angle, distance, expression and lighting change how a face appears.", listOf("Keep the camera level and face it straight on.", "Use even lighting, a relaxed expression and the same distance.", "Compare similar photos; avoid reading one changed shadow as a bone change."), "Photo measurements describe that image. They are not an objective attractiveness score."),
        CoachGuide("fitness", GuideTopic.BODY, "How do I make fitness sustainable?", "Build activity around what you can repeat and recover from.", listOf("Start with manageable movement and build gradually.", "For adults, general guidance includes aerobic activity and muscle strengthening; a short starter routine is only one small part.", "Keep recovery and your existing training plan in view."), "General adult guidance is not a personalized exercise prescription.", listOf(movement)),
        CoachGuide("softmax", GuideTopic.FORUM, "Softmaxing: the everyday changes", "Forum shorthand for changes such as grooming, skin care, hair, clothing and fitness.", listOf("Choose a change that fits your own goal, time and budget.", "Track the action and what you prefer; don't promise a fixed transformation."), "Forum vocabulary, not a scientific treatment category. Individual claims still need evidence."),
        CoachGuide("psl", GuideTopic.FORUM, "PSL, ratios and blackpill claims", "PSL ratings and blackpill beliefs are forum frameworks, not validated measures of your worth or future.", listOf("Use facial terms to ask clearer questions, not to turn an opinion into a diagnosis.", "A face score cannot establish your prospects, personality or how others will treat you.", "Use measurable habits and your own preferences as the goal."), "Vocabulary and claim context. Aesthory does not endorse deterministic claims or rank people."),
        CoachGuide("mewing", GuideTopic.FORUM, "Can mewing change my jaw?", "Jaw-sculpting promises from tongue posture are not supported by good evidence.", listOf("Don't force your tongue or jaw to chase a bone change.", "Ask a dental professional about bite, jaw or breathing concerns rather than self-correcting them."), "Orthodontic assessment is different from an internet exercise trend.", listOf(jaw)),
        CoachGuide("hardmax", GuideTopic.PROCEDURES, "Hardmaxing: understand the trade-offs", "Forum shorthand often used for invasive appearance changes. It isn't a medical diagnosis or a treatment plan.", listOf("Define your concern and ask a qualified clinician about options, alternatives and doing nothing.", "Ask about risks, recovery, cost, realistic outcomes and follow-up.", "Don't select an operation from a selfie, forum rating or app report."), "Education for adults, not a procedure recommendation. Terminology varies between communities.", listOf(surgery), true),
        CoachGuide("procedure-questions", GuideTopic.PROCEDURES, "Questions before a cosmetic procedure", "A consultation should help you understand suitability and risk, not rush a decision.", listOf("What qualifications and experience does the clinician have?", "What are the risks, alternatives, recovery and follow-up arrangements?", "What outcome is realistic for my situation, and what can't this procedure change?"), "No selfie-based surgical plan. A clinician must assess your individual situation.", listOf(surgery), true),
        CoachGuide("jaw-terms", GuideTopic.FORUM, "Jaw, cheekbones and eye-area terms", "Canthal tilt refers to eye-corner angle; gonial angle to a jaw angle; maxilla to the upper jaw. Forum use often blurs photo appearance with anatomy.", listOf("Check whether a claim is about a visible photo feature or actual anatomy.", "Avoid treating a camera-based ratio as a clinical measurement.", "Take functional concerns to an appropriate qualified professional."), "A vocabulary guide, not an anatomy assessment or an ideal-face checklist."),
    )
    fun find(query: String, topic: GuideTopic?, age: AgeBand): List<CoachGuide> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return all.filter { guide ->
            (!guide.adultOnly || age == AgeBand.ADULT) && (topic == null || guide.topic == topic) &&
                words.all { word -> (guide.title + " " + guide.summary + " " + guide.actions.joinToString(" ")).lowercase().contains(word) }
        }
    }
}
