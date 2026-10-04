@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package moe.comico.reader

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.saveable.rememberSaveable
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(state: AppState, model: ReaderViewModel) {
    val chapter = state.reader ?: return
    val reader = state.nativeReader
    val preferences = state.effectiveReaderPreferences()
    val mode = preferences.modeFor(state.selected?.format.orEmpty())
    val context = LocalContext.current
    var settings by remember { mutableStateOf(false) }
    var controls by rememberSaveable(chapter.id) { mutableStateOf(false) }
    var chapterPicker by rememberSaveable { mutableStateOf(false) }
    val discussion by model.discussion.state.collectAsState()
    ReaderSystemBars(show = controls || settings || chapterPicker || discussion.target != null)
    var pageJump by remember(chapter.id) { mutableStateOf<Int?>(null) }
    BackHandler { model.back() }
    if(settings) ReaderSettingsDialog(state,model) { settings = false }
    if(chapterPicker) ReaderChapterPicker(state, model, onDismiss = { chapterPicker = false })
    fun openBrowser(target: String) {
        val uri = Uri.parse(target)
        if(uri.scheme in listOf("https","http")) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,uri)) }
    }
    Surface(Modifier.fillMaxSize(), color = Color(0xFF101014), contentColor = Color.White) {
        Box(Modifier.fillMaxSize().pointerInput(chapter.id) {
            detectTapGestures(onTap = { position ->
                if(position.x in size.width * .25f..size.width * .75f) controls = !controls
            })
        }) {
            when {
                reader.loading -> Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(); Text("Loading chapter images…") }
                reader.session != null -> {
                    val session = reader.session
                    if(usesVerticalScrolling(mode,preferences.navigation)) {
                        val groupSize = readingGroupSize(mode)
                        val count = (session.pages.size + groupSize - 1) / groupSize
                        val list = rememberLazyListState(initialFirstVisibleItemIndex = reader.page / groupSize)
                        LaunchedEffect(session,mode,preferences.navigation) {
                            list.scrollToItem((reader.page / groupSize).coerceIn(0,count-1))
                            snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { model.readerPage(it * groupSize) }
                        }
                        LaunchedEffect(pageJump) { pageJump?.let { list.scrollToItem((it / groupSize).coerceIn(0,count-1)); model.readerPage(it / groupSize * groupSize); pageJump = null } }
                        LazyColumn(state = list,modifier = Modifier.fillMaxSize(),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(if(mode == ReadingMode.STRIP) 0.dp else 16.dp)) {
                            items(count,key = { "${chapter.id}:$it:$groupSize" }) { group ->
                                Row(Modifier.fillMaxWidth(preferences.width.fraction),horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    spreadPages(session.pages.size,group * groupSize,groupSize == 2,preferences.direction).forEach { index ->
                                        ChapterImage(session.pages[index],index,true,Modifier.weight(1f),{ controls = !controls },model::loadReader)
                                    }
                                    if(groupSize == 2 && group * groupSize + 1 >= session.pages.size) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        val spreadSize = if(mode == ReadingMode.DOUBLE) 2 else 1
                        val count = (session.pages.size + spreadSize - 1) / spreadSize
                        val pager = rememberPagerState(initialPage = (reader.page / spreadSize).coerceIn(0,count-1),pageCount = { count })
                        LaunchedEffect(session,mode,preferences.navigation) {
                            pager.scrollToPage((reader.page / spreadSize).coerceIn(0,count-1))
                            snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { model.readerPage(it * spreadSize) }
                        }
                        LaunchedEffect(pageJump) { pageJump?.let { pager.scrollToPage((it / spreadSize).coerceIn(0,count-1)); model.readerPage(it / spreadSize * spreadSize); pageJump = null } }
                        HorizontalPager(state = pager,modifier = Modifier.fillMaxSize(),reverseLayout = preferences.direction == ReadingDirection.RTL,beyondViewportPageCount = 1) { spread ->
                            Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) {
                                Row(Modifier.fillMaxWidth(preferences.width.fraction).fillMaxHeight(),horizontalArrangement = Arrangement.spacedBy(2.dp),verticalAlignment = Alignment.CenterVertically) {
                                    spreadPages(session.pages.size,spread * spreadSize,spreadSize == 2,preferences.direction).forEach { index ->
                                        ChapterImage(session.pages[index],index,false,Modifier.weight(1f).fillMaxHeight(),{ controls = !controls },model::loadReader)
                                    }
                                    if(spreadSize == 2 && spread * spreadSize + 1 >= session.pages.size) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    reader.notice?.let { Text(it,Modifier.align(Alignment.TopCenter).background(Color.Black.copy(alpha = .8f)).padding(12.dp),style = MaterialTheme.typography.bodySmall) }
                }
                chapter.external.isNotBlank() && reader.error == null -> Column(Modifier.align(Alignment.Center).padding(28.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Read at the source",style = MaterialTheme.typography.headlineMedium)
                    Text("This chapter is hosted by its publisher. Open the original reader to continue.")
                    Button(onClick = { openBrowser(chapter.external) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew,null); Spacer(Modifier.width(8.dp)); Text("Open publisher reader") }
                }
                else -> Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Rounded.CloudOff,null,Modifier.size(40.dp))
                    Text(reader.error ?: "No chapter images are available.")
                    Button(onClick = model::loadReader) { Text("Try again") }
                    OutlinedButton(onClick = { settings = true }) { Text("Choose source or proxy method") }
                }
            }
            AnimatedVisibility(visible = controls, modifier = Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
                ReaderHeader(state, model, onChapters = { chapterPicker = true })
            }
            AnimatedVisibility(visible = controls, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                ReaderBottomControls(state, mode, onSettings = { settings = true }, onChapters = { chapterPicker = true },
                    onPrevious = { model.read(it) }, onNext = { model.read(it) }, onPage = { pageJump = it })
            }
            if(!controls && reader.session != null) ReaderPageCounter(reader.page + 1, reader.session.pages.size,
                Modifier.align(Alignment.BottomCenter))

        }
    }
}

@Composable
private fun ChapterImage(url: String, page: Int, strip: Boolean, modifier: Modifier, onTap: () -> Unit, onSessionReload: () -> Unit) {
    val context = LocalContext.current
    var retry by remember(url) { mutableIntStateOf(0) }
    var zoom by remember(url) { mutableFloatStateOf(1f) }
    var pan by remember(url) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { scale, offset, _ ->
        zoom = (zoom * scale).coerceIn(1f,4f)
        pan = if(zoom > 1f) pan + offset else Offset.Zero
    }
    val request = remember(url,retry) { ImageRequest.Builder(context).data(url).addHeader("User-Agent","ComicoAndroid/${BuildConfig.VERSION_NAME} (https://github.com/Wolfkid200444/comico-android)").addHeader("Referer","$BASE_URL/").build() }
    Box(modifier.clipToBounds(),contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(model = request,contentDescription = "Chapter page ${page + 1}",contentScale = if(strip) ContentScale.FillWidth else ContentScale.Fit,modifier = (if(strip) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .pointerInput(url) { detectTapGestures(onTap = { position -> if(position.x in size.width * .25f..size.width * .75f) onTap() },onDoubleTap = { zoom = if(zoom > 1f) 1f else 2f; pan = Offset.Zero }) }
            .transformable(transform,canPan = { zoom > 1f })
        ) {
            when(val imageState = painter.state) {
                is AsyncImagePainter.State.Success -> {
                    val size = painter.intrinsicSize
                    val contentModifier = if(strip && size.width > 0 && size.height > 0) Modifier.fillMaxWidth().aspectRatio(size.width / size.height) else Modifier.fillMaxSize()
                    SubcomposeAsyncImageContent(modifier = contentModifier.graphicsLayer(scaleX = zoom,scaleY = zoom,translationX = pan.x,translationY = pan.y))
                }
                is AsyncImagePainter.State.Error -> Column((if(strip) Modifier.heightIn(min = 300.dp) else Modifier.fillMaxSize()).padding(24.dp),verticalArrangement = Arrangement.spacedBy(12.dp,Alignment.CenterVertically),horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Page ${page + 1} couldn't load.",color = Color.White)
                    FilledTonalButton(onClick = { retry++ }) { Text("Retry image") }
                    TextButton(onClick = onSessionReload) { Text("Refresh chapter session") }
                }
                else -> Box(if(strip) Modifier.fillMaxWidth().height(360.dp) else Modifier.fillMaxSize(),contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}
