package com.example.simple_pan.ui.recent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.simple_pan.domain.model.RecentRecord
import com.example.simple_pan.ui.component.WukongEmptyState
import com.example.simple_pan.ui.component.WukongFileTypeIcon
import com.example.simple_pan.ui.component.WukongPageBackground
import com.example.simple_pan.ui.component.WukongTitleTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// [设计] 为什么这样写：最近转存/最近浏览从首页进入，但它们不是文件根目录；单独页面能准确表达“查看全部历史记录”的语义。
@Composable
fun RecentRecordsScreen(
    type: RecentRecordsType,
    onBackClick: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTransfer: () -> Unit,
    viewModel: RecentRecordsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // [语法] LaunchedEffect(type) 会在 type 变化时重新执行一次，适合页面参数驱动加载。
    // [设计] 为什么这样写：导航参数决定展示最近转存还是最近浏览，Screen 不直接调用 Repository，只把参数转成 Intent。
    LaunchedEffect(type) {
        viewModel.onIntent(RecentRecordsIntent.Load(type))
    }

    RecentRecordsContent(
        state = state,
        onBackClick = onBackClick,
        onOpenSearch = onOpenSearch,
        onOpenTransfer = onOpenTransfer,
        onRetry = {
            viewModel.onIntent(RecentRecordsIntent.Retry)
        }
    )
}

@Composable
private fun RecentRecordsContent(
    state: RecentRecordsState,
    onBackClick: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTransfer: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WukongPageBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            WukongTitleTopBar(
                title = state.type.toTitle(),
                onBackClick = onBackClick,
                onTransferClick = onOpenTransfer,
                onSearchClick = onOpenSearch
            )
            Spacer(modifier = Modifier.height(18.dp))
            when {
                state.isLoading -> RecentRecordsLoading()
                state.errorMessage != null -> RecentRecordsError(
                    message = state.errorMessage,
                    onRetry = onRetry
                )
                state.records.isEmpty() -> WukongEmptyState(
                    modifier = Modifier.fillMaxSize(),
                    text = state.type.toEmptyText()
                )
                else -> RecentRecordsList(records = state.records)
            }
        }
    }
}

@Composable
private fun RecentRecordsList(records: List<RecentRecord>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(
            items = records,
            key = { record -> "${record.recordType}-${record.fileId}-${record.timestamp}" }
        ) { record ->
            RecentRecordRow(record = record)
            HorizontalDivider(color = Color(0xFFEDEDED))
        }
    }
}

// [设计] 为什么这样写：全量页比首页显示更多细节，包含文件类型、文件名、行为来源和时间，用户能确认“全部”确实是历史列表。
@Composable
private fun RecentRecordRow(record: RecentRecord) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WukongFileTypeIcon(fileType = record.fileType, size = 44.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${record.actionText()} · ${record.timestamp.toTimeLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF777777),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentRecordsLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.Black)
    }
}

@Composable
private fun RecentRecordsError(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "加载失败",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Black,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(onClick = onRetry) {
            Text(text = "重试")
        }
    }
}

private fun RecentRecordsType.toTitle(): String {
    return when (this) {
        RecentRecordsType.Transfer -> "最近转存"
        RecentRecordsType.Open -> "最近浏览"
    }
}

private fun RecentRecordsType.toEmptyText(): String {
    return when (this) {
        RecentRecordsType.Transfer -> "暂无最近转存记录"
        RecentRecordsType.Open -> "暂无最近浏览记录"
    }
}

private fun RecentRecord.actionText(): String {
    return when (recordType) {
        RecentRecord.RecordType.Open -> "浏览"
        RecentRecord.RecordType.Transfer -> when (transferType) {
            "upload" -> "上传"
            "share_save" -> "分享保存"
            else -> "转存"
        }
    }
}

private fun Long.toTimeLabel(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(this))
}
