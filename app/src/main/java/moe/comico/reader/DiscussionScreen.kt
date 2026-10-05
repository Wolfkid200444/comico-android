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
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(state.replyTo?.id) { if (state.replyTo != null) listState.animateScrollToItem(0) }
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
                LazyColumn(state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if(signedIn) {
                                state.replyTo?.let { reply -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Replying to ${reply.author}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                    TextButton(onClick = { controller.reply(null) }, enabled = !state.posting) { Text("Cancel reply") }
                                } }
                                OutlinedTextField(value = state.draft, onValueChange = controller::draft, modifier = Modifier.fillMaxWidth(),
                                    label = { Text(if (state.replyTo == null) "Write a comment" else "Write a reply") }, minLines = 3,
                                    enabled = !state.posting && state.voting == null, supportingText = { Text("${state.draft.length} / 5,000") },
                                    isError = state.draft.length > COMMENT_LIMIT)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { picker.launch("image/*") }, enabled = !state.uploading && !state.posting && state.voting == null) {
                                        Icon(Icons.Rounded.AddPhotoAlternate, "Upload image")
                                    }
                                    if(state.uploading) { CircularProgressIndicator(Modifier.size(20.dp)); Text(" Uploading image…", style = MaterialTheme.typography.bodySmall) }
                                    Spacer(Modifier.weight(1f))
                                    Button(onClick = controller::post, enabled = !state.posting && !state.uploading && state.voting == null && commentValidation(state.draft) == null) {
                                        Text(if(state.posting) "Posting…" else if (state.replyTo != null) "Reply" else "Post")
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
                            DiscussionCommentView(comment, signedIn && state.voting == null && !state.posting && !state.uploading, controller, state.voting)
                            HorizontalDivider(Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscussionCommentView(comment: DiscussionComment, enabled: Boolean, controller: DiscussionController, busyId: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(comment.author, style = MaterialTheme.typography.titleSmall)
        CommentBody(comment.body)
        if (!comment.deleted) CommentActions(comment.likes, comment.dislikes, comment.vote, comment.reactions, enabled,
            onVote = { controller.vote(comment, it) }, onReact = { controller.react(comment, it) },
            onReply = { controller.reply(comment) }, onDelete = if (comment.isOwn) ({ controller.delete(comment) }) else null)
        if (busyId == comment.id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(formatAppDate(comment.createdAt, LocalAppearance.current), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        comment.replies.forEach { reply ->
            Row {
                VerticalDivider(Modifier.height(32.dp).padding(end = 12.dp))
                Column(Modifier.weight(1f)) { DiscussionCommentView(reply, enabled, controller, busyId) }
            }
        }
    }
}
@Composable
fun CommentVotes(likes: Int, dislikes: Int, selectedVote: Int, enabled: Boolean, countsAvailable: Boolean = true, onVote: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selectedVote == 1, onClick = { onVote("like") }, enabled = enabled,
            label = { Text(if (countsAvailable) likes.toString() else "?") },
            leadingIcon = { Icon(Icons.Rounded.ThumbUp, if (selectedVote == 1) "Remove like" else "Like comment", Modifier.size(18.dp)) })
        FilterChip(selected = selectedVote == -1, onClick = { onVote("dislike") }, enabled = enabled,
            label = { Text(if (countsAvailable) dislikes.toString() else "?") },
            leadingIcon = { Icon(Icons.Rounded.ThumbDown, if (selectedVote == -1) "Remove dislike" else "Dislike comment", Modifier.size(18.dp)) })
    }
}
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CommentActions(likes: Int, dislikes: Int, selectedVote: Int, reactions: List<CommentReaction>, enabled: Boolean,
    countsAvailable: Boolean = true, onVote: (String) -> Unit, onReact: (String) -> Unit,
    onReply: () -> Unit, onDelete: (() -> Unit)? = null) {
    var choosingReaction by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    if (confirmingDelete) AlertDialog(onDismissRequest = { confirmingDelete = false },
        title = { Text("Delete comment?") }, text = { Text("This permanently removes your comment.") },
        confirmButton = { TextButton(onClick = { confirmingDelete = false; onDelete?.invoke() }, enabled = enabled) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } })
    if (choosingReaction) AlertDialog(onDismissRequest = { choosingReaction = false }, title = { Text("React to comment") },
        text = { FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("😀", "😃", "😄", "😁", "😂", "🤣", "😊", "😍", "🥰", "😘", "😎", "🤔", "😮", "😅",
                "😭", "😢", "😡", "🤯", "😱", "😴", "🤡", "💀", "👀", "🙌", "👏", "🙏", "👍", "👎",
                "❤️", "💔", "🔥", "✨", "🎉", "💯", "✅", "❌", "❓", "❗").forEach { emoji ->
                TextButton(onClick = { choosingReaction = false; onReact(emoji) }, enabled = enabled,
                    contentPadding = PaddingValues(4.dp), modifier = Modifier.width(48.dp)) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
            }
        } }, confirmButton = { TextButton(onClick = { choosingReaction = false }) { Text("Cancel") } })
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.Center) {
            CommentVotes(likes, dislikes, selectedVote, enabled, countsAvailable, onVote)
            IconButton(onClick = { choosingReaction = true }, enabled = enabled) { Icon(Icons.Rounded.AddReaction, "React to comment") }
            TextButton(onClick = onReply, enabled = enabled) { Text("Reply") }
            if (onDelete != null) TextButton(onClick = { confirmingDelete = true }, enabled = enabled) { Text("Delete") }
        }
        if (reactions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            reactions.forEach { reaction ->
                FilterChip(selected = reaction.reacted, onClick = { onReact(reaction.emoji) }, enabled = enabled,
                    label = { Text("${reaction.emoji} ${reaction.count}") })
            }
        }
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
