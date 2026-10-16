/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * System Roboto, no font files. Each style is mapped onto the Material 3 slot that carries the same role.
 * Uppercase labels are written in uppercase at the call site, since a text style cannot change case.
 */
val OruTypography = Typography(
    // Screen title, 32/35 bold
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 35.2.sp,
        letterSpacing = (-0.02).em,
    ),
    // Focus title, 30/33 bold
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.02).em,
    ),
    // Sheet title, 22/28 bold
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    // Card title, 18/22 semi-bold
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.01).em,
    ),
    // Body, 15/22
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    // Body small, 14/20
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // Chip, 12.5/16 semi-bold
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
    ),
    // Vitals label, 10.5 with 0.06em tracking
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.06.em,
    ),
)

/**
 * The title under every hero (Ready, Working, Done, Failed, Cancelled, Install). It is the Focus title style,
 * 30/33 bold, so the hero titles read at one weight on every screen.
 */
@Composable
fun oruHeroTitleStyle(): TextStyle = MaterialTheme.typography.headlineMedium

/**
 * Numbers and logs: 12/16 monospace with tabular figures, so counts and memory readouts do not
 * shift width as they change. Not a Material slot, so screens apply it explicitly.
 */
val OruMonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontFeatureSettings = "tnum",
)
