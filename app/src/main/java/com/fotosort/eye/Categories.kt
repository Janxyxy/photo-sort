package com.fotosort.eye

import com.fotosort.Folder
import com.fotosort.Media
import kotlin.math.absoluteValue

/** A category matches a photo that has any of its Eye [tags]. */
data class Category(val title: String, val tags: List<String>) {
    constructor(title: String, vararg tags: String) : this(title, tags.toList())
}

data class CategorySection(val title: String, val folders: List<Folder>)

private val cleanUp = listOf(
    Category("Screenshots", "screenshot"),
    Category("Blurry", "blurry"),
    Category("Eyes closed", "eyes_closed"),
    Category("Accidental shots", "accidental", "unusable", "technical_flaw"),
    Category("Test and repeat shots", "test_or_duplicate_shot"),
    Category("Bad framing or light", "bad_framing", "bad_lighting"),
    Category("Forgettable", "forgettable", "low_content"),
    Category("Photos of screens", "photo_of_screen"),
    Category("Memes and graphics", "meme_or_graphic"),
)

private val documents = listOf(
    Category("Receipts", "receipt"),
    Category("Documents", "document", "id_card"),
    Category("Whiteboards", "whiteboard"),
    Category("Slides and charts", "chart_or_slide"),
    Category("Maps", "map"),
    Category("Reminders", "shopping_reference", "repair_reference", "medical_reference"),
)

private val people = listOf(
    Category("Selfies", "selfie"),
    Category("Portraits", "portrait"),
    Category("Group shots", "group_shot"),
    Category("Everyone smiling", "everyone_smiling"),
)

/** Tags that describe the photo rather than what's in it; everything else Eye reports is a scene. */
private val notScenes = (cleanUp + documents + people).flatMap { it.tags }.toSet() + setOf(
    "photo", "other", "reference", "illustration_or_art", "keeper", "sharp", "bokeh", "rotated", "peak_moment",
    "people", "candid", "everyone_looking", "smiling", "looking_at_camera", "looking_away", "happy", "surprised",
    "sensitive", "nsfw_explicit", "nsfw_suggestive", "blood_injury", "medical_hospital", "funeral",
    "accident_disaster", "violence_protest", "weapon",
)

/** Captures of something else; their scene tags describe what's on the screen or page and add noise. */
private val notPhotos = setOf(
    "screenshot", "photo_of_screen", "document", "receipt", "id_card", "whiteboard", "chart_or_slide", "map",
    "meme_or_graphic", "illustration_or_art",
)

private const val MAX_SCENES = 40
private const val MIN_SCENE_PHOTOS = 2

/**
 * Builds the category sections from the analyzed photos. [photos] is the device's library, newest
 * first, so each category's cover is its newest photo; photos deleted since the scan drop out here.
 */
fun buildSections(photos: List<Media>, tags: Map<Long, Map<String, Float>>): List<CategorySection> {
    fun folderFor(category: Category, photosOnly: Boolean = false): Folder? {
        val matches = photos.filter { photo ->
            val photoTags = tags[photo.id]?.keys ?: return@filter false
            photoTags.any { it in category.tags } && !(photosOnly && photoTags.any { it in notPhotos })
        }
        if (matches.isEmpty()) return null
        return Folder(
            // negative so it never collides with a MediaStore bucket id
            id = -(category.title.hashCode().toLong().absoluteValue + 1),
            name = category.title,
            coverUri = matches.first().uri,
            mediaIds = matches.map { it.id },
            virtual = true,
        )
    }

    val sceneCounts = HashMap<String, Int>()
    for (photo in photos) {
        val photoTags = tags[photo.id]?.keys ?: continue
        if (photoTags.any { it in notPhotos }) continue
        photoTags.forEach { if (it !in notScenes) sceneCounts.merge(it, 1, Int::plus) }
    }
    val scenes = sceneCounts.entries
        .filter { it.value >= MIN_SCENE_PHOTOS }
        .sortedByDescending { it.value }
        .take(MAX_SCENES)
        .map { Category(sceneTitle(it.key), it.key) }

    return listOf(
        CategorySection("Clean up", cleanUp.mapNotNull(::folderFor)),
        CategorySection("Documents", documents.mapNotNull(::folderFor)),
        CategorySection("People", people.mapNotNull(::folderFor)),
        CategorySection("In your photos", scenes.mapNotNull { folderFor(it, photosOnly = true) }),
    ).filter { it.folders.isNotEmpty() }
}

/** `wave_noun` -> "Wave", `golden_retriever` -> "Golden retriever". */
private fun sceneTitle(id: String): String =
    id.removeSuffix("_noun").removeSuffix("_object").removeSuffix("_structure").removeSuffix("_machine")
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
