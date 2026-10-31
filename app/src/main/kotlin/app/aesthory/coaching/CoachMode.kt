package app.aesthory.coaching

enum class CoachMode(val displayName: String, val promptAsset: String) {
    SKIN("Skin", "coaching/skin.md"),
    HAIR("Hair", "coaching/hair.md"),
    FACIAL_AESTHETICS("Face & style", "coaching/facial_aesthetics.md"),
    PHYSIQUE("Body & fitness", "coaching/physique.md"),
    LOOKSMAX_MENTOR("A bit of everything", "coaching/looksmax_mentor.md"),
}
