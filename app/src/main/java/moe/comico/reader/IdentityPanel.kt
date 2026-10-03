package moe.comico.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun IdentityPanel(state: AppState, model: ReaderViewModel) {
    val user = state.account.user ?: return
    val saved = state.profileSettings
    var name by rememberSaveable(user.id, user.name) { mutableStateOf(user.name) }
    var username by rememberSaveable(user.id, user.username) { mutableStateOf(user.username) }
    var bio by rememberSaveable(user.id, saved.bio) { mutableStateOf(saved.bio) }
    var image by rememberSaveable(user.id, saved.image) { mutableStateOf(saved.image) }
    var banner by rememberSaveable(user.id, saved.banner) { mutableStateOf(saved.banner) }
    var gravatar by rememberSaveable(user.id, saved.image) { mutableStateOf(saved.image.isBlank()) }
    var links by rememberSaveable(user.id, saved.links) { mutableStateOf(ArrayList(saved.links)) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val enabled = !state.accountDataLoading && !state.account.loading
    val draft = ProfileEdit(if (gravatar) "" else image, banner, bio, links)
    val error = if (attempted) draft.validationError() else null

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Avatar", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use Gravatar", Modifier.weight(1f))
            Switch(gravatar, { gravatar = it }, enabled = enabled)
        }
        if (gravatar) Text("Uses the Gravatar associated with your account email.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            if (validProfileUrl(image)) AsyncImage(image.trim(), "Avatar preview",
                Modifier.size(72.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            OutlinedTextField(image, { image = it }, label = { Text("Avatar image URL") },
                placeholder = { Text("https://") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                enabled = enabled, isError = attempted && image.isNotBlank() && !validProfileUrl(image),
                supportingText = { Text("Leave blank to use Gravatar.") })
        }
        HorizontalDivider()
        Text("Banner", style = MaterialTheme.typography.titleMedium)
        if (validProfileUrl(banner)) AsyncImage(banner.trim(), "Banner preview",
            Modifier.fillMaxWidth().aspectRatio(3f).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
        OutlinedTextField(banner, { banner = it }, label = { Text("Banner image URL") },
            placeholder = { Text("https://") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            enabled = enabled, isError = attempted && banner.isNotBlank() && !validProfileUrl(banner),
            supportingText = { Text("Leave blank to use the default banner.") })
        HorizontalDivider()
        Text("Identity", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true)
        OutlinedTextField(username, { username = it }, label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true)
        OutlinedTextField(bio, { bio = it }, label = { Text("Bio") },
            modifier = Modifier.fillMaxWidth(), enabled = enabled,
            isError = bio.length > 500, supportingText = { Text("${bio.length} / 500") })
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Social links", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("${links.size} / 5", style = MaterialTheme.typography.labelMedium)
        }
        links.forEachIndexed { index, link ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(link, { value ->
                    links = ArrayList(links).apply { set(index, value) }
                }, label = { Text("Link ${index + 1}") }, placeholder = { Text("https://") },
                    modifier = Modifier.weight(1f), enabled = enabled, singleLine = true,
                    isError = attempted && link.isNotBlank() && (!validProfileUrl(link) || link.trim().length > 200))
                IconButton(onClick = { links = ArrayList(links).apply { removeAt(index) } }, enabled = enabled) {
                    Icon(Icons.Rounded.Close, "Remove link ${index + 1}")
                }
            }
        }
        OutlinedButton(onClick = { links = ArrayList(links).apply { add("") } },
            enabled = enabled && links.size < 5) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add link")
        }
        Text("Add up to 5 links, each starting with https:// or http://.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.accountDataLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        (error ?: state.accountDataError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            attempted = true
            if (draft.validationError() == null) model.saveProfile(name, username, draft)
        }, enabled = enabled && name.isNotBlank() && username.isNotBlank()) {
            Text("Save changes")
        }
    }
}
