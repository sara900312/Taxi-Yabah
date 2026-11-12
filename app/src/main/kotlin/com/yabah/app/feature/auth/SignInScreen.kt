package com.yabah.app.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yabah.app.R
import com.yabah.app.core.auth.PhoneAuthError
import com.yabah.app.core.auth.PhoneNumbers
import com.yabah.app.core.util.UiText
import com.yabah.app.ui.components.BadgeTone
import com.yabah.app.ui.components.MessageCard
import com.yabah.app.ui.components.YabahPrimaryButton
import com.yabah.app.ui.components.YabahSecondaryButton
import com.yabah.app.ui.theme.Spacing

@Composable
fun SignInScreen(vm: SignInViewModel, onSignedIn: () -> Unit, onTryDemo: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.step) { if (s.step == SignInStep.Done) onSignedIn() }
    val ltrInput = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(Spacing.xl).testTag("signin_screen"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xl))
        Image(painterResource(R.drawable.ic_yabah_mark), null, Modifier.size(96.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.slogan), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.lg))

        when (s.step) {
            SignInStep.Phone -> {
                Text(stringResource(R.string.signin_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                OutlinedTextField(
                    value = s.phoneInput, onValueChange = vm::onPhone, enabled = s.otpEnabled && !s.busy,
                    label = { Text(stringResource(R.string.phone_label)) }, placeholder = { Text(stringResource(R.string.phone_hint)) },
                    isError = s.phoneInvalid, singleLine = true, textStyle = ltrInput,
                    supportingText = { if (s.phoneInvalid) Text(stringResource(R.string.phone_invalid)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("phone_field"),
                )
                if (!s.otpEnabled) MessageCard(UiText.Res(R.string.otp_coming_soon), BadgeTone.Info, Modifier.testTag("otp_coming_soon"))
                YabahPrimaryButton(stringResource(R.string.send_code), vm::sendCode, enabled = s.otpEnabled, loading = s.busy,
                    modifier = Modifier.testTag("send_code"))
            }
            SignInStep.Code -> {
                Text(stringResource(R.string.code_sent_to, PhoneNumbers.display(s.e164.orEmpty())), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                OutlinedTextField(
                    value = s.codeInput, onValueChange = vm::onCode, label = { Text(stringResource(R.string.code_label)) },
                    singleLine = true, textStyle = ltrInput, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("code_field"),
                )
                YabahPrimaryButton(stringResource(R.string.verify), vm::verify, enabled = s.codeInput.length == 6, loading = s.busy)
                if (s.resendInSec > 0) Text(stringResource(R.string.resend_in, s.resendInSec), style = MaterialTheme.typography.bodySmall)
                else TextButton(onClick = vm::sendCode) { Text(stringResource(R.string.resend)) }
                TextButton(onClick = vm::changeNumber) { Text(stringResource(R.string.change_number)) }
            }
            SignInStep.Name -> {
                Text(stringResource(R.string.name_title), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = s.nameInput, onValueChange = vm::onName, label = { Text(stringResource(R.string.name_label)) },
                    isError = s.nameInvalid, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { if (s.nameInvalid) Text(stringResource(R.string.name_invalid)) },
                )
                YabahPrimaryButton(stringResource(R.string.continue_btn), vm::saveName, loading = s.busy)
            }
            SignInStep.Blocked -> MessageCard(UiText.Res(R.string.account_suspended), BadgeTone.Danger)
            SignInStep.Done -> Unit
        }
        s.error?.let { MessageCard(UiText.Res(it.messageRes()), BadgeTone.Danger) }

        if (s.step == SignInStep.Phone) {
            Spacer(Modifier.height(Spacing.lg))
            YabahSecondaryButton(stringResource(R.string.try_demo), onTryDemo, Modifier.testTag("try_demo"))
        }
    }
}

private fun PhoneAuthError.messageRes(): Int = when (this) {
    PhoneAuthError.InvalidPhone -> R.string.phone_invalid
    PhoneAuthError.OtpInvalidOrExpired -> R.string.err_otp_invalid
    PhoneAuthError.OtpRateLimited -> R.string.err_otp_rate_limited
    PhoneAuthError.SignInUnavailable -> R.string.err_phone_signin_unavailable
    PhoneAuthError.AccountConflict -> R.string.err_phone_conflict
    PhoneAuthError.Offline -> R.string.err_offline
    PhoneAuthError.Unknown -> R.string.err_generic
}
