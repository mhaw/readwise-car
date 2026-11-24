package com.example.readwisecar.car

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.readwisecar.R
import com.example.readwisecar.ReadwiseCarApp
import com.example.readwisecar.data.ArticleEntity
import com.example.readwisecar.tts.AndroidTtsSynthesizer
import com.example.readwisecar.tts.ArticleParser
import com.example.readwisecar.tts.TtsSynthesizer
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
class ReadwiseMediaService : MediaLibraryService() {

    companion object {
        private const val ACTION_CYCLE_SPEED = "com.example.readwisecar.CYCLE_SPEED"
        private const val ACTION_ARCHIVE = "com.example.readwisecar.ARCHIVE"
    }

    private val app by lazy { application as ReadwiseCarApp }
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var tts: TtsSynthesizer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var synthJob: Job? = null
    private var progressJob: Job? = null

    private var activeArticle: ArticleEntity? = null
    private var activeChunkCount: Int = 0
    private var activeStartChunk: Int = 0

    private val speeds = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private var speedIndex = 1

    override fun onCreate() {
        super.onCreate()
        tts = AndroidTtsSynthesizer(this)

        val savedSpeed = app.tokenStore.playbackSpeed
        speedIndex = speeds.indexOfFirst { it == savedSpeed }.takeIf { it >= 0 } ?: 1

        val speechAttrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(speechAttrs, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                setPlaybackSpeed(speeds[speedIndex])
                addListener(playerListener)
            }

        mediaSession = MediaLibrarySession.Builder(this, exoPlayer, LibraryCallback())
            .setMediaButtonPreferences(buildCustomButtons())
            .build()
    }

    private fun buildCustomButtons(): List<CommandButton> {
        val speedBtn = CommandButton.Builder(CommandButton.ICON_PLAYBACK_SPEED)
            .setDisplayName("${speeds[speedIndex]}x")
            .setSessionCommand(SessionCommand(ACTION_CYCLE_SPEED, Bundle.EMPTY))
            .build()

        val archiveBtn = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_archive)
            .setDisplayName("Archive")
            .setSessionCommand(SessionCommand(ACTION_ARCHIVE, Bundle.EMPTY))
            .build()

        return listOf(speedBtn, archiveBtn)
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            persistCurrentProgress()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) persistCurrentProgress()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                val finished = activeArticle ?: return
                scope.launch {
                    app.repository.saveProgress(finished.id, activeChunkCount - 1, activeChunkCount)
                    if (app.tokenStore.autoAdvance) {
                        val next = app.repository.getNextArticleInLocation(finished.location, finished.id)
                        if (next != null) {
                            playArticle(next.id)
                        }
                    }
                }
            }
        }
    }

    private fun persistCurrentProgress() {
        val doc = activeArticle ?: return
        if (activeChunkCount <= 0) return
        val currentChunk = (activeStartChunk + exoPlayer.currentMediaItemIndex)
            .coerceIn(0, activeChunkCount - 1)

        progressJob?.cancel()
        progressJob = scope.launch {
            delay(800)
            app.repository.saveProgress(doc.id, currentChunk, activeChunkCount)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(ACTION_CYCLE_SPEED, Bundle.EMPTY))
                .add(SessionCommand(ACTION_ARCHIVE, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_CYCLE_SPEED -> {
                    speedIndex = (speedIndex + 1) % speeds.size
                    val newSpeed = speeds[speedIndex]
                    app.tokenStore.playbackSpeed = newSpeed
                    exoPlayer.setPlaybackSpeed(newSpeed)
                    mediaSession.setMediaButtonPreferences(buildCustomButtons())
                }
                ACTION_ARCHIVE -> {
                    val current = activeArticle
                    if (current != null) {
                        scope.launch {
                            val next = app.repository.getNextArticleInLocation(current.location, current.id)
                            app.repository.archiveArticle(current.id)
                            if (next != null) {
                                playArticle(next.id)
                            } else {
                                withContext(Dispatchers.Main) { exoPlayer.stop() }
                            }
                        }
                    }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId("root")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                ).build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            scope.launch {
                val items = if (parentId == "root") {
                    listOf(
                        folderItem("loc:shortlist", "Shortlist"),
                        folderItem("loc:later", "Later"),
                        folderItem("loc:new", "Inbox"),
                        folderItem("loc:feed", "Feed")
                    )
                } else {
                    val loc = parentId.removePrefix("loc:")
                    app.repository.getArticlesForLocation(loc).map { doc ->
                        val mins = (doc.wordCount / 200).coerceAtLeast(1)
                        val pct = (doc.readingProgress * 100).toInt()
                        val subtitle = if (pct in 1..99) {
                            "${doc.author} · ${mins}m · $pct%"
                        } else {
                            "${doc.author} · ${mins}m"
                        }
                        MediaItem.Builder()
                            .setMediaId("doc:${doc.id}")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(doc.title)
                                    .setArtist(subtitle)
                                    .setArtworkUri(doc.imageUrl?.let(Uri::parse))
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                    .build()
                            ).build()
                    }
                }
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }
            return future
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val selected = mediaItems.firstOrNull()
            if (selected?.mediaId?.startsWith("doc:") == true) {
                playArticle(selected.mediaId.removePrefix("doc:"))
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
                )
            }
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }
    }

    private fun playArticle(docId: String) {
        synthJob?.cancel()
        synthJob = scope.launch {
            val doc = app.repository.getArticleWithHtml(docId) ?: return@launch
            val html = doc.htmlContent ?: return@launch
            val chunks = ArticleParser.toSpeechChunks(html, doc.title, doc.author)
            if (chunks.isEmpty()) return@launch

            val startChunk = when {
                doc.lastPlayedChunk in 1 until chunks.size -> doc.lastPlayedChunk
                doc.readingProgress in 0.05f..0.95f ->
                    (doc.readingProgress * chunks.size).toInt().coerceIn(0, chunks.lastIndex)
                else -> 0
            }

            activeArticle = doc
            activeChunkCount = chunks.size
            activeStartChunk = startChunk

            val dir = File(cacheDir, "tts/${doc.id}").apply { mkdirs() }
            val firstWav = tts.synthesize(chunks[startChunk], File(dir, "%04d.wav".format(startChunk)))

            withContext(Dispatchers.Main) {
                exoPlayer.setMediaItem(chunkMediaItem(firstWav, doc, startChunk, chunks.size))
                exoPlayer.prepare()
                exoPlayer.play()
            }

            for (i in (startChunk + 1) until chunks.size) {
                ensureActive()
                val wav = tts.synthesize(chunks[i], File(dir, "%04d.wav".format(i)))
                withContext(Dispatchers.Main) {
                    if (activeArticle?.id == doc.id) {
                        exoPlayer.addMediaItem(chunkMediaItem(wav, doc, i, chunks.size))
                    }
                }
            }
        }
    }

    private fun chunkMediaItem(file: File, doc: ArticleEntity, index: Int, total: Int) =
        MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(doc.title)
                    .setArtist("${doc.author} · Part ${index + 1}/$total")
                    .setArtworkUri(doc.imageUrl?.let(Uri::parse))
                    .build()
            ).build()

    private fun folderItem(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                .build()
        ).build()

    override fun onDestroy() {
        synthJob?.cancel()
        progressJob?.cancel()
        scope.cancel()
        tts.release()
        exoPlayer.release()
        mediaSession.release()
        super.onDestroy()
    }
}
