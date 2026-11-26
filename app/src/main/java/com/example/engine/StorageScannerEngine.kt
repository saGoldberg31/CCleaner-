package com.example.engine

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.model.DiskStorageCategory
import com.example.model.FileCategory
import com.example.model.SafFolderInfo
import com.example.model.ScannedFileItem
import com.example.model.StorageBreakdownResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class StorageScannerEngine(private val context: Context) {

    companion object {
        private val TEMP_EXTENSIONS = setOf("tmp", "temp", "log", "bak", "chk", "dmp", "old")
        private val TEMP_EXACT_NAMES = setOf("thumbs.db", ".ds_store", "desktop.ini")
        private val APK_EXTENSIONS = setOf("apk", "apkm", "xapk", "aab")
        private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
        private val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "svg", "raw", "dng")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "m4v", "wmv")
        private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "opus", "wma", "amr")
        private val DOCUMENT_EXTENSIONS = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "md", "json", "xml", "epub", "odt"
        )
        private const val MAX_SCAN_DEPTH = 6
        private const val MAX_FILES_SCANNED = 3500

        fun classifyFileCategory(fileName: String, mimeType: String): DiskStorageCategory {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val mime = mimeType.lowercase()
            return when {
                mime.startsWith("image/") || ext in PHOTO_EXTENSIONS -> DiskStorageCategory.PHOTOS
                mime.startsWith("video/") || ext in VIDEO_EXTENSIONS -> DiskStorageCategory.VIDEOS
                mime.startsWith("audio/") || ext in AUDIO_EXTENSIONS -> DiskStorageCategory.AUDIO
                mime.startsWith("text/") ||
                    mime.contains("pdf") ||
                    mime.contains("document") ||
                    mime.contains("spreadsheet") ||
                    mime.contains("presentation") ||
                    mime.contains("msword") ||
                    ext in DOCUMENT_EXTENSIONS -> DiskStorageCategory.DOCUMENTS
                ext in APK_EXTENSIONS ||
                    ext in ARCHIVE_EXTENSIONS ||
                    mime == "application/vnd.android.package-archive" ||
                    mime.contains("zip") -> DiskStorageCategory.ARCHIVES_APK
                else -> DiskStorageCategory.OTHER
            }
        }
    }

    fun resolveSafFolders(uriStrings: Set<String>): List<SafFolderInfo> {
        val persisted = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()

        return uriStrings.map { uriStr ->
            val uri = Uri.parse(uriStr)
            val hasPerm = uriStr in persisted
            val displayName = if (hasPerm) {
                queryTreeDisplayName(uri) ?: uri.lastPathSegment ?: "مجلد محدد"
            } else {
                uri.lastPathSegment ?: "مجلد غير متاح"
            }
            SafFolderInfo(
                uriString = uriStr,
                displayName = displayName,
                hasValidPermission = hasPerm
            )
        }
    }

    suspend fun scanSafFolders(
        folderUris: Set<String>,
        largeFileThresholdMb: Int,
        onProgress: (Int, String) -> Unit
    ): List<ScannedFileItem> = scanSafFoldersWithBreakdown(
        folderUris = folderUris,
        largeFileThresholdMb = largeFileThresholdMb,
        onProgress = onProgress
    ).scannedItems

    suspend fun scanSafFoldersWithBreakdown(
        folderUris: Set<String>,
        largeFileThresholdMb: Int,
        onProgress: (Int, String) -> Unit
    ): StorageBreakdownResult = withContext(Dispatchers.IO) {
        val rawFiles = mutableListOf<RawSafDoc>()
        val largeThresholdBytes = largeFileThresholdMb * 1024L * 1024L

        for (uriStr in folderUris) {
            val treeUri = Uri.parse(uriStr)
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
                ?: continue
            val rootName = queryTreeDisplayName(treeUri) ?: "مجلد"
            traverseTree(
                treeUri = treeUri,
                parentDocId = rootDocId,
                folderLabel = rootName,
                depth = 0,
                out = rawFiles,
                onStep = { count, folder ->
                    onProgress(count, "فحص: $folder")
                }
            )
            if (rawFiles.size >= MAX_FILES_SCANNED) break
        }

        val categoryBytes = mutableMapOf<DiskStorageCategory, Long>()
        val categoryCounts = mutableMapOf<DiskStorageCategory, Int>()
        for (doc in rawFiles) {
            val cat = classifyFileCategory(doc.name, doc.mimeType)
            categoryBytes[cat] = (categoryBytes[cat] ?: 0L) + doc.sizeBytes.coerceAtLeast(0L)
            categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1
        }

        val items = mutableListOf<ScannedFileItem>()
        val categorizedIds = HashSet<String>()

        // 1. Temp, Empty, Residual APKs, and Large Files
        for (doc in rawFiles) {
            val ext = doc.name.substringAfterLast('.', "").lowercase()
            val lowerName = doc.name.lowercase()

            when {
                doc.sizeBytes == 0L || ext in TEMP_EXTENSIONS || lowerName in TEMP_EXACT_NAMES -> {
                    categorizedIds.add(doc.uri.toString())
                    items.add(
                        doc.toItem(
                            category = FileCategory.TEMP_AND_EMPTY,
                            isSelected = true
                        )
                    )
                }
                ext in APK_EXTENSIONS -> {
                    categorizedIds.add(doc.uri.toString())
                    items.add(
                        doc.toItem(
                            category = FileCategory.RESIDUAL_APK,
                            isSelected = true
                        )
                    )
                }
                doc.sizeBytes >= largeThresholdBytes -> {
                    items.add(
                        doc.toItem(
                            category = FileCategory.LARGE_FILES,
                            isSelected = false
                        )
                    )
                }
            }
        }

        // 2. Real SHA-256 Duplicate Detection (only hash files sharing an exact non-zero byte size)
        onProgress(rawFiles.size, "تحليل الملفات المكررة عبر بصمة SHA-256...")
        val sizeGroups = rawFiles
            .filter { it.sizeBytes > 0L && it.sizeBytes <= 120L * 1024L * 1024L }
            .groupBy { it.sizeBytes }
            .filterValues { it.size >= 2 }

        for ((_, candidates) in sizeGroups) {
            val byHash = mutableMapOf<String, MutableList<RawSafDoc>>()
            for (candidate in candidates) {
                val hash = computeSha256(candidate.uri) ?: continue
                byHash.getOrPut(hash) { mutableListOf() }.add(candidate)
            }
            for ((hash, dupes) in byHash) {
                if (dupes.size >= 2) {
                    val sorted = dupes.sortedBy { it.lastModifiedMillis }
                    sorted.forEachIndexed { index, doc ->
                        items.add(
                            ScannedFileItem(
                                id = "dup_${doc.uri}",
                                uri = doc.uri,
                                name = doc.name,
                                folderName = doc.folderName,
                                sizeBytes = doc.sizeBytes,
                                lastModifiedMillis = doc.lastModifiedMillis,
                                mimeType = doc.mimeType,
                                category = FileCategory.DUPLICATE_FILES,
                                duplicateGroupHash = hash.take(10),
                                isSelected = index > 0, // Keep oldest original unchecked!
                                isDeletableSaf = true
                            )
                        )
                    }
                }
            }
        }

        StorageBreakdownResult(
            scannedItems = items.sortedByDescending { it.sizeBytes },
            categoryBytes = categoryBytes,
            categoryCounts = categoryCounts
        )
    }

    suspend fun findPickedMediaDuplicates(uris: List<Uri>): List<ScannedFileItem> =
        withContext(Dispatchers.IO) {
            if (uris.size < 2) return@withContext emptyList()
            val docs = uris.mapNotNull { uri -> queryOpenableMeta(uri) }
            val bySize = docs.filter { it.sizeBytes > 0L }.groupBy { it.sizeBytes }.filterValues { it.size >= 2 }
            val duplicates = mutableListOf<ScannedFileItem>()

            for ((_, candidates) in bySize) {
                val byHash = mutableMapOf<String, MutableList<RawSafDoc>>()
                for (doc in candidates) {
                    val hash = computeSha256(doc.uri) ?: continue
                    byHash.getOrPut(hash) { mutableListOf() }.add(doc)
                }
                for ((hash, group) in byHash) {
                    if (group.size >= 2) {
                        group.forEachIndexed { idx, doc ->
                            duplicates.add(
                                ScannedFileItem(
                                    id = "media_dup_${doc.uri}",
                                    uri = doc.uri,
                                    name = doc.name,
                                    folderName = "معرض الصور المختار",
                                    sizeBytes = doc.sizeBytes,
                                    lastModifiedMillis = doc.lastModifiedMillis,
                                    mimeType = doc.mimeType,
                                    category = FileCategory.DUPLICATE_FILES,
                                    duplicateGroupHash = hash.take(10),
                                    isSelected = idx > 0,
                                    isDeletableSaf = false
                                )
                            )
                        }
                    }
                }
            }
            duplicates
        }

    suspend fun deleteSafDocuments(items: List<ScannedFileItem>): Pair<Int, Long> =
        withContext(Dispatchers.IO) {
            var deletedCount = 0
            var bytesFreed = 0L
            val resolver = context.contentResolver
            val deletedUris = HashSet<String>()

            for (item in items) {
                if (!item.isDeletableSaf) continue
                val uriStr = item.uri.toString()
                if (uriStr in deletedUris) continue
                val success = runCatching {
                    DocumentsContract.deleteDocument(resolver, item.uri)
                }.getOrDefault(false)
                if (success) {
                    deletedUris.add(uriStr)
                    deletedCount++
                    bytesFreed += item.sizeBytes
                }
            }
            deletedCount to bytesFreed
        }

    private fun traverseTree(
        treeUri: Uri,
        parentDocId: String,
        folderLabel: String,
        depth: Int,
        out: MutableList<RawSafDoc>,
        onStep: (Int, String) -> Unit
    ) {
        if (depth > MAX_SCAN_DEPTH || out.size >= MAX_FILES_SCANNED) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext() && out.size < MAX_FILES_SCANNED) {
                    val docId = if (idCol >= 0) cursor.getString(idCol) else continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "ملف" else "ملف"
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val modified = if (modCol >= 0) cursor.getLong(modCol) else 0L

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        traverseTree(
                            treeUri = treeUri,
                            parentDocId = docId,
                            folderLabel = "$folderLabel/$name",
                            depth = depth + 1,
                            out = out,
                            onStep = onStep
                        )
                    } else {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        out.add(
                            RawSafDoc(
                                uri = docUri,
                                name = name,
                                folderName = folderLabel,
                                sizeBytes = size,
                                lastModifiedMillis = modified,
                                mimeType = mime
                            )
                        )
                        if (out.size % 25 == 0) {
                            onStep(out.size, folderLabel)
                        }
                    }
                }
            }
        }
    }

    private fun queryTreeDisplayName(treeUri: Uri): String? {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun queryOpenableMeta(uri: Uri): RawSafDoc? {
        val resolver: ContentResolver = context.contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "صورة" else "صورة"
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        RawSafDoc(
                            uri = uri,
                            name = name,
                            folderName = "معرض الصور",
                            sizeBytes = size,
                            lastModifiedMillis = System.currentTimeMillis(),
                            mimeType = resolver.getType(uri) ?: "image/*"
                        )
                    } else null
                }
        }.getOrNull()
    }

    private fun computeSha256(uri: Uri): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(16 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    private data class RawSafDoc(
        val uri: Uri,
        val name: String,
        val folderName: String,
        val sizeBytes: Long,
        val lastModifiedMillis: Long,
        val mimeType: String
    ) {
        fun toItem(category: FileCategory, isSelected: Boolean): ScannedFileItem {
            return ScannedFileItem(
                id = "${category.name}_$uri",
                uri = uri,
                name = name,
                folderName = folderName,
                sizeBytes = sizeBytes,
                lastModifiedMillis = lastModifiedMillis,
                mimeType = mimeType,
                category = category,
                isSelected = isSelected,
                isDeletableSaf = true
            )
        }
    }
}
