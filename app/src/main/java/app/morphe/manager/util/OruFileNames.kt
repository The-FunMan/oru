/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.util

/**
 * ORU: names of the files the manager saves, chosen per flavour. The morphe flavour keeps its
 * names. Pure, so tests pass the flavour in as [oruUx] (`BuildConfig.ORU_UX` in the app).
 *
 * Reading is not affected: imports detect the format from the content and the extension
 * (`keystore`, `bks`, `jks`, ...), never from the brand in the name, so files saved under either
 * name still import. The keystore secrets listed in [extraKeystoreSecrets] are only tried in addition
 * to the upstream ones, so a keystore from either flavour still opens without typing its secret.
 */
object OruFileNames {

    /** Lowercase brand prefix for exported JSON and log files. */
    fun prefix(oruUx: Boolean): String = if (oruUx) "oru" else "morphe"

    /** Name of the keystore file saved to Downloads. */
    fun keystore(oruUx: Boolean): String = if (oruUx) "Oru.keystore" else "Morphe.keystore"

    /** Name offered when saving the patched APK of an installed app. */
    fun patchedApk(oruUx: Boolean): String = "${prefix(oruUx)}_export.apk"

    /**
     * Extra passwords and aliases that a keystore import tries after the upstream ones. Oru creates
     * its keystores with the alias and password "oru". Both are added in the oru flavour only.
     */
    fun extraKeystoreSecrets(oruUx: Boolean): Array<String> =
        if (oruUx) arrayOf("oru") else emptyArray()
}
