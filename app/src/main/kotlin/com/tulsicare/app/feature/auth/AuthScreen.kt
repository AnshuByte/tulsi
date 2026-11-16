package com.tulsicare.app.feature.auth

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.tulsicare.app.BuildConfig
import com.tulsicare.app.R
import com.tulsicare.app.core.designsystem.TulsiBorderWidth
import com.tulsicare.app.core.designsystem.TulsiCornerRadius
import com.tulsicare.app.core.designsystem.TulsiScreenPadding
import com.tulsicare.app.core.designsystem.TulsiTheme
import com.tulsicare.app.core.designsystem.component.TulsiButton
import com.tulsicare.app.core.designsystem.component.TulsiGoogleSignInButton
import com.tulsicare.app.core.designsystem.component.TulsiOutlinedButton
import com.tulsicare.app.core.designsystem.component.TulsiTextButton
import com.tulsicare.app.core.designsystem.component.TulsiTextField
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGoogleLoading by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.currentUser) {
        if (uiState.currentUser != null) {
            onAuthSuccess()
        }
    }

    AuthScreenContent(
        uiState = uiState,
        isGoogleLoading = isGoogleLoading,
        onGoogleSignInClick = {
            if (isGoogleLoading || uiState.isLoading) return@AuthScreenContent
            coroutineScope.launch {
                isGoogleLoading = true
                launchCredentialManagerGoogleSignIn(
                    context = context,
                    onIdTokenReceived = { idToken ->
                        viewModel.signInWithGoogleIdToken(idToken)
                    },
                    onError = { message ->
                        viewModel.setErrorMessage(message)
                    },
                    onComplete = {
                        isGoogleLoading = false
                    }
                )
            }
        },
        onEmailChange = viewModel::setEmail,
        onPasswordChange = viewModel::setPassword,
        onDisplayNameChange = viewModel::setDisplayName,
        onSubmit = viewModel::submit,
        onToggleMode = viewModel::toggleAuthMode,
        onQuickSignIn = viewModel::quickSignIn,
        modifier = modifier
    )
}

@Composable
fun AuthScreenContent(
    uiState: AuthUiState,
    isGoogleLoading: Boolean,
    onGoogleSignInClick: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit,
    onQuickSignIn: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TulsiTheme.colors
    val focusManager = LocalFocusManager.current
    var showDevAccounts by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TulsiScreenPadding, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // Centered small leaf logo
                Image(
                    painter = painterResource(id = R.drawable.ic_tulsi_logo),
                    contentDescription = "Tulsi Logo",
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // "Tulsi" wordmark (28sp Title)
                Text(
                    text = "Tulsi",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // One line of subtext
                Text(
                    text = "Family medicine reminder",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Full-width "Continue with Google" button
                TulsiGoogleSignInButton(
                    onClick = onGoogleSignInClick,
                    isLoading = isGoogleLoading,
                    enabled = !isGoogleLoading && !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Thin "or" divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = TulsiBorderWidth,
                        color = colors.border
                    )
                    Text(
                        text = "or",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = TulsiBorderWidth,
                        color = colors.border
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Optional name for registration
                if (uiState.isSignUp) {
                    TulsiTextField(
                        value = uiState.displayName,
                        onValueChange = onDisplayNameChange,
                        placeholder = "Your name",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Email field
                TulsiTextField(
                    value = uiState.email,
                    onValueChange = onEmailChange,
                    placeholder = "Email address",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
                TulsiTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    placeholder = "Password",
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        onSubmit()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )

                // Error message
                if (!uiState.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.errorMessage,
                        fontSize = 14.sp,
                        color = colors.statusMissed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Solid primary button ("Sign in" / "Create account")
                TulsiButton(
                    text = if (uiState.isSignUp) "Create account" else "Sign in",
                    onClick = {
                        focusManager.clearFocus()
                        onSubmit()
                    },
                    isLoading = uiState.isLoading,
                    enabled = !uiState.isLoading && !isGoogleLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Text link to switch mode
                TulsiTextButton(
                    text = if (uiState.isSignUp) "Already have an account? Sign in" else "Create account",
                    onClick = onToggleMode,
                    textColor = colors.textSecondary
                )
            }

            // Developer test accounts: ONLY rendered under BuildConfig.DEBUG, never in release builds
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(32.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { showDevAccounts = !showDevAccounts }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = colors.textDisabled,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Developer: test accounts",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textDisabled
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showDevAccounts) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = colors.textDisabled,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (showDevAccounts) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TulsiOutlinedButton(
                                text = "Asha (Admin)",
                                onClick = { onQuickSignIn("ADMIN") },
                                modifier = Modifier.weight(1f)
                            )
                            TulsiOutlinedButton(
                                text = "Ravi (Patient)",
                                onClick = { onQuickSignIn("PATIENT") },
                                modifier = Modifier.weight(1f)
                            )
                            TulsiOutlinedButton(
                                text = "Meera",
                                onClick = { onQuickSignIn("MEMBER") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Native Credential Manager sign-in helper:
 * - Clears stale credential state first
 * - Uses GetGoogleIdOption with filterByAuthorizedAccounts = false and autoSelectEnabled = false
 *   so the native Google account bottom-sheet picker displays ALL accounts on the device
 * - Catches cancellation silently, returns friendly messages on network/no-account errors
 */
private suspend fun launchCredentialManagerGoogleSignIn(
    context: Context,
    onIdTokenReceived: (String) -> Unit,
    onError: (String) -> Unit,
    onComplete: () -> Unit
) {
    try {
        val serverClientId = context.getString(R.string.default_web_client_id)
        if (serverClientId.isBlank()) {
            onError("Google Web Client ID is not configured. Please add SHA-1 to Firebase and update google-services.json.")
            return
        }

        val credentialManager = CredentialManager.create(context)

        // Clear any stale cached credential state so the user is free to pick any account
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {}

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialResponse = try {
            credentialManager.getCredential(
                request = request,
                context = context
            )
        } catch (e: GetCredentialCancellationException) {
            // User dismissed the account picker; exit quietly
            return
        } catch (e: NoCredentialException) {
            onError("No Google account found on device.")
            return
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Google sign in failed."
            onError(if (msg.contains("network", ignoreCase = true)) "Network connection error." else msg)
            return
        }

        val credential = credentialResponse.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            onIdTokenReceived(googleIdTokenCredential.idToken)
        } else {
            onError("Unable to retrieve Google credentials.")
        }
    } catch (e: Exception) {
        val msg = e.localizedMessage ?: "Authentication error."
        onError(msg)
    } finally {
        onComplete()
    }
}

// -----------------------------------------------------------------------------
// Previews for Light theme, Dark theme, and 1.5x Font Scale
// -----------------------------------------------------------------------------

@Preview(name = "Light Mode", showBackground = true)
@Composable
fun AuthScreenPreviewLight() {
    TulsiTheme(darkTheme = false) {
        AuthScreenContent(
            uiState = AuthUiState(),
            isGoogleLoading = false,
            onGoogleSignInClick = {},
            onEmailChange = {},
            onPasswordChange = {},
            onDisplayNameChange = {},
            onSubmit = {},
            onToggleMode = {},
            onQuickSignIn = {}
        )
    }
}

@Preview(name = "Dark Mode", showBackground = true)
@Composable
fun AuthScreenPreviewDark() {
    TulsiTheme(darkTheme = true) {
        AuthScreenContent(
            uiState = AuthUiState(),
            isGoogleLoading = false,
            onGoogleSignInClick = {},
            onEmailChange = {},
            onPasswordChange = {},
            onDisplayNameChange = {},
            onSubmit = {},
            onToggleMode = {},
            onQuickSignIn = {}
        )
    }
}

@Preview(name = "1.5x Font Scale", fontScale = 1.5f, showBackground = true)
@Composable
fun AuthScreenPreviewLargeFont() {
    TulsiTheme(darkTheme = false) {
        AuthScreenContent(
            uiState = AuthUiState(),
            isGoogleLoading = false,
            onGoogleSignInClick = {},
            onEmailChange = {},
            onPasswordChange = {},
            onDisplayNameChange = {},
            onSubmit = {},
            onToggleMode = {},
            onQuickSignIn = {}
        )
    }
}
