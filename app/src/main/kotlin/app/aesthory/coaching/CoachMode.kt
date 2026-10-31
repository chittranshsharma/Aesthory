package app.aesthory.coaching

enum class CoachMode(val displayName: String, val promptAsset: String) {
    SKIN("Skin", "coaching/skin.md"),
    HAIR("Hair & Scalp", "coaching/hair.md"),
    FACIAL_AESTHETICS("Facial Aesthetics", "coaching/facial_aesthetics.md"),
    PHYSIQUE("Physique", "coaching/physique.md"),
    LOOKSMAX_MENTOR("Looksmax Mentor", "coaching/looksmax_mentor.md"),
}
