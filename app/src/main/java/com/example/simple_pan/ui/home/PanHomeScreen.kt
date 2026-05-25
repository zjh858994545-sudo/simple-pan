package com.example.simple_pan.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.RecentRecord

// [设计] 为什么这样写：Screen 只负责连接 ViewModel 和纯 UI，首页数据仍然走 Repository/Room，不在 Composable 里硬编码假记录。
@Composable
fun PanHomeScreen(
    viewModel: PanHomeViewModel = hiltViewModel()
) {
    // [语法] by 是 Kotlin 委托语法，这里把 State<PanHomeState> 解包成普通变量，类似 Java 每次调用 state.getValue()。
    // [设计] 为什么这样写：collectAsStateWithLifecycle 会按页面生命周期收集 State，页面不可见时减少无意义更新。
    val state by viewModel.state.collectAsStateWithLifecycle()

    PanHomeContent(
        state = state,
        onRetry = {
            viewModel.onIntent(PanHomeIntent.Retry)
        }
    )
}

// [设计] 为什么这样写：纯展示函数只依赖 State 和回调，后续 Preview 或 UI 测试可以不启动 Hilt/Room。
@Composable
private fun PanHomeContent(
    state: PanHomeState,
    onRetry: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> HomeLoading()
            state.errorMessage != null -> HomeError(message = state.errorMessage, onRetry = onRetry)
            else -> HomeDashboard(state = state)
        }
    }
}

// [设计] 为什么这样写：首页加载态明确展示，避免首次 mock 入库和最近记录 Flow 组合期间出现白屏。
@Composable
private fun HomeLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "正在加载网盘首页")
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
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(text = "重试")
        }
    }
}

// [设计] 为什么这样写：第 8 步只搭首页核心信息区和最近记录区，不接上传/打开动作，保证阶段 1 骨架稳定。
@Composable
private fun HomeDashboard(state: PanHomeState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "网盘",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProfileSection(userName = state.userName)
        Spacer(modifier = Modifier.height(18.dp))
        SpaceSection(
            usedBytes = state.usedBytes,
            fileCount = state.fileCount
        )
        Spacer(modifier = Modifier.height(22.dp))
        RecentSection(
            title = "最近浏览",
            emptyText = "暂无浏览记录",
            records = state.recentOpen
        )
        Spacer(modifier = Modifier.height(22.dp))
        RecentSection(
            title = "最近转存",
            emptyText = "暂无转存记录",
            records = state.recentTransfer
        )
    }
}

// [设计] 为什么这样写：个人信息先做稳定骨架，后续如果接登录或用户配置，只需要替换 ViewModel 状态来源。
@Composable
private fun ProfileSection(userName: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = userName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "本地演示账号",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// [设计] 为什么这样写：空间信息从 Room 根目录文件汇总而来，不是静态假数字；后续上传文件后可以自然变动。
@Composable
private fun SpaceSection(
    usedBytes: Long,
    fileCount: Int
) {
    val maxBytes = 256L * 1024L * 1024L
    val progress = (usedBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "空间",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$fileCount 项",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // [设计] 为什么这样写：进度条给首页一个可视化锚点，当前只用演示容量上限，后续可以替换为真实配额。
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${usedBytes.toSizeText()} / 256 MB",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// [设计] 为什么这样写：最近浏览和最近转存共用同一个区块组件，减少重复 UI，也能体现两个列表来自同一种 RecentRecord 模型。
@Composable
private fun RecentSection(
    title: String,
    emptyText: String,
    records: List<RecentRecord>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (records.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            for (record in records) {
                RecentRow(record = record)
                HorizontalDivider()
            }
        }
    }
}

// [设计] 为什么这样写：最近记录行只展示领域模型 RecentRecord，不接触历史表 Entity，UI 不需要知道浏览和转存来自不同表。
@Composable
private fun RecentRow(record: RecentRecord) {
    ListItem(
        leadingContent = {
            Text(
                text = record.fileType.toShortLabel(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        headlineContent = {
            Text(text = record.fileName, maxLines = 1)
        },
        supportingContent = {
            Text(text = "${record.fileType.toDisplayName()} | ${record.timestamp.toTimeLabel()}")
        }
    )
}

// [语法] 这是扩展函数，相当于 Java 静态工具方法 FileTypeDisplay.toDisplayName(fileType)。
// [设计] 为什么这样写：首页文件类型文案暂时局部管理，避免第 8 步跨到 util 抽公共格式化工具。
private fun FileType.toDisplayName(): String {
    return when (this) {
        FileType.Folder -> "文件夹"
        FileType.Video -> "视频"
        FileType.Txt -> "文档"
        FileType.Image -> "图片"
        FileType.Audio -> "音频"
        FileType.Other -> "其他"
    }
}

// [语法] 这是扩展函数，给 FileType 增加首页短标签展示能力。
// [设计] 为什么这样写：短标签只属于当前 UI 表达，不进入 domain，避免领域模型混入界面文案。
private fun FileType.toShortLabel(): String {
    return when (this) {
        FileType.Folder -> "夹"
        FileType.Video -> "视"
        FileType.Txt -> "文"
        FileType.Image -> "图"
        FileType.Audio -> "音"
        FileType.Other -> "其"
    }
}

// [语法] 这是 Long 的扩展函数，相当于 Java 静态工具方法 SizeFormatter.toSizeText(sizeBytes)。
// [设计] 为什么这样写：空间大小只在首页使用，先局部格式化，等多页面复用时再抽到 util。
private fun Long.toSizeText(): String {
    val kb = 1024L
    val mb = kb * 1024L
    return when {
        this >= mb -> "${this / mb} MB"
        this >= kb -> "${this / kb} KB"
        else -> "$this B"
    }
}

// [语法] 这是 Long 的扩展函数，用时间戳生成简短展示文案。
// [设计] 为什么这样写：第 8 步不引入日期格式化工具，先用相对简单的“时间戳后四位”占位，后续统一时间工具再替换。
private fun Long.toTimeLabel(): String {
    return "时间戳 $this"
}
