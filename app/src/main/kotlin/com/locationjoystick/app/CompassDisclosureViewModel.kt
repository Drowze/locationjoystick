package com.locationjoystick.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Records the user's answer to the compass accessibility disclosure and reports whether it is still
 * unanswered, so [MainActivity] can show it on the first launch whatever screen or API level the user lands on.
 */
@HiltViewModel
class CompassDisclosureViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _unanswered = MutableStateFlow(false)
        val unanswered: StateFlow<Boolean> = _unanswered.asStateFlow()

        init {
            viewModelScope.launch {
                _unanswered.value =
                    settingsRepository.getCompassDisclosureChoice().first() ==
                    AppConstants.CompassTrackingConstants.DISCLOSURE_UNANSWERED
            }
        }

        fun record(accepted: Boolean) {
            // Flip locally first so the dialog cannot re-open before DataStore round-trips.
            _unanswered.value = false
            viewModelScope.launch { settingsRepository.recordCompassDisclosure(accepted) }
        }
    }
