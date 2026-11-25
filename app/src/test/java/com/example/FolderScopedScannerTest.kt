package com.example

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.CadenceDatabase
import com.example.data.db.SavedFolderEntity
import com.example.data.library.ArtworkCacheManager
import com.example.data.library.DirectAudioFileMetadata
import com.example.data.library.DirectAudioMetadataReader
import com.example.data.library.FolderScopeMatcher
import com.example.data.library.LocalLibraryScanner
import com.example.data.library.MediaFileScanner
import com.example.data.library.ScanState
import com.example.ui.FullScreenScanningView
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderScopedScannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: CadenceDatabase
    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CadenceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempRoot = File(context.cacheDir, "folder_scope_test_${System.nanoTime()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        db.close()
        tempRoot.deleteRecursively()
    }

    @Test
    fun `FolderScopeMatcher strictly matches selected SAF and filesystem folders and rejects sibling prefixes`() {
        val primaryRoot = "/storage/emulated/0"
        val musicSaf = FolderScopeMatcher.normalize(
            rawUriOrPath = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            displayName = "Music",
            primaryExternalRoot = primaryRoot
        )
        val sdCardSaf = FolderScopeMatcher.normalize(
            rawUriOrPath = "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3ALossless",
            displayName = "Lossless",
            primaryExternalRoot = primaryRoot
        )
        val selected = listOf(musicSaf, sdCardSaf)

        assertEquals("/storage/emulated/0/Music", musicSaf.absoluteDirPath)
        assertEquals("Music/", musicSaf.relativePathPrefix)
        assertEquals("primary", musicSaf.volumeId)
        assertEquals("/storage/1A2B-3C4D/Lossless", sdCardSaf.absoluteDirPath)
        assertEquals("Lossless/", sdCardSaf.relativePathPrefix)
        assertEquals("1A2B-3C4D", sdCardSaf.volumeId)

        // Tracks inside selected folders and their subfolders match
        assertTrue(
            FolderScopeMatcher.isTrackInScope(
                dataPath = "/storage/emulated/0/Music/song.flac",
                relativePath = "Music/",
                volumeName = "external_primary",
                uriString = "content://media/external/audio/media/1",
                selectedFolders = selected,
                scanAllFolders = false
            )
        )
        assertTrue(
            FolderScopeMatcher.isTrackInScope(
                dataPath = "/storage/emulated/0/Music/Jazz/night.mp3",
                relativePath = "Music/Jazz/",
                volumeName = "external_primary",
                uriString = "content://media/external/audio/media/2",
                selectedFolders = selected,
                scanAllFolders = false
            )
        )
        assertTrue(
            FolderScopeMatcher.isTrackInScope(
                dataPath = "/storage/1A2B-3C4D/Lossless/master.flac",
                relativePath = "Lossless/",
                volumeName = "1a2b-3c4d",
                uriString = "content://media/external/audio/media/3",
                selectedFolders = selected,
                scanAllFolders = false
            )
        )

        // Tracks in sibling/unselected folders MUST be strictly rejected
        assertFalse(
            "Sibling directory 'MusicVideos' must never match 'Music'",
            FolderScopeMatcher.isTrackInScope(
                dataPath = "/storage/emulated/0/MusicVideos/clip.mp3",
                relativePath = "MusicVideos/",
                volumeName = "external_primary",
                uriString = "content://media/external/audio/media/4",
                selectedFolders = selected,
                scanAllFolders = false
            )
        )
        assertFalse(
            "Unselected Download folder must be excluded",
            FolderScopeMatcher.isTrackInScope(
                dataPath = "/storage/emulated/0/Download/voice_note.m4a",
                relativePath = "Download/",
                volumeName = "external_primary",
                uriString = "content://media/external/audio/media/5",
                selectedFolders = selected,
                scanAllFolders = false
            )
        )
        assertFalse(
            "Same relative folder name on a different volume must be excluded",
            FolderScopeMatcher.isTrackInScope(
                dataPath = null,
                relativePath = "Lossless/",
                volumeName = "external_primary",
                uriString = "content://media/external/audio/media/6",
                selectedFolders = listOf(sdCardSaf),
                scanAllFolders = false
            )
        )

        // SQL selection never excludes via IS_MUSIC and filters by duration >= 10_000 ms + selected folders
        val (selection, args) = FolderScopeMatcher.buildMediaStoreFolderSelection(
            selectedFolders = listOf(musicSaf),
            scanAllFolders = false,
            sdkInt = Build.VERSION_CODES.Q
        )
        assertNotNull(selection)
        assertNotNull(args)
        assertFalse("Selection must not contain overly strict IS_MUSIC filter", selection!!.contains("is_music", ignoreCase = true))
        assertTrue("Selection must enforce duration >= 10_000 ms", args!!.contains("10000"))
        assertTrue("Selection args must include Music/% relative prefix", args.contains("Music/%"))
        assertTrue("Selection args must include absolute DATA boundary", args.contains("/storage/emulated/0/Music/%"))
    }

    @Test
    fun `scanner imports only tracks inside selected folders and strictly excludes unselected folders and trashed files`() = runBlocking {
        val selectedDir = File(tempRoot, "SelectedMusic").apply { mkdirs() }
        val selectedSubDir = File(selectedDir, "Ambient").apply { mkdirs() }
        val unselectedDir = File(tempRoot, "UnselectedDownloads").apply { mkdirs() }
        val siblingPrefixDir = File(tempRoot, "SelectedMusicExtra").apply { mkdirs() }

        val selectedFile1 = File(selectedDir, "aurora.flac").apply { writeBytes(ByteArray(4096) { 1 }) }
        val selectedFile2 = File(selectedSubDir, "drift.mp3").apply { writeBytes(ByteArray(4096) { 2 }) }
        val trashedFile = File(selectedDir, ".trashed-170000-deleted.mp3").apply { writeBytes(ByteArray(4096) { 3 }) }
        val unselectedFile = File(unselectedDir, "podcast.mp3").apply { writeBytes(ByteArray(4096) { 4 }) }
        val siblingFile = File(siblingPrefixDir, "leak.flac").apply { writeBytes(ByteArray(4096) { 5 }) }

        assertTrue(selectedFile1.exists() && selectedFile2.exists() && trashedFile.exists())
        assertTrue(unselectedFile.exists() && siblingFile.exists())

        db.folderDao().upsertFolder(
            SavedFolderEntity(
                treeUri = selectedDir.absolutePath,
                displayName = "SelectedMusic",
                lastScannedAt = 0L
            )
        )

        val scannedInvocations = mutableListOf<List<String>>()
        val scanner = createTestableScanner(scannedInvocations)

        val result = scanner.syncSelectedFolders(scanAllFolders = false, showProgress = false)

        assertEquals(2, result.addedCount)
        assertEquals(1, scannedInvocations.size)
        val invokedPaths = scannedInvocations.single().map(FolderScopeMatcher::normalizeAbsolutePath).toSet()
        assertEquals(
            setOf(
                FolderScopeMatcher.normalizeAbsolutePath(selectedFile1.absolutePath),
                FolderScopeMatcher.normalizeAbsolutePath(selectedFile2.absolutePath)
            ),
            invokedPaths
        )

        val stored = db.trackDao().getAllTracksOnce()
        val storedTitles = stored.map { it.title }.toSet()
        assertEquals(setOf("aurora", "drift"), storedTitles)
        assertFalse("Unselected folder track must never enter Room", "podcast" in storedTitles)
        assertFalse("Sibling prefix directory track must never enter Room", "leak" in storedTitles)
        assertFalse("Hidden .trashed file must be skipped", stored.any { it.title.contains("trashed", ignoreCase = true) })
    }

    @Test
    fun `adding a new audio file to a selected folder invokes MediaScannerConnection scanFile and upserts into Room while deleting prunes cleanly`() = runBlocking {
        val selectedDir = File(tempRoot, "MyVinyl").apply { mkdirs() }
        val initialTrackFile = File(selectedDir, "midnight_sun.flac").apply {
            writeBytes(ByteArray(4096) { 10 })
        }

        db.folderDao().upsertFolder(
            SavedFolderEntity(
                treeUri = selectedDir.absolutePath,
                displayName = "MyVinyl",
                lastScannedAt = 0L
            )
        )

        val scannedInvocations = mutableListOf<List<String>>()
        val scanner = createTestableScanner(scannedInvocations)

        // 1. Initial sync indexes midnight_sun.flac
        val initialSync = scanner.syncSelectedFolders(scanAllFolders = false, showProgress = false)
        assertEquals(1, initialSync.addedCount)
        assertEquals(1, scannedInvocations.size)

        val initialStored = db.trackDao().getAllTracksOnce().single()
        // Mark as favorite + played so we verify incremental sync preserves user engagement
        db.trackDao().setFavorite(initialStored.id, true)
        db.trackDao().recordPlay(initialStored.id, 1_760_000_000_000L)

        // 2. Add a brand-new audio file into the selected folder
        val newlyAddedFile = File(selectedDir, "new_horizon.opus").apply {
            writeBytes(ByteArray(6144) { 20 })
        }

        val secondSync = scanner.syncSelectedFolders(scanAllFolders = false, showProgress = false)
        assertEquals(1, secondSync.addedCount)
        assertEquals(0, secondSync.prunedCount)
        assertEquals(
            "MediaScannerConnection.scanFile must be invoked specifically for the new file",
            listOf(newlyAddedFile.absolutePath),
            secondSync.mediaScannerInvokedPaths
        )
        assertEquals(2, scannedInvocations.size)
        assertEquals(listOf(newlyAddedFile.absolutePath), scannedInvocations.last())

        val afterAdd = db.trackDao().getAllTracksOnce()
        assertEquals(setOf("midnight sun", "new horizon"), afterAdd.map { it.title }.toSet())
        val preservedOriginal = afterAdd.first { it.id == initialStored.id }
        assertTrue("Existing track favorite state must be preserved across syncs", preservedOriginal.isFavorite)
        assertEquals("Existing track play count must be preserved across syncs", 1, preservedOriginal.playCount)

        // 3. Delete the newly added file from the selected folder -> sync prunes it cleanly
        assertTrue(newlyAddedFile.delete())

        val thirdSync = scanner.syncSelectedFolders(scanAllFolders = false, showProgress = false)
        assertEquals(0, thirdSync.addedCount)
        assertEquals(1, thirdSync.prunedCount)
        assertTrue("No new files to scan when only deleting", thirdSync.mediaScannerInvokedPaths.isEmpty())

        val afterDelete = db.trackDao().getAllTracksOnce()
        assertEquals(1, afterDelete.size)
        assertEquals("midnight sun", afterDelete.single().title)
    }

    @Test
    fun `FullScreenScanningView renders M3 Expressive wavy progress indicator with live track count and determinate percentage`() {
        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.DARK) {
                FullScreenScanningView(
                    scanState = ScanState(
                        isScanning = true,
                        currentFolderName = "Lossless Archive",
                        currentFileName = "02 - Velvet Horizon.flac",
                        scannedCount = 42,
                        totalCount = 100,
                        addedCount = 40,
                        statusLabel = "Indexing 42 of 100…"
                    ),
                    artworkShape = ExpressiveArtworkShape.SCALLOP
                )
            }
        }
        composeRule.waitForIdle()

        val progressNode = composeRule.onNodeWithTag("scanning_progress_bar").assertIsDisplayed()
        progressNode.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.42f, 0f..1f)
            )
        )
        val bounds = progressNode.getUnclippedBoundsInRoot()
        assertEquals("Wavy progress indicator must have 20.dp height for sinusoidal amplitude", 20f, bounds.height.value, 1.5f)
        composeRule.onNodeWithTag("scanning_percent_badge")
            .assertIsDisplayed()
            .assertTextContains("42%")
        composeRule.onNodeWithTag("scanning_counter_text")
            .assertIsDisplayed()
            .assertTextContains("42 / 100 files · 40 added")
    }

    private fun createTestableScanner(
        scannedInvocations: MutableList<List<String>>
    ): LocalLibraryScanner {
        val fakeMediaScanner = MediaFileScanner { paths ->
            scannedInvocations += paths
            paths.associate { p -> FolderScopeMatcher.normalizeAbsolutePath(p) to Uri.fromFile(File(p)) }
        }
        val fakeMetadataReader = DirectAudioMetadataReader { file ->
            DirectAudioFileMetadata(
                title = file.nameWithoutExtension.replace('_', ' '),
                artist = "Cadence Artist",
                album = file.parentFile?.name ?: "Album",
                genre = "Electronic",
                durationMs = 185_000L,
                mimeType = when (file.extension.lowercase()) {
                    "flac" -> "audio/flac"
                    "opus" -> "audio/opus"
                    else -> "audio/mpeg"
                },
                bitrateKbps = 320,
                sampleRateHz = 48_000,
                bitDepth = if (file.extension.equals("flac", ignoreCase = true)) 24 else 16
            )
        }
        return LocalLibraryScanner(
            context = context,
            trackDao = db.trackDao(),
            folderDao = db.folderDao(),
            artworkCache = ArtworkCacheManager(context),
            mediaFileScanner = fakeMediaScanner,
            directMetadataReader = fakeMetadataReader,
            primaryExternalRootProvider = { tempRoot.absolutePath }
        )
    }
}
