/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.ui.oru.settings

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.morphe.manager.BuildConfig
import app.morphe.manager.domain.manager.OruEndpoints
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.StorageStatsRepository
import app.morphe.manager.ui.oru.theme.OruGlassStyle
import app.morphe.manager.ui.oru.theme.OruThemeMode
import app.morphe.manager.ui.screen.shared.LanguageRepository
import app.morphe.manager.util.AppLocale
import app.morphe.manager.worker.UpdateCheckWorker
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Feeds the oru Settings sheet. Reads the preferences the sheet shows and writes the choices back. Every key is
 * the one the rest of the app already reads, so nothing is duplicated.
 */
class OruSettingsViewModel(
    private val prefs: PreferencesManager,
    private val storageStatsRepository: StorageStatsRepository,
    private val appContext: Context,
) : ViewModel(), OruSettingsEvents {

    private val hasGms: Boolean = GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS

    private data class Toggles(
        val themeMode: OruThemeMode,
        val glassStyle: OruGlassStyle,
        val wallpaperColors: Boolean,
        val keepAppsUpdated: Boolean,
        val allowMeteredUpdates: Boolean,
    )

    private val toggles: Flow<Toggles> = combine(
        prefs.oruThemeMode.flow,
        prefs.oruGlassStyle.flow,
        prefs.wallpaperColors.flow,
        prefs.backgroundUpdateNotifications.flow,
        prefs.allowMeteredUpdates.flow,
    ) { theme, glass, wallpaper, keep, metered ->
        Toggles(theme, glass, wallpaper, keep, metered)
    }

    private val environment: Flow<Pair<Long, String>> = combine(
        storageStatsRepository.stats,
        AppLocale.selected,
    ) { stats, language ->
        stats.totalCacheBytes to language
    }

    /** Rows for the sheet. Reads come from the preferences, so a choice shows in the sheet as it is written. */
    val state: StateFlow<OruSettingsUiState> = combine(toggles, environment, prefs.oruAnimations.flow) { t, env, animations ->
        OruSettingsPresenter.present(
            OruSettingsInput(
                sdkInt = Build.VERSION.SDK_INT,
                themeMode = t.themeMode,
                glassStyle = t.glassStyle,
                animations = animations,
                wallpaperColors = t.wallpaperColors,
                keepAppsUpdated = t.keepAppsUpdated,
                allowMeteredUpdates = t.allowMeteredUpdates,
                reclaimableBytes = env.first,
                languageLabel = languageLabel(env.second),
                versionName = BuildConfig.VERSION_NAME,
            )
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, OruSettingsPresenter.present(emptyInput()))

    /** Empty for the phone language, which the sheet words itself. */
    private fun languageLabel(code: String): String =
        if (code == AppLocale.SYSTEM) "" else LanguageRepository.getLanguage(code, appContext).displayName

    override fun onToggle(id: OruSettingsRowId, on: Boolean) {
        viewModelScope.launch {
            when (id) {
                OruSettingsRowId.ANIMATIONS -> prefs.oruAnimations.update(on)
                OruSettingsRowId.WALLPAPER_COLORS -> prefs.wallpaperColors.update(on)
                OruSettingsRowId.KEEP_APPS_UPDATED -> setKeepAppsUpdated(on)
                OruSettingsRowId.MOBILE_DATA_UPDATES -> prefs.allowMeteredUpdates.update(on)
                else -> Unit // Link rows are handled by the screen that hosts the sheet
            }
        }
    }

    override fun onThemeModeSelected(mode: OruThemeMode) {
        viewModelScope.launch { prefs.oruThemeMode.update(mode) }
    }

    override fun onGlassStyleSelected(style: OruGlassStyle) {
        viewModelScope.launch { prefs.oruGlassStyle.update(style) }
    }

    override fun onLanguageSelected(code: String) {
        AppLocale.select(appContext, code)
    }

    private suspend fun setKeepAppsUpdated(on: Boolean) {
        prefs.backgroundUpdateNotifications.update(on)
        // Same rule as the app start: oru runs the worker on Google Play services devices too
        val interval = prefs.updateCheckInterval.get()
        if (OruEndpoints.current.updateWorkerScheduled(on, hasGms)) {
            UpdateCheckWorker.schedule(appContext, interval)
        } else {
            UpdateCheckWorker.cancel(appContext)
        }
    }

    private companion object {
        fun emptyInput() = OruSettingsInput(
            sdkInt = 0,
            themeMode = OruThemeMode.System,
            glassStyle = OruGlassStyle.Frosted,
            animations = true,
            wallpaperColors = false,
            keepAppsUpdated = false,
            allowMeteredUpdates = true,
            reclaimableBytes = 0L,
            languageLabel = "",
            versionName = "",
        )
    }
}

/**
 * The view model of the Settings sheet for the screen that hosts it (Home or Focus). Both build it the same way, on
 * the preferences the rest of the app reads, so the sheet shows the same state wherever it opens.
 */
@Composable
fun rememberOruSettingsViewModel(prefs: PreferencesManager): OruSettingsViewModel {
    val context = LocalContext.current
    val storageStats: StorageStatsRepository = koinInject()
    return viewModel(
        factory = remember(prefs, storageStats) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = OruSettingsViewModel(
                    prefs = prefs,
                    storageStatsRepository = storageStats,
                    appContext = context.applicationContext,
                ) as T
            }
        },
    )
}
