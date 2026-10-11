package com.openlauncher.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ImportedFile(
    val uri: String,
    val displayName: String
)

/**
 * Returns the broadest available picker on stripped-down Android head units.
 * DocumentsUI is preferred, followed by third-party content providers and the
 * legacy system media gallery.
 */
fun compatibleFilePickerIntent(
    context: Context,
    mimeType: String,
    allowMultiple: Boolean = false
): Intent? {
    fun Intent.configure() = apply {
        if (data != null) setDataAndType(data, mimeType) else type = mimeType
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val openDocument = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .configure()
        .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    val getContent = Intent(Intent.ACTION_GET_CONTENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .configure()
    val mediaUri = when {
        mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> null
    }
    val mediaPicker = mediaUri?.let { Intent(Intent.ACTION_PICK, it).configure() }

    return listOfNotNull(openDocument, getContent, mediaPicker).firstOrNull {
        it.resolveActivity(context.packageManager) != null
    }
}

fun selectedUris(result: Intent?): List<Uri> = buildList {
    result?.clipData?.let { clip ->
        for (index in 0 until clip.itemCount) {
            clip.getItemAt(index).uri?.let { if (it !in this) add(it) }
        }
    }
    result?.data?.let { if (it !in this) add(it) }
}

suspend fun importFileToPrivateStorage(
    context: Context,
    source: Uri,
    directoryName: String,
    baseName: String,
    replaceExisting: Boolean = false
): ImportedFile? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val displayName = runCatching {
            resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: baseName
        val extensionFromName = displayName.substringAfterLast('.', "")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(8)
        val extension = extensionFromName.ifEmpty {
            when (resolver.getType(source)?.lowercase()) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "image/heic", "image/heif" -> "heic"
                "audio/mpeg" -> "mp3"
                "audio/wav", "audio/x-wav" -> "wav"
                "audio/ogg" -> "ogg"
                "audio/mp4", "audio/x-m4a" -> "m4a"
                else -> "bin"
            }
        }
        val safeBaseName = baseName.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(context.filesDir, directoryName).apply { mkdirs() }
        val target = File(directory, "${safeBaseName}_${System.currentTimeMillis()}.$extension")
        resolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("File provider returned no data")

        if (replaceExisting) {
            directory.listFiles()?.forEach { file -> if (file != target) file.delete() }
        }
        ImportedFile(Uri.fromFile(target).toString(), displayName)
    }.getOrNull()
}

suspend fun clearPrivateImportDirectory(context: Context, directoryName: String) =
    withContext(Dispatchers.IO) {
        File(context.filesDir, directoryName).listFiles()?.forEach { it.delete() }
    }
