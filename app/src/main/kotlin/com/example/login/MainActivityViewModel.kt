package com.example.login

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.ShowSignInButton)
    val uiState = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>()
    val event = _event.asSharedFlow()

    fun onSignInWithGoogle() {
        val googleIdOption = GetSignInWithGoogleOption.Builder("986905329207-9p54r3qmctrv969ma4p1np3arf3793mu.apps.googleusercontent.com").build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        sendEvent(
            Event.LaunchCredentialManagerSignIn(
                request = request,
                credentialManager = CredentialManager.create(context)
            )
        )
    }

    private fun sendEvent(event: Event) {
        viewModelScope.launch {
            _event.emit(event)
        }
    }

    fun onCredentialManagerSignedIn(response: GetCredentialResponse) {
        viewModelScope.launch {
            val credential = response.credential

            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                try {
                    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    // Our backend is not waiting for this value, it is waiting for a server auth code
                    // For this reason, we need to make the authorization request to get the server auth code

                    val requestedScopes = listOf(
                        Scope(Scopes.PLUS_ME),
                        Scope(Scopes.PROFILE),
                        Scope("https://www.googleapis.com/auth/user.birthday.read"),
                        Scope("https://www.googleapis.com/auth/user.gender.read"),
                        Scope("https://www.googleapis.com/auth/userinfo.email")
                    )

                    val authorizationRequest =
                        AuthorizationRequest.builder()
                            .setRequestedScopes(requestedScopes)
                            .requestOfflineAccess("986905329207-9p54r3qmctrv969ma4p1np3arf3793mu.apps.googleusercontent.com")
                            .build()
                    sendEvent(Event.LaunchAuthorizationClient(authorizationRequest))
                } catch (e: GoogleIdTokenParsingException) {
                    Timber.e(RuntimeException("Credential Manager Parsing Error. Google Id Token Parsing Exception: $e"))
                }
            } else {
                Timber.e(RuntimeException("Unexpected type of credential"))
            }
        }
    }

    fun setServerAuthCode(serverAuthCode: String?) {
        _uiState.value = serverAuthCode?.let {
            UiState.ShowServerAuthCode(it)
        } ?: UiState.ShowError("Server auth code is null")
    }

    fun showError(errorMessage: String) {
        _uiState.value = UiState.ShowError(errorMessage)
    }

    sealed interface UiState {
        data object ShowSignInButton : UiState
        data class ShowServerAuthCode(val serverAuthCode: String?) : UiState
        data class ShowError(val errorMessage: String) : UiState
    }

    sealed interface Event {
        data class LaunchCredentialManagerSignIn(
            val request: GetCredentialRequest,
            val credentialManager: CredentialManager
        ) : Event

        data class LaunchAuthorizationClient(val authorizationRequest: AuthorizationRequest) : Event
    }
}
