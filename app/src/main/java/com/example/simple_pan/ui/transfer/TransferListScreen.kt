package com.example.simple_pan.ui.transfer

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.TransferDirection
import com.example.simple_pan.domain.model.TransferRecord
import com.example.simple_pan.domain.model.TransferStatus
import com.example.simple_pan.ui.component.WukongEmptyState
import com.example.simple_pan.ui.component.WukongPageBackground
import com.example.simple_pan.ui.component.WukongSegmentedTabs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// [设计] 为什么这样写：传输页展示真实 transfer_history 数据，Screen 只连接 ViewModel State 和 UI，不直接访问 DAO。
@Composable
fun TransferListScreen(
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: TransferListViewModel = hiltViewModel()
) {
    // [语法] by 是 Kotlin 委托语法，把 State<TransferListState> 解包成普通变量，Compose 会在状态变化时重组。
    // [设计] 为什么这样写：collectAsStateWithLifecycle 随页面生命周期订阅，避免后台页面继续无意义刷新。
    val state by viewModel.state.collectAsStateWithLifecycle()

    TransferListContent(
        state = state,
        onBackClick = onBackClick,
        onSettingsClick = onSettingsClick,
        onDirectionChange = { direction ->
            viewModel.onIntent(TransferListIntent.ChangeDirection(direction))
        },
        onStatusFilterChange = { filter ->
            viewModel.onIntent(TransferListIntent.ChangeStatusFilter(filter))
        },
        onRetry = {
            viewModel.onIntent(TransferListIntent.Retry)
        }
    )
}

// [设计] 为什么这样写：纯展示函数只依赖 State 和回调，后续 Preview/UI 测试可以直接构造状态，不需要真实数据库。
@Composable
private fun TransferListContent(
    state: TransferListState,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDirectionChange: (TransferDirection) -> Unit,
    onStatusFilterChange: (TransferStatusFilter) -> Unit,
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
            TransferHeader(
                selectedDirection = state.selectedDirection,
                onBackClick = onBackClick,
                onSettingsClick = onSettingsClick,
                onDirectionChange = onDirectionChange
            )
            Spacer(modifier = Modifier.height(12.dp))
            TransferStatusTabs(
                state = state,
                onStatusFilterChange = onStatusFilterChange
            )
            TransferBody(
                modifier = Modifier.weight(1f),
                state = state,
                onRetry = onRetry
            )
        }
    }
}

// [设计] 为什么这样写：顶部上传/下载 Tab 只用字重和颜色区分选中态，贴近参考图的轻量切换方式。
@Composable
private fun TransferHeader(
    selectedDirection: TransferDirection,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDirectionChange: (TransferDirection) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBackClick) {
            Text(
                text = "<",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TransferTopTabText(
            text = "上传",
            selected = selectedDirection == TransferDirection.Upload,
            onClick = { onDirectionChange(TransferDirection.Upload) }
        )
        Spacer(modifier = Modifier.size(width = 28.dp, height = 1.dp))
        TransferTopTabText(
            text = "下载",
            selected = selectedDirection == TransferDirection.Download,
            onClick = { onDirectionChange(TransferDirection.Download) }
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onSettingsClick) {
            Text(
                text = "⚙",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun TransferTopTabText(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = if (selected) Color.Black else Color(0xFF8F8F8F),
            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal
        )
    }
}

// [设计] 为什么这样写：状态筛选的数量来自真实记录派生；当前没有任务表，所以进行中/失败真实为 0。
@Composable
private fun TransferStatusTabs(
    state: TransferListState,
    onStatusFilterChange: (TransferStatusFilter) -> Unit
) {
    val filters = TransferStatusFilter.entries
    val runningLabel = if (state.selectedDirection == TransferDirection.Upload) {
        "上传中"
    } else {
        "下载中"
    }
    val labels = listOf(
        "全部",
        "已完成 ${state.completedCount}",
        "$runningLabel ${state.runningCount}",
        "失败 ${state.failedCount}"
    )
    WukongSegmentedTabs(
        items = labels,
        selectedIndex = filters.indexOf(state.selectedStatusFilter),
        onSelected = { index -> onStatusFilterChange(filters[index]) }
    )
}

// [设计] 为什么这样写：正文统一承载加载、错误、空状态和列表，让顶部筛选栏始终稳定显示。
@Composable
private fun TransferBody(
    modifier: Modifier = Modifier,
    state: TransferListState,
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isLoading -> TransferLoading()
            state.errorMessage != null -> TransferError(
                message = state.errorMessage,
                onRetry = onRetry
            )
            state.visibleRecords.isEmpty() -> WukongEmptyState(
                modifier = Modifier.fillMaxSize(),
                text = state.emptyText()
            )
            else -> TransferRecordList(records = state.visibleRecords)
        }
    }
}

@Composable
private fun TransferLoading() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Color.Black)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "正在加载传输记录",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF666666)
        )
    }
}

@Composable
private fun TransferError(
    message: String,
    onRetry: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(text = "重试")
        }
    }
}

@Composable
private fun TransferRecordList(records: List<TransferRecord>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = records,
            key = { record -> record.historyId }
        ) { record ->
            TransferRecordRow(record = record)
        }
    }
}

// [设计] 为什么这样写：列表行展示文件类型、名称、大小、完成时间和来源，能证明传输页来自真实历史表，而不是静态空态。
@Composable
private fun TransferRecordRow(record: TransferRecord) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(14.dp)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                TransferTypeBadge(fileType = record.fileType)
            },
            headlineContent = {
                Text(
                    text = record.fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF111827),
                    fontWeight = FontWeight.Black
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = "${record.transferActionText()} · ${record.sizeBytes.toSizeText()}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFF6B7280)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = record.transferredAt.toTimeLabel(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFF9CA3AF),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            trailingContent = {
                Text(
                    text = record.status.toDisplayText(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF16A34A),
                    fontWeight = FontWeight.SemiBold
                )
            }
        )
        HorizontalDivider(color = Color.Transparent)
    }
}

@Composable
private fun TransferTypeBadge(fileType: FileType) {
    val badge = fileType.toBadgeSpec()
    Surface(
        modifier = Modifier.size(50.dp),
        color = badge.containerColor,
        contentColor = badge.contentColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, badge.contentColor.copy(alpha = 0.14f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = badge.shortLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

private data class TransferBadgeSpec(
    val shortLabel: String,
    val containerColor: Color,
    val contentColor: Color
)

private fun FileType.toBadgeSpec(): TransferBadgeSpec {
    return when (this) {
        FileType.Folder -> TransferBadgeSpec("夹", Color(0xFFE7EEFF), Color(0xFF1E3A8A))
        FileType.Video -> TransferBadgeSpec("视", Color(0xFFFFDDD7), Color(0xFF9F1239))
        FileType.Txt -> TransferBadgeSpec("文", Color(0xFFD8F3EF), Color(0xFF115E59))
        FileType.Image -> TransferBadgeSpec("图", Color(0xFFEAF0F8), Color(0xFF475569))
        FileType.Audio -> TransferBadgeSpec("音", Color(0xFFE8D9FF), Color(0xFF4C1D95))
        FileType.Other -> TransferBadgeSpec("其", Color(0xFFEAF0F8), Color(0xFF475569))
    }
}

private fun TransferListState.emptyText(): String {
    return when (selectedStatusFilter) {
        TransferStatusFilter.All,
        TransferStatusFilter.Completed -> if (selectedDirection == TransferDirection.Upload) {
            "暂无上传记录"
        } else {
            "暂无下载记录"
        }
        TransferStatusFilter.Running -> "暂无进行中的任务"
        TransferStatusFilter.Failed -> "暂无失败任务"
    }
}

private fun TransferRecord.transferActionText(): String {
    return when (transferType) {
        "upload" -> "上传"
        "share_save" -> "分享保存"
        else -> "传输"
    }
}

private fun TransferStatus.toDisplayText(): String {
    return when (this) {
        TransferStatus.Completed -> "已完成"
        TransferStatus.Running -> "进行中"
        TransferStatus.Failed -> "失败"
    }
}

private fun Long.toSizeText(): String {
    val kb = 1024L
    val mb = kb * 1024L
    return when {
        this >= mb -> "${this / mb} MB"
        this >= kb -> "${this / kb} KB"
        else -> "$this B"
    }
}

private fun Long.toTimeLabel(): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(this))
}
