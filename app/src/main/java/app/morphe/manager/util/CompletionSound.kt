/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri

private const val TAG = "Morphe CompletionSound"

/** How long a tone is tracked before it is stopped, in case nothing else stops it. */
private const val ACTIVE_RINGTONE_RETAIN_MS = 60_000L

/**
 * Played as a notification, so the tone follows the notification volume rather than the ringer's
 * or the media's.
 */
private val TONE_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/**
 * The tone played when patching finishes, shared by the single-app run and the batch queue.
 *
 * The tone is the system's default notification sound. A tone the user has picked for success or
 * failure takes its place when it can be loaded.
 *
 * Only one tone is ever tracked, because only one run can finish at a time and the patcher
 * screen has to be able to stop whichever is playing when the user navigates away.
 */
object CompletionSound {

    @Volatile
    private var active: Ringtone? = null

    /**
     * Plays the success or failure tone, falling back to the default notification sound when the
     * user's own pick is not set or cannot be played.
     */
    fun play(
        context: Context,
        succeeded: Boolean,
        successSoundUri: String,
        errorSoundUri: String
    ) {
        val appContext = context.applicationContext
        if (!isAudible(appContext)) return

        val custom = if (succeeded) successSoundUri else errorSoundUri
        val customUri = custom.takeIf { it.isNotBlank() }?.toUri()

        val ringtone = customUri?.let { tryGetRingtone(appContext, it) }
            ?: defaultNotificationUri()?.let { tryGetRingtone(appContext, it) }
            ?: return
        ringtone.audioAttributes = TONE_ATTRIBUTES

        // A finished run replaces a tone still playing from an earlier one
        val previous = synchronized(this) { active.also { active = ringtone } }
        runCatching { previous?.stop() }

        // Exposed so the patcher screen can stop it when the user navigates home before it
        // finishes on its own
        try {
            ringtone.play()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play completion sound", e)
        }
        // Safety-net cleanup in case the user never navigates home
        Handler(Looper.getMainLooper()).postDelayed({
            if (active === ringtone) stop()
        }, ACTIVE_RINGTONE_RETAIN_MS)
    }

    /**
     * Stops the tone currently playing, if any. Safe to call from any thread and any state,
     * and a no-op when nothing is playing.
     */
    fun stop() {
        val ringtone = synchronized(this) { active.also { active = null } } ?: return
        runCatching { ringtone.stop() }
    }

    /**
     * Whether a notification would make a sound now. Silent and vibrate mode, a muted notification
     * volume and Do not disturb keep the tone quiet, as they keep notifications quiet.
     */
    private fun isAudible(context: Context): Boolean {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        if (audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0) return false
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return true
        return notificationManager.currentInterruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL
    }

    private fun defaultNotificationUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun tryGetRingtone(context: Context, uri: Uri): Ringtone? = runCatching {
        RingtoneManager.getRingtone(context, uri)
    }.onFailure {
        Log.w(TAG, "Failed to load ringtone $uri", it)
    }.getOrNull()
}
