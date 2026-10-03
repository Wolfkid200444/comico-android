package moe.comico.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncRefreshBox(state: AppState, model: ReaderViewModel, enabled: Boolean = true,
                   content: @Composable () -> Unit) {
    if(enabled) PullToRefreshBox(
        isRefreshing = state.syncLoading,
        onRefresh = { if(!state.syncLoading && !state.account.loading) model.syncAccount() },
        modifier = Modifier.fillMaxSize()
    ) { content() } else content()
}
