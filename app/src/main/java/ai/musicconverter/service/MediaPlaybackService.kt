package ai.musicconverter.service

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import ai.musicconverter.R
import ai.musicconverter.worker.ConversionWorker
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        val COMMAND_CONVERT = SessionCommand("ai.musicconverter.CONVERT", Bundle.EMPTY)
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Branded notification: custom icon, forced color, convert action
        setMediaNotificationProvider(CustomMediaNotificationProvider(this))

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus= */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // "Convert" button: shown in expanded notification overflow slots
        val convertButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("Convert")
            .setIconResId(R.drawable.ic_convert)
            .setSessionCommand(COMMAND_CONVERT)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(PlaybackSessionCallback())
            .build()

        // Push the convert button into the notification layout
        mediaSession?.setCustomLayout(listOf(convertButton))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    // ── Session callback: authorize + handle custom commands ─────

    private inner class PlaybackSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Authorize the convert command for all connecting controllers
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(COMMAND_CONVERT)
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
            if (customCommand.customAction == COMMAND_CONVERT.customAction) {
                convertCurrentTrack()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    // ── Convert the currently playing track via WorkManager ──────

    private fun convertCurrentTrack() {
        val currentItem = mediaSession?.player?.currentMediaItem ?: return
        val uri = currentItem.localConfiguration?.uri ?: return
        val path = uri.path ?: return

        // Skip if already in a target format
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext in listOf("mp3", "m4a", "aac")) return

        val workRequest = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setInputData(
                workDataOf(
                    ConversionWorker.KEY_INPUT_PATH to path,
                    ConversionWorker.KEY_OUTPUT_FORMAT to ConversionWorker.FORMAT_AAC,
                    ConversionWorker.KEY_DELETE_ORIGINAL to false,
                    ConversionWorker.KEY_SAVE_TO_MUSIC_FOLDER to false
                )
            )
            .addTag(ConversionWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)
    }
}
