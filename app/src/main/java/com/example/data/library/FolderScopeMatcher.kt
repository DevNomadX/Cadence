package com.example.data.library

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.Locale

/**
 * Canonical representation of a single user-selected music folder supporting:
 * - SAF tree URIs (e.g. `content://com.android.externalstorage.documents/tree/primary%3AMusic%2FFLAC`)
 * - Direct filesystem paths & `file://` URIs (e.g. `/storage/emulated/0/Music/FLAC`)
 * - MediaStore `RELATIVE_PATH` + `VOLUME_NAME` prefixes on API 29+
 */
data class NormalizedSelectedFolder(
    val rawUriOrPath: String,
    val displayName: String,
    val volumeId: String?,
    val relativePathPrefix: String?,
    val absoluteDirPath: String?,
    val treeUriPrefix: String?
) {
    /** Directory prefix with trailing `/` for strict boundary matching against `DATA` paths. */
    val absoluteDirBoundary: String?
        get() = absoluteDirPath?.let { path ->
            if (path == "/") "/" else "${path.trimEnd('/')}/"
        }

    fun resolveReadableDirectory(): File? {
        val abs = absoluteDirPath?.takeIf { it.isNotBlank() } ?: return null
        val dir = File(abs)
        return if (dir.exists() && dir.isDirectory && dir.canRead()) dir else null
    }
}

/**
 * Pure, deterministic folder-scope normalizer and matcher used across:
 * - Batched MediaStore SQL queries (`DATA` + `RELATIVE_PATH`)
 * - Direct filesystem walks
 * - Live `FileObserver` registration
 * - Atomic Room track pruning (removing tracks deleted or moved outside selected folders)
 */
object FolderScopeMatcher {

    const val MIN_MEDIASTORE_DURATION_MS: Long = 10_000L

    val DIRECT_WALK_EXTENSIONS: Set<String> = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav",
        "alac", "aiff", "aif", "oga", "ape", "dsf", "dff", "wv"
    )

    private val PRIMARY_ROOT_ALIASES = listOf(
        "/storage/emulated/0",
        "/sdcard"
    )

    fun defaultPrimaryStorageRoot(): String = runCatching {
        Environment.getExternalStorageDirectory()?.absolutePath
            ?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: "/storage/emulated/0"

    fun normalize(
        rawUriOrPath: String,
        displayName: String = "",
        primaryExternalRoot: String = defaultPrimaryStorageRoot()
    ): NormalizedSelectedFolder {
        val trimmed = rawUriOrPath.trim()
        val normalizedPrimaryRoot = normalizeAbsolutePath(primaryExternalRoot)

        // 1. SAF Tree URI: content://com.android.externalstorage.documents/tree/<volume>:<relPath>
        if (trimmed.startsWith("content://", ignoreCase = true)) {
            val treeDocId = extractTreeDocumentId(trimmed)
            val resolvedName = displayName.ifBlank {
                treeDocId?.substringAfter(':')?.substringAfterLast('/')?.ifBlank { null }
                    ?: Uri.decode(trimmed).substringAfterLast('/').substringAfterLast(':').ifBlank { "Music Folder" }
            }
            if (treeDocId != null && ':' in treeDocId) {
                val volume = treeDocId.substringBefore(':').trim()
                val relRaw = treeDocId.substringAfter(':').trim('/')
                val relPrefix = if (relRaw.isEmpty()) "" else "$relRaw/"
                val absPath = when {
                    volume.equals("primary", ignoreCase = true) -> {
                        if (relRaw.isEmpty()) normalizedPrimaryRoot else "$normalizedPrimaryRoot/$relRaw"
                    }
                    volume.isNotBlank() && !volume.equals("home", ignoreCase = true) -> {
                        if (relRaw.isEmpty()) "/storage/$volume" else "/storage/$volume/$relRaw"
                    }
                    volume.equals("home", ignoreCase = true) -> {
                        if (relRaw.isEmpty()) "$normalizedPrimaryRoot/Documents" else "$normalizedPrimaryRoot/Documents/$relRaw"
                    }
                    else -> null
                }
                val resolvedRelPrefix = if (volume.equals("home", ignoreCase = true)) {
                    if (relRaw.isEmpty()) "Documents/" else "Documents/$relRaw/"
                } else {
                    relPrefix
                }
                return NormalizedSelectedFolder(
                    rawUriOrPath = trimmed,
                    displayName = resolvedName,
                    volumeId = if (volume.equals("home", ignoreCase = true)) "primary" else volume.ifBlank { null },
                    relativePathPrefix = resolvedRelPrefix,
                    absoluteDirPath = absPath?.let(::normalizeAbsolutePath),
                    treeUriPrefix = trimmed.trimEnd('/')
                )
            }
            return NormalizedSelectedFolder(
                rawUriOrPath = trimmed,
                displayName = resolvedName,
                volumeId = null,
                relativePathPrefix = null,
                absoluteDirPath = null,
                treeUriPrefix = trimmed.trimEnd('/')
            )
        }

        // 2. file:// URI or direct absolute filesystem path
        val fsPath = if (trimmed.startsWith("file://", ignoreCase = true)) {
            Uri.parse(trimmed).path ?: trimmed.removePrefix("file://")
        } else {
            trimmed
        }

        if (fsPath.startsWith("/")) {
            val cleanAbs = normalizeAbsolutePath(fsPath)
            val (vol, rel) = splitVolumeAndRelativePath(cleanAbs, normalizedPrimaryRoot)
            val resolvedName = displayName.ifBlank {
                cleanAbs.substringAfterLast('/').ifBlank { "Music Folder" }
            }
            return NormalizedSelectedFolder(
                rawUriOrPath = trimmed,
                displayName = resolvedName,
                volumeId = vol,
                relativePathPrefix = rel,
                absoluteDirPath = cleanAbs,
                treeUriPrefix = null
            )
        }

        // 3. Relative folder path (e.g. "Music/FLAC")
        val cleanRel = fsPath.replace('\\', '/').trim('/')
        val relPrefix = if (cleanRel.isEmpty()) "" else "$cleanRel/"
        val absPath = if (cleanRel.isEmpty()) normalizedPrimaryRoot else "$normalizedPrimaryRoot/$cleanRel"
        val resolvedName = displayName.ifBlank {
            cleanRel.substringAfterLast('/').ifBlank { "Music Folder" }
        }
        return NormalizedSelectedFolder(
            rawUriOrPath = trimmed,
            displayName = resolvedName,
            volumeId = "primary",
            relativePathPrefix = relPrefix,
            absoluteDirPath = normalizeAbsolutePath(absPath),
            treeUriPrefix = null
        )
    }

    /**
     * Strictly checks whether a candidate track belongs to at least one of [selectedFolders].
     * When [scanAllFolders] is false and [selectedFolders] is empty, returns false so unselected
     * folders are never pulled in accidentally.
     */
    fun isTrackInScope(
        dataPath: String?,
        relativePath: String?,
        volumeName: String?,
        uriString: String?,
        selectedFolders: List<NormalizedSelectedFolder>,
        scanAllFolders: Boolean
    ): Boolean {
        if (scanAllFolders) return true
        if (selectedFolders.isEmpty()) return false

        val cleanDataPath = dataPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeAbsolutePath)
        val cleanRelativePath = relativePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeRelativePath)
        val cleanUri = uriString?.trim()

        return selectedFolders.any { folder ->
            matchesFolder(
                folder = folder,
                cleanDataPath = cleanDataPath,
                cleanRelativePath = cleanRelativePath,
                volumeName = volumeName,
                cleanUri = cleanUri
            )
        }
    }

    /**
     * Checks whether a persisted Room track's stored `folderPath` / `localUriString` falls inside
     * the user's currently active selected folder scope.
     */
    fun isPersistedTrackInScope(
        folderPath: String?,
        localUriString: String?,
        selectedFolders: List<NormalizedSelectedFolder>,
        scanAllFolders: Boolean
    ): Boolean {
        if (scanAllFolders) return true
        if (selectedFolders.isEmpty()) return false

        val uriPath = localUriString
            ?.takeIf { it.startsWith("file://", ignoreCase = true) }
            ?.let { runCatching { Uri.parse(it).path }.getOrNull() }
        val dataCandidate = when {
            !uriPath.isNullOrBlank() -> uriPath
            !folderPath.isNullOrBlank() && folderPath.startsWith("/") -> folderPath
            else -> null
        }
        val relCandidate = when {
            !folderPath.isNullOrBlank() && !folderPath.startsWith("/") -> folderPath
            else -> null
        }

        return isTrackInScope(
            dataPath = dataCandidate,
            relativePath = relCandidate,
            volumeName = null,
            uriString = localUriString,
            selectedFolders = selectedFolders,
            scanAllFolders = false
        )
    }

    fun isFileInFolder(file: File, folder: NormalizedSelectedFolder): Boolean {
        val boundary = folder.absoluteDirBoundary ?: return false
        val normalizedFile = normalizeAbsolutePath(file.absolutePath)
        return normalizedFile.startsWith(boundary, ignoreCase = true)
    }

    fun isSupportedAudioFile(file: File): Boolean {
        if (!file.isFile) return false
        val name = file.name
        if (name.isBlank() || name.startsWith(".") || name.startsWith(".trashed", ignoreCase = true)) {
            return false
        }
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in DIRECT_WALK_EXTENSIONS
    }

    fun isHiddenOrTrashedDirectory(dir: File): Boolean {
        val name = dir.name
        return name.startsWith(".") || name.equals("Android", ignoreCase = false) && dir.parentFile?.name == "0"
    }

    /**
     * Builds a parameterized SQL `WHERE` clause and `selectionArgs` for `MediaStore.Audio.Media`:
     * - Filters by valid audio MIME type or audio file extension and `DURATION >= 10_000L` ms
     *   (avoiding overly strict `IS_MUSIC != 0` exclusions that drop downloaded/copied tracks).
     * - Strictly scopes results to [selectedFolders] unless [scanAllFolders] is explicitly true.
     */
    fun buildMediaStoreFolderSelection(
        selectedFolders: List<NormalizedSelectedFolder>,
        scanAllFolders: Boolean,
        minDurationMs: Long = MIN_MEDIASTORE_DURATION_MS,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Pair<String?, Array<String>?> {
        if (!scanAllFolders && selectedFolders.isEmpty()) {
            return null to null
        }

        val effectiveMinDuration = minDurationMs.coerceAtLeast(MIN_MEDIASTORE_DURATION_MS)
        val args = ArrayList<String>()

        val audioMimeClause = buildString {
            append("(")
            append("${MediaStore.Audio.Media.MIME_TYPE} LIKE ?")
            args += "audio/%"
            listOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav").forEach { ext ->
                append(" OR LOWER(${MediaStore.Audio.Media.DISPLAY_NAME}) LIKE ?")
                args += "%.$ext"
            }
            append(")")
        }

        val baseClause = "$audioMimeClause AND ${MediaStore.Audio.Media.DURATION} >= ?"
        args += effectiveMinDuration.toString()

        if (scanAllFolders) {
            return baseClause to args.toTypedArray()
        }

        val folderPredicates = ArrayList<String>()
        for (folder in selectedFolders) {
            val parts = ArrayList<String>(2)
            val absBoundary = folder.absoluteDirBoundary
            val relPrefix = folder.relativePathPrefix

            if (sdkInt >= Build.VERSION_CODES.Q && relPrefix != null) {
                if (relPrefix.isEmpty()) {
                    parts += "${MediaStore.Audio.Media.RELATIVE_PATH} IS NOT NULL"
                } else {
                    parts += "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
                    args += "$relPrefix%"
                }
            }
            if (absBoundary != null) {
                @Suppress("DEPRECATION")
                parts += "${MediaStore.Audio.Media.DATA} LIKE ?"
                args += "$absBoundary%"
            }
            if (parts.isNotEmpty()) {
                folderPredicates += parts.joinToString(separator = " OR ", prefix = "(", postfix = ")")
            }
        }

        if (folderPredicates.isEmpty()) {
            // Selected folders have no MediaStore-queryable path (e.g., third-party SAF provider);
            // return null so caller skips MediaStore and relies on SAF traversal strictly.
            return null to null
        }

        val fullSelection = "$baseClause AND (${folderPredicates.joinToString(" OR ")})"
        return fullSelection to args.toTypedArray()
    }

    private fun matchesFolder(
        folder: NormalizedSelectedFolder,
        cleanDataPath: String?,
        cleanRelativePath: String?,
        volumeName: String?,
        cleanUri: String?
    ): Boolean {
        // 1. Strict absolute DATA path directory boundary match
        val absDir = folder.absoluteDirPath
        val absBoundary = folder.absoluteDirBoundary
        if (cleanDataPath != null && absDir != null && absBoundary != null) {
            if (cleanDataPath.equals(absDir, ignoreCase = true) ||
                cleanDataPath.startsWith(absBoundary, ignoreCase = true)
            ) {
                return true
            }
            // If both DATA and folder absolute path are known and live on the filesystem,
            // do NOT fall through to a looser relative-path guess when volumes clearly differ.
            if (cleanRelativePath == null) return false
        }

        // 2. API 29+ RELATIVE_PATH + optional VOLUME_NAME match
        val relPrefix = folder.relativePathPrefix
        if (cleanRelativePath != null && relPrefix != null) {
            val volumeCompatible = isVolumeCompatible(folder.volumeId, volumeName)
            if (volumeCompatible) {
                if (relPrefix.isEmpty()) return true
                if (cleanRelativePath.equals(relPrefix, ignoreCase = true) ||
                    cleanRelativePath.startsWith(relPrefix, ignoreCase = true)
                ) {
                    return true
                }
            }
        }

        // 3. SAF tree URI match
        val treePrefix = folder.treeUriPrefix
        if (cleanUri != null && treePrefix != null) {
            if (cleanUri.equals(treePrefix, ignoreCase = false) ||
                cleanUri.startsWith("$treePrefix/document/", ignoreCase = false)
            ) {
                return true
            }
        }

        return false
    }

    private fun isVolumeCompatible(folderVolumeId: String?, mediaStoreVolumeName: String?): Boolean {
        if (folderVolumeId.isNullOrBlank() || mediaStoreVolumeName.isNullOrBlank()) return true
        val normFolderVol = folderVolumeId.lowercase(Locale.ROOT)
        val normStoreVol = mediaStoreVolumeName.lowercase(Locale.ROOT)
        if (normFolderVol == "primary") {
            return normStoreVol == "external_primary" || normStoreVol == "external" || normStoreVol == "primary"
        }
        return normStoreVol == normFolderVol || normStoreVol == "external"
    }

    private fun extractTreeDocumentId(uriString: String): String? {
        val treeMarker = "/tree/"
        val idx = uriString.indexOf(treeMarker)
        if (idx < 0) return null
        val afterTree = uriString.substring(idx + treeMarker.length)
        val rawTreeSegment = afterTree.substringBefore("/document/").substringBefore('?')
        return Uri.decode(rawTreeSegment).takeIf { it.isNotBlank() }
    }

    private fun splitVolumeAndRelativePath(
        cleanAbsPath: String,
        normalizedPrimaryRoot: String
    ): Pair<String?, String?> {
        val candidatePrimaryRoots = buildList {
            add(normalizedPrimaryRoot)
            PRIMARY_ROOT_ALIASES.forEach { alias ->
                if (!alias.equals(normalizedPrimaryRoot, ignoreCase = true)) add(alias)
            }
        }
        for (root in candidatePrimaryRoots) {
            if (cleanAbsPath.equals(root, ignoreCase = true)) {
                return "primary" to ""
            }
            val boundary = "$root/"
            if (cleanAbsPath.startsWith(boundary, ignoreCase = true)) {
                val rel = cleanAbsPath.substring(boundary.length).trim('/')
                return "primary" to if (rel.isEmpty()) "" else "$rel/"
            }
        }
        if (cleanAbsPath.startsWith("/storage/", ignoreCase = true)) {
            val rest = cleanAbsPath.substring("/storage/".length)
            val vol = rest.substringBefore('/')
            if (vol.isNotBlank() && !vol.equals("emulated", ignoreCase = true)) {
                val rel = rest.substringAfter('/', "").trim('/')
                return vol to if (rel.isEmpty()) "" else "$rel/"
            }
        }
        return null to null
    }

    fun normalizeAbsolutePath(path: String): String {
        val collapsed = path.replace('\\', '/').replace(Regex("/+"), "/")
        return if (collapsed == "/") "/" else collapsed.trimEnd('/')
    }

    fun normalizeRelativePath(relativePath: String): String {
        val cleaned = relativePath.replace('\\', '/').replace(Regex("/+"), "/").trim('/')
        return if (cleaned.isEmpty()) "" else "$cleaned/"
    }
}
