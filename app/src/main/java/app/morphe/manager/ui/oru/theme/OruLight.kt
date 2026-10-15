/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/*
 * The light of the Oru depth system. Every lit edge, highlight and cast shadow of the glass slabs and the raised
 * controls takes its direction from one place, so the whole app is lit by the same lamp. At rest the lamp hangs
 * at the top left. While the app is resumed, the gravity sensor (or the accelerometer when there is no gravity
 * sensor) turns it a little as the phone is tilted: a roll turns the direction of the lamp, a pitch strengthens or
 * softens the glint. The reading is low-pass filtered, and a change smaller than a visible step is not published,
 * so a phone lying still on a table keeps no surface drawing. The sensor is registered on resume and unregistered on
 * pause, and with reduced motion, or no sensor, the lamp stays where it hangs at rest.
 *
 * The values are snapshot state and are read only in draw phases, so a tilt redraws the lit edges and never
 * recomposes.
 */

/** The direction of the lamp at rest: the top left, as an angle (radians, y down) of the vector that points at it. */
val OruLightRestAngle = (-3.0 * PI / 4.0).toFloat()

/** How far a full roll (45 degrees) turns the lamp from its rest angle, in radians (about 34 degrees). */
const val OruLightMaxTurn = 0.6f

/** The lamp: [angle] points at it, [lift] (-1 to 1) is how much the glint is lifted by the way the phone leans. */
@Stable
class OruLightState {
    var angle: Float by mutableFloatStateOf(OruLightRestAngle)
    var lift: Float by mutableFloatStateOf(0f)

    /** Back to the rest direction. */
    fun rest() {
        angle = OruLightRestAngle
        lift = 0f
    }
}

/** The lamp every surface reads. Outside a theme it is a lamp that stays at rest. */
val LocalOruLight = staticCompositionLocalOf { OruLightState() }

/** A tilt read from gravity: [roll] and [pitch] both in -1..1, 0 when the phone is held as it is read. */
data class OruTilt(val roll: Float, val pitch: Float)

/**
 * The tilt of a phone from its gravity vector ([gx], [gy], [gz] in the sensor frame, x to the right, y up the screen,
 * z out of the screen). The roll is the sideways lean, a full 1 at 45 degrees. The pitch is 0 at 40 degrees back from
 * upright, which is how a phone is held to read it, -1 when upright and 1 when it lies flat. A vector of no length
 * (a sensor that has not reported) is a phone held as it is read.
 */
fun oruTiltFromGravity(gx: Float, gy: Float, gz: Float): OruTilt {
    val length = sqrt(gx * gx + gy * gy + gz * gz)
    if (length < MinGravity) return OruTilt(roll = 0f, pitch = 0f)
    val roll = asin((gx / length).coerceIn(-1f, 1f)) / FullRollRad
    val back = atan2(gz, gy)
    val pitch = (back - ReadingPitchRad) / ReadingPitchSpan
    return OruTilt(roll = roll.coerceIn(-1f, 1f), pitch = pitch.coerceIn(-1f, 1f))
}

/**
 * The angle of the lamp for a [roll] (-1 to 1). A phone that rolls its right edge down reads a negative x on the
 * sensor, and the lamp, fixed in the room, then moves against the roll, so the angle follows the sign of the roll.
 */
fun oruLightAngle(roll: Float): Float = OruLightRestAngle + roll.coerceIn(-1f, 1f) * OruLightMaxTurn

/** One step of the low-pass filter: [previous] moved [smoothing] of the way to [raw]. */
fun oruLowPass(previous: Float, raw: Float, smoothing: Float): Float = previous + (raw - previous) * smoothing

/** Whether a change is large enough to be published: smaller ones are sensor noise and redraw nothing. */
fun oruLightChanged(old: Float, new: Float): Boolean = abs(new - old) >= LightStep

/**
 * Provides the lamp to [content] and keeps it fed from the sensor while the app is resumed. Provided once, by
 * OruTheme. With reduced motion the lamp is at rest and no sensor is touched.
 */
@Composable
fun ProvideOruLight(content: @Composable () -> Unit) {
    val reduced = rememberOruReducedMotion()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val state = remember { OruLightState() }

    DisposableEffect(context, owner, reduced) {
        state.rest()
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (reduced || manager == null || sensor == null) {
            return@DisposableEffect onDispose { }
        }

        var gx = 0f
        var gy = 0f
        var gz = 0f
        var seeded = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 3) return
                if (seeded) {
                    gx = oruLowPass(gx, event.values[0], LightSmoothing)
                    gy = oruLowPass(gy, event.values[1], LightSmoothing)
                    gz = oruLowPass(gz, event.values[2], LightSmoothing)
                } else {
                    gx = event.values[0]
                    gy = event.values[1]
                    gz = event.values[2]
                    seeded = true
                }
                val tilt = oruTiltFromGravity(gx, gy, gz)
                val angle = oruLightAngle(tilt.roll)
                if (oruLightChanged(state.angle, angle)) state.angle = angle
                if (oruLightChanged(state.lift, tilt.pitch)) state.lift = tilt.pitch
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        // The observer replays the lifecycle up to its current state, so a resumed app registers at once
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    seeded = false
                    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                }
                Lifecycle.Event.ON_PAUSE -> manager.unregisterListener(listener)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            manager.unregisterListener(listener)
            state.rest()
        }
    }

    CompositionLocalProvider(LocalOruLight provides state, content = content)
}

/** The sensor reports about every 60 ms; this share of each report is taken, so the lamp follows without a flutter. */
private const val LightSmoothing = 0.12f

/** The smallest change of the angle or the lift that is published (about half a degree). */
private const val LightStep = 0.01f

private const val MinGravity = 1f
private val FullRollRad = (PI / 4.0).toFloat()
private val ReadingPitchRad = (40.0 * PI / 180.0).toFloat()
private val ReadingPitchSpan = (40.0 * PI / 180.0).toFloat()
