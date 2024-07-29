package com.example.login

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.Identity

@Composable
fun MainScreen(
    viewModel: MainActivityViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val authorizationLauncher = rememberAuthorizationLauncher(context = context)

    val state = viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (state.value) {
                MainActivityViewModel.UiState.ShowSignInButton -> {
                    Button(onClick = viewModel::onSignInWithGoogle) {
                        Text(text = "Sign in with Google")
                    }
                }

                is MainActivityViewModel.UiState.ShowServerAuthCode -> {
                    val serverAuthCode =
                        (state.value as MainActivityViewModel.UiState.ShowServerAuthCode).serverAuthCode
                    Text(text = serverAuthCode ?: "serverAuthCode is null")
                }

                is MainActivityViewModel.UiState.ShowError -> {
                    val errorMessage = (state.value as MainActivityViewModel.UiState.ShowError).errorMessage
                    Text(text = errorMessage)
                }
            }
        }
    }

    LaunchedEffect(key1 = viewModel.event) {
        viewModel.event.collect { event ->
            when (event) {
                is MainActivityViewModel.Event.LaunchCredentialManagerSignIn -> try {
                    val response = event.credentialManager.getCredential(context, event.request)
                    viewModel.onCredentialManagerSignedIn(response)
                } catch (e: GetCredentialException) {
                    viewModel.showError("Get credential exception: ${e.message}")
                }

                is MainActivityViewModel.Event.LaunchAuthorizationClient -> {
                    Identity.getAuthorizationClient(context)
                        .authorize(event.authorizationRequest)
                        .addOnSuccessListener { authorizationResult ->
                            if (authorizationResult.hasResolution()) {
                                authorizationResult.pendingIntent?.intentSender?.let { intentSender ->
                                    authorizationLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                }
                            } else {
                                viewModel.setServerAuthCode(authorizationResult.serverAuthCode)
                            }
                        }
                        .addOnFailureListener { e ->
                            viewModel.showError("Authorization request exception: $e")
                        }
                }
            }
        }
    }
}

@Composable
private fun rememberAuthorizationLauncher(
    context: Context
) = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartIntentSenderForResult()
) { activityResult ->
    if (activityResult.resultCode == Activity.RESULT_OK) {
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(activityResult.data)
    }
}
