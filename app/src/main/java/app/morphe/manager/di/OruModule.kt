/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.di

import app.morphe.manager.ui.oru.focus.ActiveRunTracker
import app.morphe.manager.ui.oru.focus.OruRunReadyNotifier
import app.morphe.manager.ui.oru.focus.SingleRunOwner
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Bindings of the oru UX. Registered for both flavors, which is harmless because Morphe never resolves any of them.
 */
val oruModule = module {
    singleOf(::ActiveRunTracker)
    single { SingleRunOwner(app = get(), runs = get(), readyNotifier = OruRunReadyNotifier(app = get())) }
}
