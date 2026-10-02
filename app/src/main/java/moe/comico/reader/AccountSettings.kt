package moe.comico.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AccountSettings(state: AppState, model: ReaderViewModel) {
    var form by remember { mutableStateOf<String?>(null) }
    val account = state.account
    if(form != null) AccountDialog(form == "register",account,model,onDismiss = { form = null })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Account",style = MaterialTheme.typography.titleLarge)
        if(account.user != null) {
            ListItem(headlineContent = { Text(account.user.name.ifBlank { account.user.username }) },supportingContent = { Column { Text(account.user.email);Text(if(account.user.verified) "Email verified" else "Email not verified") } },leadingContent = { Icon(Icons.Rounded.Person,null) })
            OutlinedButton(onClick = model::signOut,enabled = !account.loading) { Text("Sign out") }
        } else {
            Text("Sign in to your comico.moe account or create one.",style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { model.clearAccountMessages();form = "login" },enabled = !account.loading) { Text("Sign in") }
                OutlinedButton(onClick = { model.clearAccountMessages();form = "register" },enabled = !account.loading) { Text("Create account") }
            }
        }
        if(account.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        account.error?.let { Text(it,color = MaterialTheme.colorScheme.error);TextButton(onClick = model::refreshAccount,enabled = !account.loading) { Text("Check session again") } }
        account.message?.let { Text(it,style = MaterialTheme.typography.bodyMedium) }
        Text("Passwords aren't saved. Your session is encrypted on this device. Your library and reading preferences remain local; account library sync isn't included yet.",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountDialog(register: Boolean,account: AccountState,model: ReaderViewModel,onDismiss: () -> Unit) {
    var identifier by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val success = account.user != null || account.message?.startsWith("Account created.") == true
    LaunchedEffect(success) { if(success) { password = "";confirmation = "" } }
    val valid = identifier.isNotBlank() && password.isNotEmpty() && (!register || (email.contains('@') && password.length >= 8 && password == confirmation))
    AlertDialog(onDismissRequest = { if(!account.loading) onDismiss() },title = { Text(if(register) "Create a comico.moe account" else "Sign in to comico.moe") },text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if(success) Text(account.message ?: "Signed in.") else {
                OutlinedTextField(value = identifier,onValueChange = { identifier = it },label = { Text(if(register) "Username" else "Email or username") },singleLine = true,enabled = !account.loading,modifier = Modifier.fillMaxWidth())
                if(register) {
                    OutlinedTextField(value = displayName,onValueChange = { displayName = it },label = { Text("Display name · optional") },singleLine = true,enabled = !account.loading,modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email,onValueChange = { email = it },label = { Text("Email") },singleLine = true,enabled = !account.loading,keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(value = password,onValueChange = { password = it },label = { Text("Password") },supportingText = { if(register) Text("At least 8 characters") },singleLine = true,enabled = !account.loading,visualTransformation = if(showPassword) VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if(showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,if(showPassword) "Hide password" else "Show password") } },modifier = Modifier.fillMaxWidth())
                if(register) {
                    OutlinedTextField(value = confirmation,onValueChange = { confirmation = it },label = { Text("Confirm password") },singleLine = true,enabled = !account.loading,visualTransformation = PasswordVisualTransformation(),keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),isError = confirmation.isNotEmpty() && confirmation != password,modifier = Modifier.fillMaxWidth())
                    Text("Comico will send a verification email. Verify your address before signing in.",style = MaterialTheme.typography.bodySmall)
                } else TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("$BASE_URL/forgot-password"))) }) { Text("Forgot password?") }
                account.error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
                if(account.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    },confirmButton = {
        if(success) TextButton(onClick = onDismiss) { Text("Done") }
        else TextButton(onClick = { if(register) model.register(identifier,displayName,email,password) else model.signIn(identifier,password) },enabled = valid && !account.loading) { Text(if(register) "Create account" else "Sign in") }
    },dismissButton = { if(!success) TextButton(onClick = onDismiss,enabled = !account.loading) { Text("Cancel") } })
}
