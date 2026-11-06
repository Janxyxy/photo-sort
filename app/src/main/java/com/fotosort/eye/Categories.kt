package com.fotosort.eye

import com.fotosort.Folder
import com.fotosort.Media
import com.fotosort.ui.similarGroups
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

const val SECTION_CLEAN_UP = "Clean up"

/** Tags kept out of the suggestions, so they never pop up unasked; they can still be searched. */
private val sensitive = setOf(
    "sensitive", "nsfw_explicit", "nsfw_suggestive", "blood_injury", "medical_hospital", "funeral",
    "accident_disaster", "violence_protest", "weapon",
)

/** Tags that describe the photo rather than what's in it; everything else Eye reports is a scene. */
private val notScenes = (cleanUp + documents + people).flatMap { it.tags }.toSet() + sensitive + setOf(
    "photo", "other", "reference", "illustration_or_art", "keeper", "sharp", "bokeh", "rotated", "peak_moment",
    "people", "candid", "everyone_looking", "smiling", "looking_at_camera", "looking_away", "happy", "surprised",
    // bursts are found by capture time instead, see similarFolder
    "test_or_duplicate_shot",
)

/** Captures of something else; their scene tags describe what's on the screen or page and add noise. */
private val notPhotos = setOf(
    "screenshot", "photo_of_screen", "document", "receipt", "id_card", "whiteboard", "chart_or_slide", "map",
    "meme_or_graphic", "illustration_or_art",
)

private const val MAX_SCENES = 40

/** Smaller categories are hidden: a "folder" of one or two photos isn't worth opening. */
private const val MIN_CATEGORY_PHOTOS = 3

/** Tags too generic to be worth searching for. */
private val notSearchable = setOf("photo", "other", "keeper", "sharp", "people")

private val fixedCategories = cleanUp + documents + people

/**
 * Builds the category sections from the analyzed photos. [photos] is the device's library, newest
 * first, so each category's cover is its newest photo; photos deleted since the scan drop out here.
 */
fun buildSections(photos: List<Media>, tags: Map<Long, Map<String, Float>>): List<CategorySection> {
    fun folderFor(category: Category, photosOnly: Boolean = false): Folder? =
        matchingFolder(photos, tags, category, photosOnly)?.takeIf { it.mediaIds.size >= MIN_CATEGORY_PHOTOS }

    val scenes = sceneCategories(photos, tags, photosOnly = true) { it !in notScenes }
        .filter { it.second >= MIN_CATEGORY_PHOTOS }
        .take(MAX_SCENES)
        .map { it.first }

    return listOf(
        CategorySection(SECTION_CLEAN_UP, listOfNotNull(similarFolder(photos)) + cleanUp.mapNotNull { folderFor(it) }),
        CategorySection("Documents", documents.mapNotNull { folderFor(it) }),
        CategorySection("People", people.mapNotNull { folderFor(it) }),
        CategorySection("In your photos", scenes.mapNotNull { folderFor(it, photosOnly = true) }),
    ).filter { it.folders.isNotEmpty() }
}

/**
 * Categories and tags whose name contains [query], biggest first. Unlike [buildSections] this
 * shows small matches too, since the user asked for them by name.
 */
fun searchTags(photos: List<Media>, tags: Map<Long, Map<String, Float>>, query: String): List<Folder> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val fixedTags = fixedCategories.flatMap { it.tags }.toSet()
    val fixed = fixedCategories.filter { c -> c.title.lowercase().contains(q) || c.tags.any { tagName(it).contains(q) } }
    val other = sceneCategories(photos, tags, photosOnly = false) { it !in fixedTags && it !in notSearchable }
        .map { it.first }
        .filter { c -> c.title.lowercase().contains(q) || c.tags.any { tagName(it).contains(q) } }
    return (fixed + other)
        .mapNotNull { matchingFolder(photos, tags, it, photosOnly = false) }
        .sortedByDescending { it.mediaIds.size }
}

/** The most common tags in the library, for search suggestions. */
fun popularTags(photos: List<Media>, tags: Map<Long, Map<String, Float>>, limit: Int = 16): List<String> =
    sceneCategories(photos, tags, photosOnly = false) { it !in notSearchable && it !in sensitive }
        .take(limit)
        .map { it.first.title }

/**
 * Every photo that is part of a burst (shots seconds apart), library-wide. Needs no analysis, so
 * it shows up before the first scan; the swipe screen offers to compare each burst.
 */
private fun similarFolder(photos: List<Media>): Folder? {
    val grouped = similarGroups(photos).keys
    val matches = photos.filter { it.id in grouped }
    if (matches.size < MIN_CATEGORY_PHOTOS) return null
    return Folder(
        id = -(SIMILAR_TITLE.hashCode().toLong().absoluteValue + 1),
        name = SIMILAR_TITLE,
        coverUri = matches.first().uri,
        mediaIds = matches.map { it.id },
        virtual = true,
    )
}

private const val SIMILAR_TITLE = "Similar photos"

private fun matchingFolder(
    photos: List<Media>,
    tags: Map<Long, Map<String, Float>>,
    category: Category,
    photosOnly: Boolean,
): Folder? {
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

/**
 * One category per distinct tag title (`wave` and `wave_noun` both become "Wave", so they are
 * merged instead of producing two folders with the same id), with its photo count, biggest first.
 */
private fun sceneCategories(
    photos: List<Media>,
    tags: Map<Long, Map<String, Float>>,
    photosOnly: Boolean,
    include: (String) -> Boolean,
): List<Pair<Category, Int>> {
    val byTitle = HashMap<String, MutableSet<String>>()
    val counts = HashMap<String, Int>()
    for (photo in photos) {
        val photoTags = tags[photo.id]?.keys ?: continue
        if (photosOnly && photoTags.any { it in notPhotos }) continue
        photoTags.filter(include).map { tag ->
            sceneTitle(tag).also { byTitle.getOrPut(it) { HashSet() } += tag }
        }.toSet().forEach { counts.merge(it, 1, Int::plus) }
    }
    return counts.entries
        .sortedByDescending { it.value }
        .map { Category(it.key, byTitle.getValue(it.key).toList()) to it.value }
}

private fun tagName(tag: String) = tag.replace('_', ' ')

/** `wave_noun` -> "Wave", `golden_retriever` -> "Golden retriever". */
private fun sceneTitle(id: String): String =
    id.removeSuffix("_noun").removeSuffix("_object").removeSuffix("_structure").removeSuffix("_machine")
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
