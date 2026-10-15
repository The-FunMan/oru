/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.settings

/** The pages of the Settings sheet. Advanced is pushed over Root inside the same sheet, and goes back to it. */
enum class OruSettingsPage { ROOT, ADVANCED }

/** The groups of the Advanced page, top to bottom. A link can scroll the page to one of them. */
enum class OruAdvancedGroup { INSTALLING, SOURCES, AUTOMATION, PATCHING, BACKUP, HELP }

/**
 * What the sheet shows when it opens: the list, scrolled to [section], or the Advanced page, scrolled to
 * [advancedGroup]. The default opens the top of the list, as the gear does.
 */
data class OruSettingsOpen(
    val page: OruSettingsPage = OruSettingsPage.ROOT,
    val section: OruSettingsSection? = null,
    val advancedGroup: OruAdvancedGroup? = null,
)

/** The rules of the sheet's pages, as plain functions, so they are tested without Compose. */
object OruSettingsPages {

    /** The page a back gesture or the back chevron leads to. Root has none: it dismisses the sheet instead. */
    fun back(page: OruSettingsPage): OruSettingsPage? = if (page == OruSettingsPage.ADVANCED) OruSettingsPage.ROOT else null

    /** Whether the sheet's back handler takes the back gesture. Only a pushed page takes it. */
    fun handlesBack(page: OruSettingsPage): Boolean = back(page) != null

    /** A move from [from] to [to] goes forward when [to] is the pushed page: it comes in from the right. */
    fun isForward(from: OruSettingsPage, to: OruSettingsPage): Boolean = to.ordinal > from.ordinal

    /** The page the sheet shows when it opens. */
    fun startPage(open: OruSettingsOpen): OruSettingsPage = open.page
}
