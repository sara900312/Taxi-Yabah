package com.yabah.app.feature.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yabah.app.core.model.TripDetails
import com.yabah.app.core.net.ApiException
import com.yabah.app.core.net.ApiFailure
import com.yabah.app.data.repo.RideRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ActionUi {
    data object Idle : ActionUi
    data object Running : ActionUi
    data class Failed(val failure: ApiFailure) : ActionUi
    data object Done : ActionUi
}

data class TripUiState(
    val details: TripDetails? = null,
    val loading: Boolean = true,
    val failure: ApiFailure? = null,
    val lastCheckedMs: Long? = null,
    val cancel: ActionUi = ActionUi.Idle,
    val stars: Int = 0,
    val rating: ActionUi = ActionUi.Idle,
    val sessionExpired: Boolean = false,
) {
    /** Minutes since the captain's last location fix, measured on the SERVER clock; null if unknown. */
    val captainLocationAgeMin: Int?
        get() {
            val d = details ?: return null
            val at = d.captainLocation?.updatedAtMs ?: return null
            return ((d.serverNowMs - at) / 60_000).toInt().coerceAtLeast(0)
        }
    val captainLocationStale: Boolean get() = (captainLocationAgeMin ?: 0) >= STALE_MINUTES

    companion object { const val STALE_MINUTES = 2 }
}

/**
 * Active trip, driven only by the server (trip_details). Polls while the screen is visible; Realtime may later be
 * added as a hint to refresh sooner, but polling stays as the fallback.
 */
class TripViewModel(
    private val tripId: String,
    private val repo: RideRepository,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val baseIntervalMs: Long = 5_000,
) : ViewModel() {
    private val _state = MutableStateFlow(TripUiState())
    val state: StateFlow<TripUiState> = _state.asStateFlow()
    private var pollJob: Job? = null

    /** Called when the screen becomes visible (also on app resume): re-read immediately, then poll. */
    fun onVisible() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var interval = baseIntervalMs
            while (isActive) {
                val ok = refreshOnce()
                val status = _state.value.details?.status
                if (status != null && !status.isActive) break // terminal: stop polling
                interval = if (ok) baseIntervalMs else (interval * 2).coerceAtMost(30_000)
                delay(interval)
            }
        }
    }

    fun onHidden() { pollJob?.cancel(); pollJob = null }

    fun refresh() { viewModelScope.launch { refreshOnce() } }

    private suspend fun refreshOnce(): Boolean = try {
        val d = repo.tripDetails(tripId)
        _state.update { it.copy(details = d, loading = false, failure = null, lastCheckedMs = nowMs()) }
        true
    } catch (e: ApiException) {
        _state.update { it.copy(loading = false, failure = e.failure, sessionExpired = it.sessionExpired || e.failure.isSessionProblem) }
        false
    }

    fun cancelTrip() {
        val s = _state.value
        val d = s.details ?: return
        if (!d.status.passengerMayCancel || s.cancel == ActionUi.Running) return
        _state.update { it.copy(cancel = ActionUi.Running) }
        viewModelScope.launch {
            try {
                repo.cancelTrip(tripId, null)
                _state.update { it.copy(cancel = ActionUi.Done) }
            } catch (e: ApiException) {
                // A lost answer is reconciled by the refresh below; the server's state is shown either way.
                _state.update { it.copy(cancel = ActionUi.Failed(e.failure)) }
            }
            refreshOnce()
        }
    }

    fun setStars(stars: Int) { if (_state.value.rating != ActionUi.Done) _state.update { it.copy(stars = stars.coerceIn(1, 5)) } }

    fun submitRating() {
        val s = _state.value
        val d = s.details ?: return
        if (s.stars !in 1..5 || d.myRatingSubmitted || s.rating == ActionUi.Running || s.rating == ActionUi.Done) return
        _state.update { it.copy(rating = ActionUi.Running) }
        viewModelScope.launch {
            try {
                repo.rateTrip(tripId, s.stars, null)
                _state.update { it.copy(rating = ActionUi.Done) }
            } catch (e: ApiException) {
                val done = e.failure.code == "ALREADY_RATED"
                _state.update { it.copy(rating = if (done) ActionUi.Done else ActionUi.Failed(e.failure)) }
            }
        }
    }
}
