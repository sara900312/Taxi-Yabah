package com.yabah.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yabah.app.core.auth.AuthRepository
import com.yabah.app.core.auth.PhoneAuthError
import com.yabah.app.core.auth.PhoneAuthException
import com.yabah.app.core.auth.PhoneNumbers
import com.yabah.app.core.net.ApiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInStep { Phone, Code, Name, Blocked, Done }

data class SignInUiState(
    val otpEnabled: Boolean,
    val step: SignInStep = SignInStep.Phone,
    val phoneInput: String = "",
    val phoneInvalid: Boolean = false,
    val e164: String? = null,
    val codeInput: String = "",
    val nameInput: String = "",
    val nameInvalid: Boolean = false,
    val busy: Boolean = false,
    val resendInSec: Int = 0,
    val error: PhoneAuthError? = null,
)

/**
 * Phone-first sign-in (owner decision 11 Nov 2026). With [otpEnabled] = false (default build) nothing is ever sent:
 * the screen explains that SMS sign-in is coming soon.
 */
class SignInViewModel(private val auth: AuthRepository, otpEnabled: Boolean, private val resendSeconds: Int = 60) : ViewModel() {
    private val _state = MutableStateFlow(SignInUiState(otpEnabled = otpEnabled))
    val state: StateFlow<SignInUiState> = _state.asStateFlow()
    private var timer: Job? = null

    fun onPhone(v: String) = _state.update { it.copy(phoneInput = v.take(20), phoneInvalid = false, error = null) }
    fun onCode(v: String) = _state.update { it.copy(codeInput = PhoneNumbers.clean(v).filter(Char::isDigit).take(6), error = null) }
    fun onName(v: String) = _state.update { it.copy(nameInput = v.take(60), nameInvalid = false) }

    fun sendCode() {
        val s = _state.value
        if (!s.otpEnabled || s.busy || s.resendInSec > 0) return
        val e164 = PhoneNumbers.toE164(s.phoneInput) ?: run { _state.update { it.copy(phoneInvalid = true) }; return }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                auth.sendOtp(e164)
                _state.update { it.copy(busy = false, e164 = e164, step = SignInStep.Code, codeInput = "") }
                startTimer()
            } catch (e: PhoneAuthException) {
                _state.update { it.copy(busy = false, error = e.error) }
            }
        }
    }

    fun changeNumber() { timer?.cancel(); _state.update { it.copy(step = SignInStep.Phone, resendInSec = 0, error = null) } }

    fun verify() {
        val s = _state.value
        val e164 = s.e164 ?: return
        val code = PhoneNumbers.normalizeOtp(s.codeInput) ?: run { _state.update { it.copy(error = PhoneAuthError.OtpInvalidOrExpired) }; return }
        if (s.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                auth.verifyOtp(e164, code)
                routeAfterSession()
            } catch (e: PhoneAuthException) {
                _state.update { it.copy(busy = false, error = e.error) }
            }
        }
    }

    private suspend fun routeAfterSession() {
        try {
            val profile = auth.loadProfile()
            val next = when {
                profile?.accountStatus != null && profile.accountStatus != "active" -> SignInStep.Blocked
                profile?.fullName == null -> SignInStep.Name
                else -> SignInStep.Done
            }
            _state.update { it.copy(busy = false, step = next) }
        } catch (_: ApiException) {
            // Session exists; profile could not be read right now. Let the server gate the next calls.
            _state.update { it.copy(busy = false, step = SignInStep.Done) }
        }
    }

    fun saveName() {
        val name = _state.value.nameInput.trim()
        if (name.length < 2) { _state.update { it.copy(nameInvalid = true) }; return }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { auth.saveName(name); _state.update { it.copy(busy = false, step = SignInStep.Done) } }
            catch (_: ApiException) { _state.update { it.copy(busy = false, error = PhoneAuthError.Unknown) } }
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            for (left in resendSeconds downTo 0) { _state.update { it.copy(resendInSec = left) }; if (left > 0) delay(1_000) }
        }
    }
}
