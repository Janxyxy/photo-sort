package com.fotosort

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A set of items to sort. Usually a MediaStore bucket; a [virtual] folder is a category
 * (screenshots, receipts, ...) whose items come from any bucket.
 */
data class Folder(
    val id: Long,
    val name: String,
    val coverUri: Uri,
    val mediaIds: List<Long>,
    val virtual: Boolean = false,
)

data class Media(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val size: Long,
    /** 0 when the file has no capture date (e.g. downloads) */
    val takenMs: Long,
    val addedMs: Long,
    val durationMs: Long,
) {
    val displayDateMs: Long get() = if (takenMs > 0) takenMs else addedMs
}

enum class SortOrder(val label: String) {
    NewestFirst("Newest first"),
    OldestFirst("Oldest first"),
    LargestFirst("Largest first"),
}

/**
 * Reads photos and videos from MediaStore. A "folder" is a MediaStore bucket (the directory a file lives in).
 * Queries go through the Files table so photos and videos come back in one sorted list; each item then
 * gets its Images or Video URI, which is what the trash request expects.
 */
object MediaRepository {
    private val files: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val images: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val videos: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val mediaProjection = arrayOf(
        FileColumns._ID,
        FileColumns.MEDIA_TYPE,
        FileColumns.SIZE,
        FileColumns.DATE_TAKEN,
        FileColumns.DATE_ADDED,
        FileColumns.DURATION,
    )

    fun uriFor(id: Long, isVideo: Boolean): Uri = ContentUris.withAppendedId(if (isVideo) videos else images, id)

    private fun typeSelection(includeVideos: Boolean) =
        if (includeVideos) {
            "${FileColumns.MEDIA_TYPE} IN (${FileColumns.MEDIA_TYPE_IMAGE}, ${FileColumns.MEDIA_TYPE_VIDEO})"
        } else {
            "${FileColumns.MEDIA_TYPE} = ${FileColumns.MEDIA_TYPE_IMAGE}"
        }

    private fun orderBy(sort: SortOrder) = when (sort) {
        SortOrder.NewestFirst -> "${FileColumns.DATE_TAKEN} DESC, ${FileColumns.DATE_ADDED} DESC"
        SortOrder.OldestFirst -> "${FileColumns.DATE_TAKEN} ASC, ${FileColumns.DATE_ADDED} ASC"
        SortOrder.LargestFirst -> "${FileColumns.SIZE} DESC"
    }

    suspend fun loadFolders(context: Context, includeVideos: Boolean): List<Folder> = withContext(Dispatchers.IO) {
        class Bucket(val name: String, val coverUri: Uri, val ids: MutableList<Long> = mutableListOf())

        // LinkedHashMap keeps the folder holding the newest item first
        val buckets = LinkedHashMap<Long, Bucket>()
        val projection = arrayOf(
            FileColumns._ID,
            FileColumns.MEDIA_TYPE,
            FileColumns.BUCKET_ID,
            FileColumns.BUCKET_DISPLAY_NAME,
        )
        context.contentResolver.query(
            files, projection, typeSelection(includeVideos), null, orderBy(SortOrder.NewestFirst),
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
            val bucketCol = c.getColumnIndexOrThrow(FileColumns.BUCKET_ID)
            val nameCol = c.getColumnIndexOrThrow(FileColumns.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val bucket = buckets.getOrPut(c.getLong(bucketCol)) {
                    val isVideo = c.getInt(typeCol) == FileColumns.MEDIA_TYPE_VIDEO
                    Bucket(c.getString(nameCol) ?: "Unknown", uriFor(id, isVideo))
                }
                bucket.ids += id
            }
        }
        buckets.map { (id, bucket) -> Folder(id, bucket.name, bucket.coverUri, bucket.ids) }
    }

    suspend fun loadMedia(
        context: Context,
        bucketId: Long,
        includeVideos: Boolean,
        sort: SortOrder,
    ): List<Media> = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            files,
            mediaProjection,
            "${FileColumns.BUCKET_ID} = ? AND ${typeSelection(includeVideos)}",
            arrayOf(bucketId.toString()),
            orderBy(sort),
        )?.use { it.readMedia() } ?: emptyList()
    }

    /** Every photo on the device, newest first. */
    suspend fun loadAllImages(context: Context): List<Media> = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            files,
            mediaProjection,
            typeSelection(includeVideos = false),
            null,
            orderBy(SortOrder.NewestFirst),
        )?.use { it.readMedia() } ?: emptyList()
    }

    /** Items of a [Folder]: a bucket query, or for a virtual folder its ids sorted like a bucket would be. */
    suspend fun loadFolder(context: Context, folder: Folder, includeVideos: Boolean, sort: SortOrder): List<Media> {
        if (!folder.virtual) return loadMedia(context, folder.id, includeVideos, sort)
        val items = loadByIds(context, folder.mediaIds).filter { includeVideos || !it.isVideo }
        return when (sort) {
            SortOrder.NewestFirst -> items.sortedByDescending { it.displayDateMs }
            SortOrder.OldestFirst -> items.sortedBy { it.displayDateMs }
            SortOrder.LargestFirst -> items.sortedByDescending { it.size }
        }
    }

    /** Returns the items of [ids] that still exist (and are not already trashed). */
    suspend fun loadByIds(context: Context, ids: Collection<Long>): List<Media> = withContext(Dispatchers.IO) {
        ids.chunked(500).flatMap { chunk ->
            context.contentResolver.query(
                files,
                mediaProjection,
                "${FileColumns._ID} IN (${chunk.joinToString(",")}) AND ${typeSelection(includeVideos = true)}",
                null,
                orderBy(SortOrder.NewestFirst),
            )?.use { it.readMedia() } ?: emptyList()
        }
    }

    private fun Cursor.readMedia(): List<Media> {
        val idCol = getColumnIndexOrThrow(FileColumns._ID)
        val typeCol = getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
        val sizeCol = getColumnIndexOrThrow(FileColumns.SIZE)
        val takenCol = getColumnIndexOrThrow(FileColumns.DATE_TAKEN)
        val addedCol = getColumnIndexOrThrow(FileColumns.DATE_ADDED)
        val durationCol = getColumnIndexOrThrow(FileColumns.DURATION)
        val result = mutableListOf<Media>()
        while (moveToNext()) {
            val id = getLong(idCol)
            val isVideo = getInt(typeCol) == FileColumns.MEDIA_TYPE_VIDEO
            result += Media(
                id = id,
                uri = uriFor(id, isVideo),
                isVideo = isVideo,
                size = getLong(sizeCol),
                takenMs = getLong(takenCol),
                addedMs = getLong(addedCol) * 1000,
                durationMs = getLong(durationCol),
            )
        }
        return result
    }
}
