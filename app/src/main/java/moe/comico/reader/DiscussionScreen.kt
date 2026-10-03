@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package moe.comico.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage

private const val COMMENT_GUIDE = """Use Markdown to format your comment. Keep formatting readable and use a warning before spoilers.

Markdown
**bold**
*italic*
[link text](https://example.com)
> quote
Image
![description](https://example.com/image.png)"""
private const val COMMENT_POLICY = "Be respectful. Do not post spam, harassment, spoilers without warning, illegal content, or personal information. Keep comments relevant to the series. Images must be safe to view and hosted over HTTPS."

@Composable
fun DiscussionDialog(state: DiscussionState, controller: DiscussionController, signedIn: Boolean) {
    val target = state.target ?: return
    var guide by remember { mutableStateOf(false) }
    var policy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(controller::upload) }
    if(guide || policy) AlertDialog(onDismissRequest = { guide = false; policy = false },
        title = { Text(if(guide) "Comment formatting" else "Comment policy") },
        text = { Text(if(guide) COMMENT_GUIDE else COMMENT_POLICY) },
        confirmButton = { TextButton(onClick = { guide = false; policy = false }) { Text("Got it") } })
    Dialog(onDismissRequest = controller::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(modifier = Modifier.fillMaxSize().imePadding(),
            topBar = { TopAppBar(title = { Column {
                Text(if(target.chapter) "Chapter comments" else "Manga comments", style = MaterialTheme.typography.titleMedium)
                Text(target.title, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            } }, navigationIcon = { IconButton(onClick = controller::close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) }) { padding ->
            PullToRefreshBox(isRefreshing = state.loading, onRefresh = controller::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if(signedIn) {
                                OutlinedTextField(value = state.draft, onValueChange = controller::draft, modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Write a comment") }, minLines = 3,
                                    enabled = !state.posting, supportingText = { Text("${state.draft.length} / 5,000") },
                                    isError = state.draft.length > COMMENT_LIMIT)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { picker.launch("image/*") }, enabled = !state.uploading && !state.posting) {
                                        Icon(Icons.Rounded.AddPhotoAlternate, "Upload image")
                                    }
                                    if(state.uploading) { CircularProgressIndicator(Modifier.size(20.dp)); Text(" Uploading image…", style = MaterialTheme.typography.bodySmall) }
                                    Spacer(Modifier.weight(1f))
                                    Button(onClick = controller::post, enabled = !state.posting && !state.uploading && commentValidation(state.draft) == null) {
                                        Text(if(state.posting) "Posting…" else "Post")
                                    }
                                }
                                if(state.draft.isNotBlank()) {
                                    Text("Preview", style = MaterialTheme.typography.labelLarge)
                                    CommentBody(state.draft)
                                }
                            } else Text("Sign in from Profile to post comments and upload images.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row {
                                TextButton(onClick = { guide = true }) { Text("Markdown guide") }
                                TextButton(onClick = { policy = true }) { Text("Comment policy") }
                            }
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            HorizontalDivider()
                        }
                    }
                    if(!state.loading && state.comments.isEmpty()) item { Text("No comments yet. Start the conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(state.comments, key = { it.id }) { comment ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiscussionCommentView(comment)
                            comment.replies.forEach { reply -> Column(Modifier.padding(start = 20.dp)) { DiscussionCommentView(reply) } }
                            HorizontalDivider(Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscussionCommentView(comment: DiscussionComment) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(comment.author, style = MaterialTheme.typography.titleSmall)
        CommentBody(comment.body)
        Text(formatAppDate(comment.createdAt, LocalAppearance.current), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
fun CommentBody(body: String) {
    val matches = remember(body) { commentImagePattern.findAll(body).toList() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var offset = 0
        matches.forEach { match ->
            if(match.range.first > offset) MarkdownText(body.substring(offset, match.range.first))
            val url = commentImageUrl(match.groupValues[2])
            if(url != null) SubcomposeAsyncImage(model = url, contentDescription = match.groupValues[1].ifBlank { "Comment image" },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp, max = 360.dp),
                loading = { Box(Modifier.height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                error = { Text("Could not load image", color = MaterialTheme.colorScheme.onSurfaceVariant) })
            else Text(match.value)
            offset = match.range.last + 1
        }
        if(offset < body.length) MarkdownText(body.substring(offset))
    }
}
@Composable
private fun MarkdownText(body: String) {
    val colors = MaterialTheme.colorScheme
    val handler = androidx.compose.ui.platform.LocalUriHandler.current
    val text = remember(body, colors.primary) {
        androidx.compose.ui.text.buildAnnotatedString {
            val pattern = Regex("\\*\\*([^*]+)\\*\\*|\\*([^*]+)\\*|\\[([^\\]]+)\\]\\((https?://[^\\s)]+)\\)")
            var position = 0
            pattern.findAll(body).forEach { match ->
                append(body.substring(position, match.range.first))
                val start = length
                val label = match.groupValues[1].ifEmpty { match.groupValues[2].ifEmpty { match.groupValues[3] } }
                append(label)
                if(match.groupValues[1].isNotEmpty()) addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), start, length)
                else if(match.groupValues[2].isNotEmpty()) addStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), start, length)
                else {
                    addStyle(androidx.compose.ui.text.SpanStyle(color = colors.primary, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), start, length)
                    addStringAnnotation("URL", match.groupValues[4], start, length)
                }
                position = match.range.last + 1
            }
            append(body.substring(position))
        }
    }
    androidx.compose.foundation.text.ClickableText(text = text, style = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        onClick = { index -> text.getStringAnnotations("URL", index, index).firstOrNull()?.let { runCatching { handler.openUri(it.item) } } })
}
