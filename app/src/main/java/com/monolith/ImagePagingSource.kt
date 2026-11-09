package com.monolith

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImagePagingSource(
    private val contentResolver: ContentResolver,
    private val searchQuery: String? = null,
    private val screenshotsOnly: Boolean = false,
    private val bucketId: String? = null
) : PagingSource<Int, Uri>() {

    private val collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            invalidate()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            invalidate()
        }
    }

    init {
        contentResolver.registerContentObserver(collectionUri, true, observer)

        registerInvalidatedCallback {
            try {
                contentResolver.unregisterContentObserver(observer)
            } catch (_: IllegalArgumentException) {
                // Already unregistered.
            }
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Uri> =
        withContext(Dispatchers.IO) {
            val offset = params.key ?: 0
            val limit = params.loadSize.coerceAtLeast(1)
            val clauses = mutableListOf<String>()
            val selectionArgs = mutableListOf<String>()

            searchQuery
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { query ->
                    clauses += "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                    selectionArgs += "%$query%"
                }

            bucketId
                ?.takeIf { it.isNotBlank() }
                ?.let { id ->
                    clauses += "${MediaStore.Images.Media.BUCKET_ID} = ?"
                    selectionArgs += id
                }

            if (screenshotsOnly) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    clauses += "(" +
                        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR " +
                        "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?" +
                        ")"
                    selectionArgs += "%Screenshot%"
                    selectionArgs += "%Screenshot%"
                } else {
                    clauses += "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?"
                    selectionArgs += "%Screenshot%"
                }
            }

            val selection = clauses
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" AND ")

            val args = selectionArgs
                .takeIf { it.isNotEmpty() }
                ?.toTypedArray()

            try {
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val sortOrder =
                    "${MediaStore.Images.Media.DATE_ADDED} DESC, " +
                        "${MediaStore.Images.Media._ID} DESC"

                val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val queryArgs = Bundle().apply {
                        putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                        putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                        putInt(ContentResolver.QUERY_ARG_OFFSET, offset)

                        if (selection != null && args != null) {
                            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                            putStringArray(
                                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                                args
                            )
                        }
                    }

                    contentResolver.query(
                        collectionUri,
                        projection,
                        queryArgs,
                        null
                    )
                } else {
                    val pagedSortOrder = "$sortOrder LIMIT $limit OFFSET $offset"

                    contentResolver.query(
                        collectionUri,
                        projection,
                        selection,
                        args,
                        pagedSortOrder
                    )
                }

                val imageUris = mutableListOf<Uri>()

                cursor?.use {
                    val idColumn =
                        it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

                    while (it.moveToNext()) {
                        val id = it.getLong(idColumn)
                        imageUris += Uri.withAppendedPath(
                            collectionUri,
                            id.toString()
                        )
                    }
                }

                LoadResult.Page(
                    data = imageUris,
                    prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                    nextKey = if (imageUris.size < limit) null else offset + imageUris.size
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                LoadResult.Error(exception)
            }
        }

    override fun getRefreshKey(state: PagingState<Int, Uri>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val closestPage = state.closestPageToPosition(anchorPosition) ?: return null

        return closestPage.prevKey?.plus(state.config.pageSize)
            ?: closestPage.nextKey?.minus(state.config.pageSize)
    }
}
