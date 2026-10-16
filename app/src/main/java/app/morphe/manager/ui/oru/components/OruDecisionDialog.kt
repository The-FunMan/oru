/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import app.morphe.manager.ui.oru.theme.OruMotion
import app.morphe.manager.ui.oru.theme.rememberOruReducedMotion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * A decision with one or two actions, as a centred card over a dimmed scrim. The primary action is filled,
 * the secondary one tonal or a text button in the tone (see [OruDialogActionStyle]), and both stack full width so long
 * labels never wrap. Tapping the scrim or going
 * back calls [onDismiss]. An [OruTone.Error] tone colours the title, so a removal reads as one. [hint] is
 * an optional line under the actions.
 */
@Composable
fun OruDecisionDialog(
    title: String,
    body: String,
    primary: OruDialogAction,
    secondary: OruDialogAction?,
    tone: OruTone,
    hint: String? = null,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            // Edge to edge, so the scrim covers the status bar and navigation bar areas too
            decorFitsSystemWindows = false,
        ),
    ) {
        // The platform dims the dialog window on its own. The scrim below is the only dim, so it is set to zero.
        // The window itself is transparent and its bars are drawn over the scrim rather than in a white band.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            // The bar colours are ignored on Android 15+ and still apply on older releases.
            @Suppress("DEPRECATION") dialogWindow?.run {
                setDimAmount(0f)
                setBackgroundDrawableResource(android.R.color.transparent)
                statusBarColor = android.graphics.Color.TRANSPARENT
                navigationBarColor = android.graphics.Color.TRANSPARENT
            }
        }

        val reduced = rememberOruReducedMotion()
        val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.scrim)
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = if (reduced) {
                    EnterTransition.None
                } else {
                    fadeIn(OruMotion.fade(reduced)) +
                        scaleIn(initialScale = EnterScale, animationSpec = OruMotion.springGlide(reduced))
                },
                exit = if (reduced) {
                    ExitTransition.None
                } else {
                    fadeOut(OruMotion.fade(reduced)) +
                        scaleOut(targetScale = EnterScale, animationSpec = OruMotion.springGlide(reduced))
                },
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = CardMargin)
                        .widthIn(max = CardMaxWidth)
                        .fillMaxWidth()
                        // The card floats above the scrim: a lit rim and a deep soft shadow
                        .oruSlab(
                            shape = CardShape,
                            color = scheme.surfaceContainerHigh,
                            elevation = OruElevation.Floating,
                        )
                        // Taps on the card are consumed here, so they never reach the scrim.
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(CardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(TextSpacing),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (tone == OruTone.Error) scheme.error else scheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ButtonSpacing),
                    ) {
                        OruPrimaryButton(
                            text = primary.label,
                            onClick = primary.onClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (secondary != null) {
                            when (secondary.style) {
                                OruDialogActionStyle.Tonal -> OruSecondaryButton(
                                    text = secondary.label,
                                    onClick = secondary.onClick,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OruDialogActionStyle.Text -> OruTextButton(
                                    text = secondary.label,
                                    onClick = secondary.onClick,
                                    color = if (tone == OruTone.Error) scheme.error else scheme.primary,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    if (hint != null) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private val CardShape = RoundedCornerShape(28.dp)
private val CardMargin = 24.dp
private val CardMaxWidth = 560.dp
private val CardPadding = 24.dp
private val SectionSpacing = 24.dp
private val TextSpacing = 8.dp
private val ButtonSpacing = 8.dp
private const val EnterScale = 0.92f
