package com.example.simple_pan.ui.file

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.example.simple_pan.domain.model.CloudFile
import com.example.simple_pan.domain.model.FileType

// [设计] 为什么这样写：Screen 只负责连接 ViewModel 和纯 UI 内容，数据来源仍然是 Room -> Repository -> ViewModel -> State。
@Composable
fun FileListScreen(
    viewModel: FileListViewModel = hiltViewModel()
) {
    // [语法] by 是 Kotlin 委托语法，这里把 State<FileListState> 解包成普通变量，类似 Java 每次调用 state.getValue()。
    // [设计] 为什么这样写：collectAsStateWithLifecycle 会随页面生命周期自动开始/停止收集，避免后台页面继续消耗资源。
    val state by viewModel.state.collectAsStateWithLifecycle()

    FileListContent(
        state = state,
        onRetry = {
            viewModel.onIntent(FileListIntent.Retry)
        }
    )
}

// [设计] 为什么这样写：把纯展示内容拆出来，后续写 Preview 或 UI 测试时可以直接传入 State，不需要真的启动 Hilt 和数据库。
@Composable
private fun FileListContent(
    state: FileListState,
    onRetry: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            FileListHeader(fileCount = state.files.size)
            Spacer(modifier = Modifier.height(12.dp))

            when {
                state.isLoading -> FileListLoading()
                state.errorMessage != null -> FileListError(
                    message = state.errorMessage,
                    onRetry = onRetry
                )
                state.files.isEmpty() -> FileListEmpty()
                else -> FileListItems(files = state.files)
            }
        }
    }
}

// [设计] 为什么这样写：头部只展示当前根目录的列表数量，第 7 步不做面包屑和文件夹进入，避免提前跨到阶段 2。
@Composable
private fun FileListHeader(fileCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "文件",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "根目录 · $fileCount 项",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// [设计] 为什么这样写：loading 状态明确展示，避免首次 mock 入库和 Room 查询期间出现空白页面。
@Composable
private fun FileListLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "正在从 Room 加载文件")
        }
    }
}

// [设计] 为什么这样写：错误状态提供重试入口，验证阶段如果 JSON 或数据库初始化失败，可以直接在 UI 上看到问题。
@Composable
private fun FileListError(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
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

// [设计] 为什么这样写：空状态保留给数据库为空或筛选无结果场景，确保 UI 不把“空列表”误表现成加载失败。
@Composable
private fun FileListEmpty() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无文件",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// [设计] 为什么这样写：LazyColumn 用稳定 key 绑定 fileId，后续排序/删除/管理态选择时不会因为 index 变化导致行状态串位。
@Composable
private fun FileListItems(files: List<CloudFile>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // [语法] items 的 key = { file -> ... } 是尾随 lambda，file 是显式命名参数，类似 Java 回调里的参数名。
        items(
            items = files,
            key = { file -> file.fileId }
        ) { file ->
            FileRow(file = file)
            HorizontalDivider()
        }
    }
}

// [设计] 为什么这样写：列表行只展示领域模型 CloudFile，不接触 Entity 或 DTO，证明 UI 层已经和数据源细节解耦。
@Composable
private fun FileRow(file: CloudFile) {
    ListItem(
        leadingContent = {
            FileTypeBadge(fileType = file.type)
        },
        headlineContent = {
            Text(
                text = file.name,
                maxLines = 1
            )
        },
        supportingContent = {
            Text(
                text = "${file.type.toDisplayName()} | ${file.sizeBytes.toSizeText()}",
                maxLines = 1
            )
        }
    )
}

// [设计] 为什么这样写：当前阶段不额外引入图标库，用短文本徽标表达类型；后续接入设计系统时可以集中替换。
@Composable
private fun FileTypeBadge(fileType: FileType) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = fileType.toShortLabel(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    Spacer(modifier = Modifier.width(8.dp))
}

// [语法] 这是扩展函数，相当于 Java 静态工具方法 FileTypeDisplay.toDisplayName(fileType)。
// [设计] 为什么这样写：文件类型文案集中在 UI 层，后续列表、筛选栏和详情页可以复用同一套展示规则。
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

// [语法] 这是扩展函数，给 FileType 增加 UI 短标签能力；Java 里通常会写成工具类静态方法。
// [设计] 为什么这样写：短标签只服务当前列表视觉，不进入 domain 模型，避免领域层混入 UI 文案。
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
// [设计] 为什么这样写：第 7 步先在文件页局部格式化大小，不新增 util 跨模块文件；后续若多页面复用，再抽到 util。
private fun Long.toSizeText(): String {
    val kb = 1024L
    val mb = kb * 1024L
    return when {
        this >= mb -> "${this / mb} MB"
        this >= kb -> "${this / kb} KB"
        else -> "$this B"
    }
}
