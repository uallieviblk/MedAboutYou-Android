// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uallsi.medaboutyou.MedApp
import com.uallsi.medaboutyou.ui.calendar.CalendarViewModel
import com.uallsi.medaboutyou.ui.dashboard.InsightsViewModel
import com.uallsi.medaboutyou.ui.detail.DetailViewModel
import com.uallsi.medaboutyou.ui.log.ActionLogViewModel
import com.uallsi.medaboutyou.ui.schedules.SchedulesViewModel
import com.uallsi.medaboutyou.ui.search.SearchViewModel
import com.uallsi.medaboutyou.ui.settings.SettingsViewModel
import com.uallsi.medaboutyou.ui.today.TodayViewModel

private fun CreationExtras.app(): MedApp =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedApp)

/** Single factory wiring every screen ViewModel to the [com.uallsi.medaboutyou.AppContainer]. */
val AppViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { TodayViewModel(app().container) }
    initializer { SearchViewModel(app().container) }
    initializer { DetailViewModel(app().container) }
    initializer { CalendarViewModel(app().container) }
    initializer { SchedulesViewModel(app().container) }
    initializer { InsightsViewModel(app().container) }
    initializer { ActionLogViewModel(app().container) }
    initializer { SettingsViewModel(app().container, app()) }
}

/** Marker base so screen ViewModels share a common type if needed later. */
abstract class MedViewModel : ViewModel()
