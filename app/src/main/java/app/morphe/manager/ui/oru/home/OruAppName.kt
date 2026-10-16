/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.home

import app.morphe.manager.util.KnownApps

/** Characters that may sit around the brand word, as in "YouTube (Morphe)" or "YouTube - Morphe". */
private const val SEPARATORS = """[\s\-_:|()\[\]–]"""

/** The brand word at the start of a label, with the separators after it. */
private val LeadingBrand = Regex("""^$SEPARATORS*morphe(?![\p{L}\p{N}])$SEPARATORS*""", RegexOption.IGNORE_CASE)

/** The brand word at the end of a label, with the separators before it. */
private val TrailingBrand = Regex("""$SEPARATORS*(?<![\p{L}\p{N}])morphe$SEPARATORS*$""", RegexOption.IGNORE_CASE)

/**
 * ORU: the name an app is shown under in the Oru UI, for the package [packageName] labelled [label].
 *
 * A patched copy takes its label from the patch bundle, which can carry the brand word. That word is
 * taken off where it starts or ends the label. A known app shows its stock name whatever its label says,
 * and a label that would be left empty, such as the brand word alone, is shown as it is.
 */
fun oruAppName(packageName: String, label: String): String {
    KnownApps.fallbackName(packageName)?.let { return it }
    val stripped = label.replace(LeadingBrand, "").replace(TrailingBrand, "")
    return stripped.ifBlank { label }
}

/** The brand word as a whole word, in any case. */
private val WholeBrand = Regex("""(?<![\p{L}\p{N}])morphe(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

/** The brand letters anywhere in a text, in any case. */
private val AnyBrand = Regex("morphe", RegexOption.IGNORE_CASE)

/** Characters left dangling at the ends of a name once the brand word is taken out. */
private const val DanglingSeparators = "-_:|()[]–"

/**
 * ORU: the name of the app in a notification, for the package [packageName] labelled [label]. It is [oruAppName], the
 * name the Home card shows. When that name still holds the brand word, the whole word is taken out and the rest
 * trimmed. When nothing usable is left, the last part of the package name stands in. The result never contains the
 * brand letters, in any case.
 */
fun oruNotifiedAppName(packageName: String, label: String): String {
    val name = oruAppName(packageName, label)
    val stripped = if (AnyBrand.containsMatchIn(name)) {
        name
            .replace(WholeBrand, " ")
            .replace(Regex("\\s+"), " ")
            .trim { it.isWhitespace() || it in DanglingSeparators }
    } else {
        name.trim()
    }
    if (stripped.isNotEmpty() && !AnyBrand.containsMatchIn(stripped)) return stripped
    return packageName.substringAfterLast('.').replace(AnyBrand, "").trim()
}
