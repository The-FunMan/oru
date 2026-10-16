/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.components

import app.morphe.manager.ui.oru.theme.OruGlassStyle

/** The glass surfaces that take their own parameters: the Focus dock, the Home bar, the pills, the sheets and the Home cards. */
enum class OruGlassRole { Dock, Bar, Pill, Sheet, Card }

/**
 * Every number a glass surface draws with, for one style, theme and role.
 * - [blurDp]: the blur radius of the captured backdrop.
 * - [saturation]: the colour matrix saturation applied after the blur (1 is unchanged).
 * - [tintAlpha]: the surface colour laid over the blur. Text on it is measured against this (OruGlassParamsTest).
 * - [rimTop], [rimBottom]: the rim alpha at the edge nearest the lamp and the edge farthest from it (see OruLight.kt),
 *   the lit side at the top left when the phone is at rest.
 * - [rimWidthDp]: the full width of the rim, drawn centred on the edge and clipped to the inside.
 * - [highlight]: the white alpha of the gradient over the lit third of the glass.
 * - [topLine]: the alpha of the thin highlight just inside the lit edge, which gives the glass its thickness.
 * - [bottomShade]: the alpha of the faint shade inside the bottom edge.
 * - [lensBandDp], [lensBendDp], [lensChroma]: the edge lens of API 33 and later (OruLens.kt): how wide the band
 *   of bent light is, how far it pulls the picture behind, and how far the colours part. Nothing below API 33.
 */
data class OruGlassParams(
    val blurDp: Float,
    val saturation: Float,
    val tintAlpha: Float,
    val rimTop: Float,
    val rimBottom: Float,
    val rimWidthDp: Float,
    val highlight: Float,
    val topLine: Float,
    val bottomShade: Float,
    val lensBandDp: Float,
    val lensBendDp: Float,
    val lensChroma: Float,
) {
    /** The edge treatment of this glass: the rim, the thin highlight, the shade and the light over the lit third. */
    fun toEdge(): OruEdgeParams = OruEdgeParams(
        rimNear = rimTop,
        rimFar = rimBottom,
        rimWidthDp = rimWidthDp,
        topLine = topLine,
        bottomShade = bottomShade,
        sheen = highlight,
        sheenShare = GlassSheenShare,
    )
}

/** The light over the glass fades over the top third. */
private const val GlassSheenShare = 1f / 3f

/**
 * The parameters of a glass surface. Frosted is the default look: a strong blur under a strong tint, so any text
 * on it stays legible. Clear is the lighter liquid look: a weaker blur and tint, a brighter rim and an inner
 * highlight. Sheets raise the tint to a floor, because they carry long text: 0.86 when Frosted, 0.62 light and
 * 0.68 dark when Clear. Dark glass needs more tint than light, since a dark surface over a bright backdrop has
 * less contrast with light text than a light surface over a dark one.
 */
fun oruGlassParams(style: OruGlassStyle, isDark: Boolean, role: OruGlassRole): OruGlassParams {
    if (role == OruGlassRole.Card) return oruCardGlassParams(style, isDark)
    val base = when (style) {
        OruGlassStyle.Frosted -> OruGlassParams(
            blurDp = FrostedBlurDp,
            // Desaturated 0.9 then boosted 1.25; two saturation matrices multiply into one, so the net is 1.125
            saturation = FrostedSaturation,
            tintAlpha = if (isDark) FrostedTintDark else FrostedTintLight,
            rimTop = FrostedRimTop,
            rimBottom = FrostedRimBottom,
            rimWidthDp = FrostedRimWidthDp,
            highlight = FrostedHighlight,
            topLine = FrostedTopLine,
            bottomShade = if (isDark) FrostedShadeDark else FrostedShadeLight,
            lensBandDp = FrostedLensBandDp,
            lensBendDp = FrostedLensBendDp,
            lensChroma = FrostedLensChroma,
        )
        OruGlassStyle.Clear -> OruGlassParams(
            blurDp = ClearBlurDp,
            saturation = ClearSaturation,
            tintAlpha = if (isDark) ClearTintDark else ClearTintLight,
            rimTop = ClearRimTop,
            rimBottom = ClearRimBottom,
            rimWidthDp = ClearRimWidthDp,
            highlight = ClearHighlight,
            topLine = ClearTopLine,
            bottomShade = if (isDark) ClearShadeDark else ClearShadeLight,
            lensBandDp = ClearLensBandDp,
            lensBendDp = ClearLensBendDp,
            lensChroma = ClearLensChroma,
        )
    }
    if (role != OruGlassRole.Sheet) return base
    val floor = when (style) {
        OruGlassStyle.Frosted -> FrostedSheetTint
        OruGlassStyle.Clear -> if (isDark) ClearSheetTintDark else ClearSheetTintLight
    }
    // The sides and the bottom of a sheet are the edges of the screen, so there is no glass edge to bend the light there
    return base.copy(tintAlpha = maxOf(base.tintAlpha, floor), lensBandDp = 0f, lensBendDp = 0f)
}

/**
 * The Home card. It carries a title, a line, chips and two buttons over the app's own tint, so its tint is higher
 * than a bar's: 0.86 light and 0.88 dark when Frosted, 0.56 and 0.66 when Clear. The blur is lighter than a bar's,
 * since a card is large and every card blurs on its own. Clear has the brightest rim, so the card reads as a lit
 * edge of glass. OruGlassParamsTest holds the text on it to 4.5:1 over the strongest backdrop orb.
 */
private fun oruCardGlassParams(style: OruGlassStyle, isDark: Boolean): OruGlassParams = when (style) {
    OruGlassStyle.Frosted -> OruGlassParams(
        blurDp = CardFrostedBlurDp,
        saturation = FrostedSaturation,
        tintAlpha = if (isDark) CardFrostedTintDark else CardFrostedTintLight,
        rimTop = CardFrostedRimTop,
        rimBottom = FrostedRimBottom,
        rimWidthDp = FrostedRimWidthDp,
        highlight = CardFrostedHighlight,
        topLine = CardFrostedTopLine,
        bottomShade = if (isDark) FrostedShadeDark else FrostedShadeLight,
        lensBandDp = FrostedLensBandDp,
        lensBendDp = FrostedLensBendDp,
        lensChroma = FrostedLensChroma,
    )
    OruGlassStyle.Clear -> OruGlassParams(
        blurDp = CardClearBlurDp,
        saturation = ClearSaturation,
        tintAlpha = if (isDark) CardClearTintDark else CardClearTintLight,
        rimTop = CardClearRimTop,
        rimBottom = CardClearRimBottom,
        rimWidthDp = ClearRimWidthDp,
        highlight = CardClearHighlight,
        topLine = CardClearTopLine,
        bottomShade = if (isDark) ClearShadeDark else ClearShadeLight,
        lensBandDp = ClearLensBandDp,
        lensBendDp = ClearLensBendDp,
        lensChroma = ClearLensChroma,
    )
}

/**
 * [from] moved [fraction] of the way to [to], every number linear. Fractions outside 0..1 are clamped, so a spring
 * that overshoots never gives a negative blur or a tint above 1. The glass uses it to morph between two styles
 * instead of jumping.
 */
fun oruGlassLerp(from: OruGlassParams, to: OruGlassParams, fraction: Float): OruGlassParams {
    if (fraction <= 0f) return from
    if (fraction >= 1f) return to
    fun mix(a: Float, b: Float) = a + (b - a) * fraction
    return OruGlassParams(
        blurDp = mix(from.blurDp, to.blurDp),
        saturation = mix(from.saturation, to.saturation),
        tintAlpha = mix(from.tintAlpha, to.tintAlpha),
        rimTop = mix(from.rimTop, to.rimTop),
        rimBottom = mix(from.rimBottom, to.rimBottom),
        rimWidthDp = mix(from.rimWidthDp, to.rimWidthDp),
        highlight = mix(from.highlight, to.highlight),
        topLine = mix(from.topLine, to.topLine),
        bottomShade = mix(from.bottomShade, to.bottomShade),
        lensBandDp = mix(from.lensBandDp, to.lensBandDp),
        lensBendDp = mix(from.lensBendDp, to.lensBendDp),
        lensChroma = mix(from.lensChroma, to.lensChroma),
    )
}

private const val FrostedBlurDp = 28f
private const val FrostedSaturation = 1.125f
private const val FrostedTintLight = 0.78f
private const val FrostedTintDark = 0.80f
private const val FrostedRimTop = 0.35f
private const val FrostedRimBottom = 0.05f
private const val FrostedRimWidthDp = 1f
private const val FrostedHighlight = 0.12f
private const val FrostedSheetTint = 0.86f
private const val FrostedTopLine = 0.30f
private const val FrostedShadeLight = 0.05f
private const val FrostedShadeDark = 0.18f
private const val FrostedLensBandDp = 12f
private const val FrostedLensBendDp = 3.5f
private const val FrostedLensChroma = 0.02f

private const val ClearBlurDp = 14f
private const val ClearSaturation = 1.3f
private const val ClearTintLight = 0.30f
private const val ClearTintDark = 0.34f
private const val ClearRimTop = 0.55f
private const val ClearRimBottom = 0.08f
private const val ClearRimWidthDp = 1.5f
private const val ClearHighlight = 0.20f
private const val ClearSheetTintLight = 0.62f
private const val ClearSheetTintDark = 0.68f
private const val ClearTopLine = 0.55f
private const val ClearShadeLight = 0.06f
private const val ClearShadeDark = 0.22f
private const val ClearLensBandDp = 22f
private const val ClearLensBendDp = 11f
private const val ClearLensChroma = 0.06f

private const val CardFrostedBlurDp = 24f
private const val CardFrostedTintLight = 0.86f
private const val CardFrostedTintDark = 0.88f
private const val CardFrostedRimTop = 0.40f
private const val CardFrostedHighlight = 0.14f
private const val CardFrostedTopLine = 0.34f

private const val CardClearBlurDp = 12f
private const val CardClearTintLight = 0.56f
private const val CardClearTintDark = 0.66f
private const val CardClearRimTop = 0.75f
private const val CardClearRimBottom = 0.14f
private const val CardClearHighlight = 0.24f
private const val CardClearTopLine = 0.62f
