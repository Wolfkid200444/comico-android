@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package moe.comico.reader

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

@Composable
fun ReaderScreen(chapter: Chapter, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val url = "$BASE_URL/reader/${chapter.id}"
    var loading by remember(chapter.id) { mutableStateOf(true) }
    var error by remember(chapter.id) { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    BackHandler { onBack() }
    DisposableEffect(chapter.id) { onDispose { webView?.stopLoading(); webView?.destroy(); webView = null } }
    fun openBrowser(target: String) {
        val uri = Uri.parse(target)
        if(uri.scheme in listOf("https", "http")) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure { error = "No browser is available to open this chapter." }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Column { Text("Chapter ${chapter.number}", style = MaterialTheme.typography.titleMedium); Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to chapters") } }, actions = {
            if(chapter.external.isEmpty()) IconButton(onClick = { error = null; loading = true; webView?.reload() }) { Icon(Icons.Rounded.Refresh, "Reload reader") }
            IconButton(onClick = { openBrowser(chapter.external.ifBlank { url }) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open chapter in browser") }
        })
    }) { padding ->
        if(chapter.external.isNotEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Read at the source", style = MaterialTheme.typography.headlineMedium)
                Text("This chapter is hosted by its publisher. Open the original reader to continue.")
                Button(onClick = { openBrowser(chapter.external) }) { Text("Open publisher reader") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        } else Box(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                WebView(ctx).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient = object: WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if(request.url.scheme == "https" && request.url.host == "comico.moe") return false
                            openBrowser(request.url.toString()); return true
                        }
                        override fun onPageFinished(view: WebView, url: String) { loading = false }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                            if(request.isForMainFrame) { loading = false; error = "The reader couldn't connect. Check your connection and reload." }
                        }
                    }
                    loadUrl(url)
                }
            })
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            error?.let { message -> Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) { Column(Modifier.padding(16.dp)) { Text(message); TextButton(onClick = { error = null; loading = true; webView?.loadUrl(url) }) { Text("Reload") } } } }
        }
    }
}
