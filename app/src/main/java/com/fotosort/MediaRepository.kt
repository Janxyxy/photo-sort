package com.fotosort

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Folder(val id: Long, val name: String, val coverUri: Uri, val photoIds: List<Long>)

data class Photo(val id: Long, val uri: Uri)

/** Reads photos from MediaStore. A "folder" is a MediaStore bucket (the directory a photo lives in). */
object MediaRepository {
    private val collection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private const val NEWEST_FIRST =
        "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"

    fun uriFor(id: Long): Uri = ContentUris.withAppendedId(collection, id)

    suspend fun loadFolders(context: Context): List<Folder> = withContext(Dispatchers.IO) {
        // LinkedHashMap keeps the folder holding the newest photo first
        val buckets = LinkedHashMap<Long, Pair<String, MutableList<Long>>>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        context.contentResolver.query(collection, projection, null, null, NEWEST_FIRST)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: "Unknown"
                buckets.getOrPut(c.getLong(bucketCol)) { name to mutableListOf() }.second += c.getLong(idCol)
            }
        }
        buckets.map { (id, value) -> Folder(id, value.first, uriFor(value.second.first()), value.second) }
    }

    suspend fun loadPhotos(context: Context, bucketId: Long): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.BUCKET_ID} = ?",
            arrayOf(bucketId.toString()),
            NEWEST_FIRST,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                photos += Photo(id, uriFor(id))
            }
        }
        photos
    }

    /** Returns the subset of [ids] that still exist (and are not already trashed). */
    suspend fun existingIds(context: Context, ids: Collection<Long>): List<Long> = withContext(Dispatchers.IO) {
        ids.chunked(500).flatMap { chunk ->
            val found = mutableListOf<Long>()
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media._ID} IN (${chunk.joinToString(",")})",
                null,
                null,
            )?.use { c -> while (c.moveToNext()) found += c.getLong(0) }
            found
        }
    }
}
