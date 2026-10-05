package moe.comico.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONObject

data class PublicProfile(
    val image: String, val banner: String, val bio: String, val joinedAt: String,
    val level: Int, val exp: Long, val supporter: Boolean, val badges: List<String>,
    val chaptersRead: Int, val titlesFollowed: Int, val comments: Int,
    val badgeArtwork: List<BadgeArtwork> = emptyList()
)
fun JSONObject.publicProfile(): PublicProfile {
    val stats = optJSONObject("stats") ?: JSONObject()
    fun text(key: String) = optString(key).takeUnless { it == "null" }.orEmpty()
    return PublicProfile(text("image"), text("banner"), text("bio"), text("joinedAt"),
        optInt("level"), optLong("exp"), optBoolean("supporter"),
        optJSONArray("icons").objects().map { it.getString("iconId") }.distinct(),
        stats.optInt("chaptersRead"), stats.optInt("titlesFollowed"), stats.optInt("comments"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(state: AppState, model: ReaderViewModel) {
    val user = state.account.user
    val profile = state.publicProfile
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = state.accountDataLoading,
        onRefresh = { if(!state.offline && user != null && !state.accountDataLoading) model.loadAccountData() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if(user != null) item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Box(Modifier.fillMaxWidth().height(132.dp)
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(
                            MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)))) {
                        if(!profile?.banner.isNullOrBlank()) AsyncImage(profile?.banner, "Profile banner",
                            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                if(!profile?.image.isNullOrBlank()) AsyncImage(profile?.image, "Profile avatar",
                                    Modifier.size(72.dp), contentScale = ContentScale.Crop)
                                else Icon(Icons.Rounded.Person, null, Modifier.size(72.dp).padding(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(user.name.ifBlank { user.username }, style = MaterialTheme.typography.headlineSmall)
                                Text("@${user.username}", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                profile?.let {
                                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("Level ${it.level}", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                        }
                        if(!profile?.bio.isNullOrBlank()) Text(profile?.bio.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        profile?.let {
                            if(it.badgeArtwork.isNotEmpty()) {
                                Text("Badges", style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    it.badgeArtwork.forEach { badge ->
                                        BadgeButton(badge)
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ProfileStat("Comments", it.comments, Modifier.weight(1f))
                                ProfileStat("Chapters read", it.chaptersRead, Modifier.weight(1f))
                                ProfileStat("Following", it.titlesFollowed, Modifier.weight(1f))
                            }
                            val experience = levelProgress(it.exp)
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Experience", style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        if (experience.level >= 100) "Max level"
                                        else "${experience.done} / ${experience.step} to level ${experience.level + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (experience.done.toFloat() / experience.step).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    }
                }
            }
            if(user == null) item {
                MessageCard(Icons.Rounded.AccountCircle, "Your reading profile",
                    "Sign in from Settings to see your badges, level and reading stats.")
            }
            item {
                Surface(onClick = { model.tab("Statistics") }, shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    ListItem(headlineContent = { Text("Statistics") },
                        supportingContent = { Text("Reading, library and downloads") },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        leadingContent = { Icon(Icons.Rounded.BarChart, null) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) })
                }
            }
            state.accountDataError?.let { error -> item {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            } }
            item {
                Surface(onClick = { model.tab("Settings") }, shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    ListItem(headlineContent = { Text("Settings") },
                        supportingContent = { Text("Account, profile and preferences") },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        leadingContent = { Icon(Icons.Rounded.Settings, null) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) })
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
