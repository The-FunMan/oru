/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.focus.customize

import app.morphe.manager.domain.manager.PatchOptionKeys
import app.morphe.manager.util.PatchSelection

/** The option keys that make up branding: the name the patched app is built with, and its icon. */
val ORU_BRANDING_OPTION_KEYS: Set<String> = setOf(
    PatchOptionKeys.CUSTOM_NAME,
    PatchOptionKeys.CUSTOM_ICON,
    PatchOptionKeys.APP_ICON,
)

/**
 * The selected patches that declare a branding option, as (source uid, patch name) in source order. Customize is
 * offered only when this is not empty. [optionKeysOf] gives the option keys a patch declares.
 */
fun oruBrandingTargets(
    selected: PatchSelection,
    optionKeysOf: (uid: Int, patchName: String) -> Set<String>,
): List<Pair<Int, String>> = selected.keys.sorted().flatMap { uid ->
    selected[uid].orEmpty().sorted()
        .filter { name -> optionKeysOf(uid, name).any { it in ORU_BRANDING_OPTION_KEYS } }
        .map { name -> uid to name }
}
