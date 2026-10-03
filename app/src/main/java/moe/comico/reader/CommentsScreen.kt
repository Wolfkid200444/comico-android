package moe.comico.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(state: AppState, model: ReaderViewModel) {
    PullToRefreshBox(
        isRefreshing = state.accountDataLoading,
        onRefresh = { if (!state.accountDataLoading) model.loadAccountData() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.accountDataError?.let { error -> item {
                Text(error, color = MaterialTheme.colorScheme.error)
            } }
            if (!state.accountDataLoading && state.accountDataError == null && state.comments.isEmpty())
                item { Text("You haven't posted any comments yet.") }
            items(state.comments, key = { it.id }) { comment ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (comment.coverUrl.isNotBlank()) AsyncImage(
                                comment.coverUrl, null, Modifier.width(40.dp).height(56.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            Text(comment.context.ifBlank { "Comment on comico.moe" },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        CommentBody(comment.body)
                        Text(formatAppDate(comment.createdAt, LocalAppearance.current), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
