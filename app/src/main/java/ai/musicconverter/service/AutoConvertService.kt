package ai.musicconverter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import ai.musicconverter.MainActivity
import ai.musicconverter.R
import ai.musicconverter.data.MusicFileScanner
import ai.musicconverter.data.PreferencesManager
import ai.musicconverter.worker.ConversionWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Background service for automatic music file conversion.
 *
 * Smart batching features:
 * - Processes files in batches of MAX_BATCH_SIZE to avoid overwhelming the system
 * - Waits for current batch to complete before starting next batch
 * - Limits concurrent WorkManager jobs to MAX_CONCURRENT_JOBS
 * - Respects user preferences for delete original behavior
 */
@AndroidEntryPoint
class AutoConvertService : Service() {

    @Inject
    lateinit var scanner: MusicFileScanner

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var scanJob: Job? = null

    // Track files we've already queued to avoid duplicates
    private val queuedFiles = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAutoConvert()
            ACTION_STOP -> stopAutoConvert()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAutoConvert() {
        Timber.d("Starting auto-convert service")

        val notification = createNotification("Monitoring for new music files...", 0, 0)
        startForeground(NOTIFICATION_ID, notification)

        scanJob = serviceScope.launch {
            while (isActive) {
                try {
                    scanAndConvertBatched()
                } catch (e: Exception) {
                    Timber.e(e, "Error during auto-scan")
                }
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    private fun stopAutoConvert() {
        Timber.d("Stopping auto-convert service")
        scanJob?.cancel()
        queuedFiles.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Smart batched conversion that handles large numbers of files gracefully.
     */
    private suspend fun scanAndConvertBatched() {
        val workManager = WorkManager.getInstance(this@AutoConvertService)

        // Check how many jobs are currently running
        val runningJobs = workManager.getWorkInfosByTag(ConversionWorker.WORK_TAG)
            .get()
            .count { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

        if (runningJobs >= MAX_CONCURRENT_JOBS) {
            Timber.d("Already $runningJobs jobs in queue, waiting...")
            updateNotification("Converting... ($runningJobs jobs in progress)", runningJobs, runningJobs)
            return
        }

        // Scan for files
        val allFiles = scanner.scanForMusicFiles()
        val filesToConvert = allFiles
            .filter { it.needsConversion }
            .filter { it.path !in queuedFiles } // Skip already queued
            .take(MAX_BATCH_SIZE) // Only take a batch

        if (filesToConvert.isEmpty()) {
            if (queuedFiles.isEmpty()) {
                updateNotification("Monitoring for new music files...", 0, 0)
            }
            return
        }

        val totalPending = allFiles.count { it.needsConversion }
        Timber.d("Found $totalPending files needing conversion, processing batch of ${filesToConvert.size}")

        updateNotification(
            "Converting ${filesToConvert.size} of $totalPending files...",
            queuedFiles.size,
            totalPending
        )

        // Get user preference: keepOriginal = false means delete original
        val keepOriginal = preferencesManager.keepOriginalFiles.first()
        val deleteOriginal = !keepOriginal

        // Queue this batch
        filesToConvert.forEach { musicFile ->
            val workRequest = OneTimeWorkRequestBuilder<ConversionWorker>()
                .setInputData(
                    workDataOf(
                        ConversionWorker.KEY_INPUT_PATH to musicFile.path,
                        ConversionWorker.KEY_OUTPUT_FORMAT to ConversionWorker.FORMAT_AAC,
                        ConversionWorker.KEY_DELETE_ORIGINAL to deleteOriginal,
                        ConversionWorker.KEY_SAVE_TO_MUSIC_FOLDER to deleteOriginal // Only save to Music folder if deleting original
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .addTag(ConversionWorker.WORK_TAG)
                .build()

            workManager.enqueueUniqueWork(
                "convert_${musicFile.id}",
                ExistingWorkPolicy.KEEP,
                workRequest
            )

            queuedFiles.add(musicFile.path)

            // Small delay between enqueuing to avoid overwhelming WorkManager
            delay(ENQUEUE_DELAY_MS)
        }

        // Clean up completed files from tracking set
        cleanupCompletedFiles(workManager)
    }

    /**
     * Remove completed/failed files from the tracking set.
     */
    private fun cleanupCompletedFiles(workManager: WorkManager) {
        try {
            val workInfos = workManager.getWorkInfosByTag(ConversionWorker.WORK_TAG).get()
            val completedPaths = workInfos
                .filter { it.state.isFinished }
                .mapNotNull { it.outputData.getString(ConversionWorker.KEY_INPUT_PATH) }

            queuedFiles.removeAll(completedPaths.toSet())
            Timber.d("Cleaned up ${completedPaths.size} completed files from queue")
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up completed files")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Convert Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when the auto-convert service is running"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String, processed: Int, total: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AutoConvertService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Music Converter")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_convert)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)

        // Show progress bar if actively converting
        if (total > 0) {
            builder.setProgress(total, processed, false)
        }

        return builder.build()
    }

    private fun updateNotification(text: String, processed: Int, total: Int) {
        val notification = createNotification(text, processed, total)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        scanJob?.cancel()
        queuedFiles.clear()
    }

    companion object {
        const val ACTION_START = "ai.musicconverter.START_AUTO_CONVERT"
        const val ACTION_STOP = "ai.musicconverter.STOP_AUTO_CONVERT"

        private const val CHANNEL_ID = "auto_convert_channel"
        private const val NOTIFICATION_ID = 1001
        private const val SCAN_INTERVAL_MS = 60_000L // 1 minute

        // Smart batching constants
        private const val MAX_BATCH_SIZE = 10 // Process 10 files at a time
        private const val MAX_CONCURRENT_JOBS = 3 // Max simultaneous conversions
        private const val ENQUEUE_DELAY_MS = 100L // Delay between enqueuing jobs

        fun start(context: Context) {
            val intent = Intent(context, AutoConvertService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AutoConvertService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
