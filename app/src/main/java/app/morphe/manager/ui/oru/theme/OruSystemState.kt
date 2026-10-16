/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The platform switches that decide how much Oru animates and blurs. Read live, not from the
 * app's own settings, because the user controls them in system settings.
 */
@Immutable
data class OruSystemState(
    val powerSaveMode: Boolean,
    val lowRamDevice: Boolean,
    /** `Settings.Global.ANIMATOR_DURATION_SCALE`; 0 when the user turned animations off. */
    val animatorDurationScale: Float,
)

fun readOruSystemState(context: Context): OruSystemState {
    val powerManager = context.getSystemService(PowerManager::class.java)
    val activityManager = context.getSystemService(ActivityManager::class.java)
    return OruSystemState(
        powerSaveMode = powerManager?.isPowerSaveMode == true,
        lowRamDevice = activityManager?.isLowRamDevice == true,
        animatorDurationScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ),
    )
}

/**
 * [readOruSystemState], kept current: re-read when the screen resumes and when battery saver
 * changes. Animator scale has no broadcast, so resuming is the point where it is picked up.
 */
@Composable
fun rememberOruSystemState(): OruSystemState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember(context) { mutableStateOf(readOruSystemState(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val refresh = { state = readOruSystemState(context) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                refresh()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            context.unregisterReceiver(receiver)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return state
}
