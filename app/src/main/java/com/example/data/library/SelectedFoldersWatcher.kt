package com.example.data.library

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Lifecycle-aware live watcher that monitors user-selected music folders for newly copied,
 * downloaded, moved, or deleted audio files:
 * 1. Direct `FileObserver` (`CLOSE_WRITE | MOVED_TO | MOVED_FROM | DELETE`) on each selected
 *    directory (using `FileObserver(List<File>, mask)` on API 29+, per-directory fallback below).
 * 2. `ContentObserver` on `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`.
 *
 * All events are debounced (~1000ms) on `Dispatchers.IO` before invoking [onFolderChangeDetected].
 */
class SelectedFoldersWatcher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val onFolderChangeDetected: suspend () -> Unit
) {
    private val lock = Any()
    private val fileObservers = mutableListOf<FileObserver>()
    private var mediaStoreObserver: ContentObserver? = null
    private var debounceJob: Job? = null
    private var isStarted: Boolean = false
    private var currentFolders: List<NormalizedSelectedFolder> = emptyList()
    private var currentScanAllFolders: Boolean = false

    fun updateFolders(folders: List<NormalizedSelectedFolder>, scanAllFolders: Boolean) {
        val restartNeeded = synchronized(lock) {
            val changed = folders != currentFolders || scanAllFolders != currentScanAllFolders
            currentFolders = folders
            currentScanAllFolders = scanAllFolders
            changed && isStarted
        }
        if (restartNeeded) {
            rebuildFileObservers()
            ensureMediaStoreObserverRegistered()
        }
    }

    fun start() {
        synchronized(lock) {
            if (isStarted) return
            isStarted = true
        }
        rebuildFileObservers()
        ensureMediaStoreObserverRegistered()
    }

    fun stop() {
        synchronized(lock) {
            isStarted = false
            debounceJob?.cancel()
            debounceJob = null
            stopFileObserversLocked()
            unregisterMediaStoreObserverLocked()
        }
    }

    fun triggerDebouncedSync() {
        synchronized(lock) {
            debounceJob?.cancel()
            debounceJob = scope.launch(Dispatchers.IO) {
                delay(debounceMs)
                onFolderChangeDetected()
            }
        }
    }

    private fun rebuildFileObservers() {
        val foldersSnapshot = synchronized(lock) {
            stopFileObserversLocked()
            if (!isStarted) return
            currentFolders
        }
        if (foldersSnapshot.isEmpty()) return

        // Discover readable root + nested directories (capped at MAX_WATCHED_DIRS) off the UI thread
        scope.launch(Dispatchers.IO) {
            val dirs = collectWatchableDirectories(foldersSnapshot)
            if (dirs.isEmpty()) return@launch
            synchronized(lock) {
                if (!isStarted) return@synchronized
                stopFileObserversLocked()
                attachFileObserversLocked(dirs)
            }
        }
    }

    private fun attachFileObserversLocked(dirs: List<File>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val observer = object : FileObserver(dirs, WATCH_MASK) {
                override fun onEvent(event: Int, path: String?) {
                    handleFileEvent(event, path)
                }
            }
            runCatching {
                observer.startWatching()
                fileObservers += observer
            }
        } else {
            for (dir in dirs) {
                @Suppress("DEPRECATION")
                val observer = object : FileObserver(dir.absolutePath, WATCH_MASK) {
                    override fun onEvent(event: Int, path: String?) {
                        handleFileEvent(event, path)
                    }
                }
                runCatching {
                    observer.startWatching()
                    fileObservers += observer
                }
            }
        }
    }

    private fun handleFileEvent(event: Int, path: String?) {
        val cleanEvent = event and FileObserver.ALL_EVENTS
        if ((cleanEvent and WATCH_MASK) == 0) return
        if (!path.isNullOrBlank()) {
            val fileName = path.substringAfterLast('/')
            if (fileName.startsWith(".") || fileName.startsWith(".trashed", ignoreCase = true)) {
                return
            }
        }
        triggerDebouncedSync()
    }

    private fun ensureMediaStoreObserverRegistered() {
        synchronized(lock) {
            if (!isStarted) return
            if (currentFolders.isEmpty() && !currentScanAllFolders) {
                unregisterMediaStoreObserverLocked()
                return
            }
            if (mediaStoreObserver != null) return
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    triggerDebouncedSync()
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    triggerDebouncedSync()
                }
            }
            runCatching {
                context.contentResolver.registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer
                )
                mediaStoreObserver = observer
            }
        }
    }

    private fun stopFileObserversLocked() {
        fileObservers.forEach { obs ->
            runCatching { obs.stopWatching() }
        }
        fileObservers.clear()
    }

    private fun unregisterMediaStoreObserverLocked() {
        mediaStoreObserver?.let { obs ->
            runCatching { context.contentResolver.unregisterContentObserver(obs) }
        }
        mediaStoreObserver = null
    }

    private fun collectWatchableDirectories(folders: List<NormalizedSelectedFolder>): List<File> {
        val result = LinkedHashMap<String, File>()
        val queue = ArrayDeque<Pair<File, Int>>()
        for (folder in folders) {
            val root = folder.resolveReadableDirectory() ?: continue
            val key = FolderScopeMatcher.normalizeAbsolutePath(root.absolutePath)
            if (result.putIfAbsent(key, root) == null) {
                queue.addLast(root to 0)
            }
        }
        while (queue.isNotEmpty() && result.size < MAX_WATCHED_DIRS) {
            val (dir, depth) = queue.removeFirst()
            if (depth >= MAX_SUBDIR_DEPTH) continue
            val children = runCatching { dir.listFiles() }.getOrNull().orEmpty()
            for (child in children) {
                if (result.size >= MAX_WATCHED_DIRS) break
                if (child.isDirectory && child.canRead() && !child.name.startsWith(".")) {
                    val key = FolderScopeMatcher.normalizeAbsolutePath(child.absolutePath)
                    if (result.putIfAbsent(key, child) == null) {
                        queue.addLast(child to (depth + 1))
                    }
                }
            }
        }
        return result.values.toList()
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS: Long = 1_000L
        const val WATCH_MASK: Int = FileObserver.CLOSE_WRITE or
            FileObserver.MOVED_TO or
            FileObserver.MOVED_FROM or
            FileObserver.DELETE
        private const val MAX_SUBDIR_DEPTH = 4
        private const val MAX_WATCHED_DIRS = 128
    }
}
