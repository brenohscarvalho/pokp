package com.pokp.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Publishes a downloaded file into shared storage via MediaStore so it shows up in the
 * device's Downloads / Music. Requires no runtime storage permission (minSdk 29 / Android 10+).
 */
object MediaStoreSaver {

    suspend fun save(
        context: Context,
        file: File,
        mimeType: String,
        isAudio: Boolean,
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = if (isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativeDir = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOWNLOADS

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeDir/PokpDownloader")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values)
            ?: error("Não foi possível criar o arquivo no armazenamento")

        resolver.openOutputStream(uri).use { out ->
            requireNotNull(out) { "OutputStream nulo ao salvar" }
            file.inputStream().use { it.copyTo(out) }
        }

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    }
}
