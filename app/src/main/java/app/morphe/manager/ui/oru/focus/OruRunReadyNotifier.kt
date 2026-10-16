/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.app.NotificationManagerCompat
import app.morphe.manager.MainActivity
import app.morphe.manager.R

/**
 * Posts "Ready to install" for a finished run whose result is not on screen. A tap opens the Focus of that run, where the
 * install is. The notice installs nothing itself. It is not posted when notifications are off for the app, and the
 * accessory on Home says the same thing for as long as the install waits.
 */
class OruRunReadyNotifier(private val app: Application) : RunReadyNotifier {

    override fun readyToInstall(packageKey: String, label: String) {
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                app.getString(R.string.oru_notification_ready_to_install),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = MainActivity.ACTION_OPEN_FOCUS
            putExtra(MainActivity.EXTRA_FOCUS_PACKAGE, packageKey)
        }
        val contentIntent = PendingIntent.getActivity(
            app,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // The install action opens the same Focus as a tap does, where the install starts from its own button. It never installs here
        val installAction = Notification.Action.Builder(
            Icon.createWithResource(app, R.drawable.ic_notification_done),
            app.getString(R.string.oru_action_install),
            contentIntent,
        ).build()
        val notification = Notification.Builder(app, CHANNEL_ID)
            .setContentTitle(app.getString(R.string.oru_notification_ready_to_install))
            .setContentText(label)
            .setSmallIcon(R.drawable.ic_notification_done)
            .setContentIntent(contentIntent)
            .addAction(installAction)
            .setAutoCancel(true)
            .build()
        // Posting needs the notification permission on Android 13 and later, which the check above does not cover
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private companion object {
        const val CHANNEL_ID = "oru-run-ready"

        /** Its own id: the worker posts its progress and its completion under 1 and 2. */
        const val NOTIFICATION_ID = 3
    }
}
