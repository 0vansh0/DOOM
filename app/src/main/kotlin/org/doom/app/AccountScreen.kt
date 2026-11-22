package org.doom.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.doom.core.designsystem.Page

@Composable
internal fun AccountScreen(
    state: AccountUiState,
    signIn: (String, String) -> Unit,
    register: (String, String, String) -> Unit,
    logout: () -> Unit,
    back: () -> Unit
) = Page("DOOM account") {
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords remain transient and are not included in saved-instance state.
    var password by remember { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val emailValid = email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
    val passwordValid = password.isNotEmpty() && password.length <= 128 &&
        (!createAccount || password.length >= 12)

    TextButton(onClick = back) { Text("Back") }
    Text("Account", style = MaterialTheme.typography.headlineSmall)
    Text("Sign in to your DOOM account.")
    if (state.session != null) {
        Text("Signed in as ${state.session.email}")
        state.expired.let { if (it) Text("Your session expired. Sign in again.") }
        OutlinedButton(onClick = logout, enabled = !state.loading) { Text("Log out") }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !createAccount, onClick = { createAccount = false }, label = { Text("Sign in") })
            FilterChip(selected = createAccount, onClick = { createAccount = true }, label = { Text("Register") })
        }
        if (createAccount) {
            OutlinedTextField(displayName, { displayName = it.take(80) },
                label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(email, { email = it.take(320) }, label = { Text("Email") },
            isError = email.isNotEmpty() && !emailValid, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it.take(128) }, label = { Text("Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            }, modifier = Modifier.fillMaxWidth())
        if (createAccount && password.isNotEmpty() && password.length < 12) {
            Text("Use at least 12 characters.")
        }
        Button(enabled = !state.loading && emailValid && passwordValid &&
            (!createAccount || displayName.trim().length >= 2), onClick = {
            if (createAccount) register(email.trim(), password, displayName.trim())
            else signIn(email.trim(), password)
        }) {
            if (state.loading) CircularProgressIndicator()
            else Text(if (createAccount) "Create account" else "Sign In")
        }
        if (state.loading) Text(if (state.registering) "Creating account…" else "Signing in…")
        if (state.expired) Text("Your session expired. Sign in again.")
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
