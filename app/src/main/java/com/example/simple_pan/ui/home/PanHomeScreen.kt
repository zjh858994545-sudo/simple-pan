package com.example.simple_pan.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.simple_pan.domain.model.RecentRecord
import com.example.simple_pan.ui.component.WukongPageBackground
import com.example.simple_pan.ui.component.WukongPlusButton
import com.example.simple_pan.ui.component.WukongTopTab
import com.example.simple_pan.ui.component.WukongTopTabs
import com.example.simple_pan.ui.openfile.openVideoFile
import com.example.simple_pan.ui.space.WukongSignInDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// [设计] 为什么这样写：Screen 只负责连接 ViewModel 和纯 UI，首页数据仍然走 Repository/Room，不在 Composable 里硬编码假记录。
@Composable
fun PanHomeScreen(
    onOpenFiles: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenRecentTransfer: () -> Unit,
    onOpenRecentOpen: () -> Unit,
    onOpenTxtReader: (fileId: String, fileName: String) -> Unit,
    onOpenSpaceManagement: () -> Unit,
    onOpenMySubscription: () -> Unit,
    onOpenMyShare: () -> Unit,
    onOpenCloudCollection: () -> Unit,
    viewModel: PanHomeViewModel = hiltViewModel()
) {
    // [语法] by 是 Kotlin 委托语法，这里把 State<PanHomeState> 解包成普通变量，类似 Java 每次调用 state.getValue()。
    // [设计] 为什么这样写：collectAsStateWithLifecycle 会按页面生命周期收集 State，页面不可见时减少无意义更新。
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // [设计] 为什么这样写：Effect 是一次性动作，首页只消费事件，不把导航和 Android Intent 写进 State。
    LaunchedEffect(viewModel, context) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PanHomeEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is PanHomeEffect.OpenTxtReader -> onOpenTxtReader(effect.fileId, effect.fileName)
                is PanHomeEffect.OpenVideoPlayer -> {
                    val errorMessage = context.openVideoFile(
                        localPath = effect.localPath,
                        mimeType = effect.mimeType
                    )
                    if (errorMessage == null) {
                        viewModel.onIntent(PanHomeIntent.RecordOpenedFile(effect.fileId))
                    } else {
                        snackbarHostState.showSnackbar(errorMessage)
                    }
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { contentPadding ->
        PanHomeContent(
            modifier = Modifier.padding(contentPadding),
            state = state,
            onOpenFiles = onOpenFiles,
            onOpenSearch = onOpenSearch,
            onOpenTransfer = onOpenTransfer,
            onOpenRecentTransfer = onOpenRecentTransfer,
            onOpenRecentOpen = onOpenRecentOpen,
            onOpenSpaceManagement = onOpenSpaceManagement,
            onOpenMySubscription = onOpenMySubscription,
            onOpenMyShare = onOpenMyShare,
            onOpenCloudCollection = onOpenCloudCollection,
            onRecentRecordClick = { record ->
                viewModel.onIntent(PanHomeIntent.OpenRecentFile(record.fileId))
            },
            onRetry = {
                viewModel.onIntent(PanHomeIntent.Retry)
            }
        )
    }
}

// [设计] 为什么这样写：纯展示函数只依赖 State 和回调，后续 Preview 或 UI 测试可以不启动 Hilt/Room。
@Composable
private fun PanHomeContent(
    modifier: Modifier = Modifier,
    state: PanHomeState,
    onOpenFiles: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenRecentTransfer: () -> Unit,
    onOpenRecentOpen: () -> Unit,
    onOpenSpaceManagement: () -> Unit,
    onOpenMySubscription: () -> Unit,
    onOpenMyShare: () -> Unit,
    onOpenCloudCollection: () -> Unit,
    onRecentRecordClick: (RecentRecord) -> Unit,
    onRetry: () -> Unit
) {
    var isSignInDialogVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = WukongPageBackground
    ) {
        when {
            state.isLoading -> HomeLoading()
            state.errorMessage != null -> HomeError(message = state.errorMessage, onRetry = onRetry)
            else -> Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp)
                ) {
                    WukongTopTabs(
                        selectedTab = WukongTopTab.Pan,
                        onPanClick = {},
                        onFileClick = onOpenFiles,
                        onBackClick = {},
                        onTransferClick = onOpenTransfer,
                        onSearchClick = onOpenSearch,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        PanProfileCard(
                            state = state,
                            onOpenSpaceManagement = onOpenSpaceManagement,
                            onShowSignInDialog = {
                                isSignInDialogVisible = true
                            },
                            onOpenMySubscription = onOpenMySubscription,
                            onOpenMyShare = onOpenMyShare,
                            onOpenCloudCollection = onOpenCloudCollection
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    RecentPanel(
                        title = "最近转存",
                        emptyText = "暂无转存记录",
                        records = state.recentTransfer,
                        onAllClick = onOpenRecentTransfer,
                        onRecordClick = onRecentRecordClick
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    RecentPanel(
                        title = "最近浏览",
                        emptyText = "暂无浏览记录",
                        records = state.recentOpen,
                        onAllClick = onOpenRecentOpen,
                        onRecordClick = onRecentRecordClick
                    )
                    Spacer(modifier = Modifier.height(110.dp))
                }
                WukongPlusButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 22.dp, bottom = 26.dp),
                    onClick = onOpenFiles
                )
            }
        }
    }

    if (isSignInDialogVisible) {
        WukongSignInDialog(
            onDismiss = {
                isSignInDialogVisible = false
            }
        )
    }
}

// [设计] 为什么这样写：首页加载态明确展示，避免首次 mock 入库和最近记录 Flow 组合期间出现白屏。
@Composable
private fun HomeLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.Black)
    }
}

// [设计] 为什么这样写：首页错误态提供重试入口，便于验证阶段定位 mock 入库或数据库观察问题。
@Composable
private fun HomeError(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "首页加载失败",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(onClick = onRetry) {
            Text(text = "重试")
        }
    }
}

// [设计] 为什么这样写：账号卡片按参考图承载容量、管理空间、签到角标和三列统计，是网盘页第一视觉焦点。
@Composable
private fun PanProfileCard(
    state: PanHomeState,
    onOpenSpaceManagement: () -> Unit,
    onShowSignInDialog: () -> Unit,
    onOpenMySubscription: () -> Unit,
    onOpenMyShare: () -> Unit,
    onOpenCloudCollection: () -> Unit
) {
    val totalBytes = 1024L * 1024L * 1024L * 1024L
    val progress = (state.usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFF0F0F0))
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        color = Color(0xFF202020),
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "SP",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp,
                                    lineHeight = 23.sp
                                ),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .padding(start = 18.dp)
                            .width(136.dp)
                    ) {
                        Text(
                            text = "已用空间：${state.usedBytes.toSizeText()}/1T",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            ),
                            color = Color.Black,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = Color(0xFF347DFF),
                            trackColor = Color(0xFFEDEDED)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color.Transparent,
                            onClick = onOpenSpaceManagement
                        ) {
                            Text(
                                text = "管理空间 >",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp
                                ),
                                color = Color(0xFF666666)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PanStatItem(
                        modifier = Modifier.weight(1f),
                        title = "我的订阅",
                        value = "0",
                        onClick = onOpenMySubscription
                    )
                    VerticalDivider()
                    PanStatItem(
                        modifier = Modifier.weight(1f),
                        title = "我的分享",
                        value = state.recentTransfer.size.toString(),
                        onClick = onOpenMyShare
                    )
                    VerticalDivider()
                    PanStatItem(
                        modifier = Modifier.weight(1f),
                        title = "云收藏文件",
                        value = state.fileCount.toString(),
                        onClick = onOpenCloudCollection
                    )
                }
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp),
            color = Color(0xFFFFF19A),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp),
            onClick = onShowSignInDialog
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(20.dp),
                    color = Color(0xFFF6C94B),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "今日签到",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    ),
                    color = Color.Black,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// [设计] 为什么这样写：统计项和参考图一致用标题 + 数字 + 箭头，先展示已有本地数据能支撑的数量。
@Composable
private fun PanStatItem(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color.Transparent,
        onClick = onClick
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                ),
                color = Color.Black,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$value >",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 20.sp,
                    lineHeight = 25.sp
                ),
                color = Color.Black,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// [设计] 为什么这样写：卡片里的竖线是参考图中的统计分隔符，用固定尺寸避免布局随内容跳动。
@Composable
private fun VerticalDivider() {
    Surface(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(width = 1.dp, height = 52.dp),
        color = Color(0xFFE6E6E6)
    ) {}
}

// [设计] 为什么这样写：最近转存/浏览是首页核心区块，按参考图做成白色横向大卡片，空状态和列表共用同一结构。
@Composable
private fun RecentPanel(
    title: String,
    emptyText: String,
    records: List<RecentRecord>,
    onAllClick: () -> Unit,
    onRecordClick: (RecentRecord) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 16.sp,
                        lineHeight = 21.sp
                    ),
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = Color.Transparent,
                    onClick = onAllClick
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                        text = "全部 >",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        ),
                        color = Color(0xFF666666)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        ),
                        color = Color(0xFF9A9A9A)
                    )
                }
            } else {
                records.take(3).forEachIndexed { index, record ->
                    RecentHomeRow(
                        record = record,
                        onClick = { onRecordClick(record) }
                    )
                    if (index != records.take(3).lastIndex) {
                        HorizontalDivider(color = Color(0xFFEDEDED))
                    }
                }
            }
        }
    }
}

// [设计] 为什么这样写：最近记录行在首页保持简洁，只显示文件名和行为来源，详细打开仍在文件页完成。
@Composable
private fun RecentHomeRow(
    record: RecentRecord,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(vertical = 7.dp)) {
        Text(
            text = record.fileName,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                lineHeight = 22.sp
            ),
            color = Color.Black,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${record.actionText()} · ${record.timestamp.toTimeLabel()}",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 19.sp
            ),
            color = Color(0xFF7A7A7A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
}

// [语法] 这是 RecentRecord 的扩展函数，相当于 Java 静态工具方法 RecentRecordDisplay.actionText(record)。
// [设计] 为什么这样写：首页只需要短来源文案，避免把 transferType 原始存储值直接展示给用户。
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

// [语法] 这是 Long 的扩展函数，相当于 Java 静态工具方法 SizeFormatter.toSizeText(sizeBytes)。
// [设计] 为什么这样写：空间大小只在首页使用，先局部格式化，等多页面复用时再抽到 util。
private fun Long.toSizeText(): String {
    val kb = 1024L
    val mb = kb * 1024L
    return when {
        this >= mb -> "${this / mb}MB"
        this >= kb -> "${this / kb}KB"
        else -> "${this}B"
    }
}

// [语法] 这是 Long 的扩展函数，用时间戳生成简短展示文案。
// [设计] 为什么这样写：首页最近记录需要用户可读时间，先用本地格式化替代“时间戳 xxxx”，演示时更像真实产品。
private fun Long.toTimeLabel(): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(this))
}
