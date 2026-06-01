package com.example.simple_pan.ui.share

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.ShareSnapshotFile
import com.example.simple_pan.domain.model.ShareType

// [设计] 为什么这样写：Screen 只负责连接导航 token、ViewModel 和纯 UI 内容，分享快照仍通过 Repository/Room 读取。
@Composable
fun SharePreviewScreen(
    token: String,
    onBackClick: () -> Unit,
    viewModel: SharePreviewViewModel = hiltViewModel()
) {
    // [语法] by 是 Kotlin 委托语法，这里把 State<SharePreviewState> 解包成普通变量，类似 Java 每次调用 state.getValue()。
    // [设计] 为什么这样写：collectAsStateWithLifecycle 会随页面生命周期自动开始/停止收集，避免后台页面继续观察分享 Flow。
    val state by viewModel.state.collectAsStateWithLifecycle()

    // [语法] LaunchedEffect 会在 token 变化时启动协程，离开页面时自动取消。
    // [设计] 为什么这样写：token 是导航参数，进入页面后由 ViewModel 统一加载分享内容，Composable 不直接碰 Repository。
    LaunchedEffect(token, viewModel) {
        viewModel.onIntent(SharePreviewIntent.Load(token))
    }

    SharePreviewContent(
        state = state,
        onBackClick = onBackClick,
        onRetry = {
            viewModel.onIntent(SharePreviewIntent.Retry)
        },
        onSaveClick = {
            viewModel.onIntent(SharePreviewIntent.SaveToPan)
        }
    )
}

// [设计] 为什么这样写：把纯展示内容拆出来，后续写 Preview 或 UI 测试时可以直接传入 State，不需要真的启动 Hilt 和数据库。
@Composable
private fun SharePreviewContent(
    state: SharePreviewState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onSaveClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SharePreviewHeader(onBackClick = onBackClick)
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))
            // [设计] 为什么这样写：主体区域只根据 State 分支渲染，避免 loading/error/success 的状态判断散落到多个 Composable。
            when {
                state.isLoading -> SharePreviewLoading()
                state.errorMessage != null -> SharePreviewError(
                    message = state.errorMessage,
                    onRetry = onRetry
                )
                state.isNotFound -> SharePreviewNotFound(onBackClick = onBackClick)
                else -> SharePreviewLoaded(
                    state = state,
                    onSaveClick = onSaveClick
                )
            }
        }
    }
}

// [设计] 为什么这样写：预览页是二级页面，头部提供明确返回入口，底部 Tab 会由导航层隐藏。
@Composable
private fun SharePreviewHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [语法] TextButton 后面的 { } 是尾随 lambda，用来声明按钮内部要显示的 Compose 内容。
        TextButton(onClick = onBackClick) {
            Text(text = "返回")
        }
        Text(
            text = "分享预览",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// [设计] 为什么这样写：加载状态明确展示，避免 token 路由进入后 Room 查询期间出现空白页面。
@Composable
private fun SharePreviewLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "正在加载分享内容")
        }
    }
}

// [设计] 为什么这样写：分享不存在和加载失败分开展示，用户能区分“链接无效”和“本地读取异常”。
@Composable
private fun SharePreviewNotFound(onBackClick: () -> Unit) {
    SharePreviewCenteredMessage(
        title = "分享不存在",
        message = "分享链接不存在或已失效",
        buttonText = "返回文件",
        onButtonClick = onBackClick
    )
}

// [设计] 为什么这样写：读取失败时提供重试入口，方便验证阶段直接重新触发 Room Flow 收集。
@Composable
private fun SharePreviewError(
    message: String,
    onRetry: () -> Unit
) {
    SharePreviewCenteredMessage(
        title = "加载失败",
        message = message,
        buttonText = "重试",
        onButtonClick = onRetry
    )
}

// [设计] 为什么这样写：居中提示样式在不存在和错误状态复用，保持页面反馈一致。
@Composable
private fun SharePreviewCenteredMessage(
    title: String,
    message: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onButtonClick) {
            Text(text = buttonText)
        }
    }
}

// [设计] 为什么这样写：成功状态顶部展示分享元信息，下面展示固定快照列表，证明页面读的是分享快照而不是原文件表实时列表。
@Composable
private fun SharePreviewLoaded(
    state: SharePreviewState,
    onSaveClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SharePreviewSummary(state = state)
        Spacer(modifier = Modifier.height(12.dp))
        ShareSaveAction(
            isSaving = state.isSaving,
            canSave = state.files.isNotEmpty(),
            saveMessage = state.saveMessage,
            saveErrorMessage = state.saveErrorMessage,
            onSaveClick = onSaveClick
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (state.files.isEmpty()) {
            SharePreviewEmpty()
        } else {
            ShareSnapshotList(files = state.files)
        }
    }
}

// [设计] 为什么这样写：保存到网盘是分享页的主操作，按钮和结果提示放在列表上方，用户保存后不需要滚动才能看到反馈。
@Composable
private fun ShareSaveAction(
    isSaving: Boolean,
    canSave: Boolean,
    saveMessage: String?,
    saveErrorMessage: String?,
    onSaveClick: () -> Unit
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = canSave && !isSaving,
        onClick = onSaveClick
    ) {
        Text(text = if (isSaving) "保存中" else "保存到网盘")
    }
    val message = saveErrorMessage ?: saveMessage
    if (message != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (saveErrorMessage != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }
}

// [设计] 为什么这样写：标题、类型、数量和脱敏分享码集中显示，用户进入分享页后能立即确认打开的是哪份分享。
@Composable
private fun SharePreviewSummary(state: SharePreviewState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = state.title.ifBlank { "我的分享" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${state.shareType.toDisplayName()} | ${state.files.size} 项 | ${state.token.toMaskedShareToken()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// [设计] 为什么这样写：极端情况下分享存在但快照为空时给出明确空状态，避免列表区域空白让人误以为还在加载。
@Composable
private fun SharePreviewEmpty() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无分享文件",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// [设计] 为什么这样写：LazyColumn 用 snapshotId/sourceFileId/name 组合成稳定 key，后续快照列表变化时行状态不会串位。
@Composable
private fun ShareSnapshotList(files: List<ShareSnapshotFile>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // [语法] items 的 key = { file -> ... } 是尾随 lambda，file 是当前列表项参数，类似 Java 回调里的参数。
        items(
            items = files,
            key = { file -> file.toStableKey() }
        ) { file ->
            ShareSnapshotRow(file = file)
            HorizontalDivider()
        }
    }
}

// [设计] 为什么这样写：列表行只展示快照模型 ShareSnapshotFile，不反查原始 CloudFile，确保分享页展示的是创建分享那一刻的数据。
@Composable
private fun ShareSnapshotRow(file: ShareSnapshotFile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShareSnapshotTypeBadge(fileType = file.type)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                maxLines = 1,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = file.toDescriptionText(),
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// [设计] 为什么这样写：阶段内不新增图标依赖，先用固定宽度类型徽章表达文件类型，和文件页的类型标识保持一致。
@Composable
private fun ShareSnapshotTypeBadge(fileType: FileType) {
    Surface(
        modifier = Modifier.width(48.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = fileType.toShortLabel(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// [语法] 这是 String 的扩展函数，相当于 Java 静态工具方法 ShareTokenDisplay.mask(token)。
// [设计] 为什么这样写：预览页只需要让用户识别分享码，不必完整暴露 token；后续真实查询仍使用未脱敏 token。
private fun String.toMaskedShareToken(): String {
    val normalizedToken = trim()
    return if (normalizedToken.length <= MASK_VISIBLE_PREFIX + MASK_VISIBLE_SUFFIX) {
        normalizedToken
    } else {
        normalizedToken.take(MASK_VISIBLE_PREFIX) + "..." + normalizedToken.takeLast(MASK_VISIBLE_SUFFIX)
    }
}

// [语法] 这是 ShareSnapshotFile 的扩展函数，相当于 Java 静态工具方法 SnapshotDisplay.toDescriptionText(file)。
// [设计] 为什么这样写：文件类型、大小和路径展示规则集中处理，列表行不需要拼接一堆业务细节。
private fun ShareSnapshotFile.toDescriptionText(): String {
    val pathText = relativePath.toPathText()
    val sizeText = if (type == FileType.Folder) {
        "文件夹"
    } else {
        sizeBytes.toSizeText()
    }
    return "${type.toDisplayName()} | $sizeText | $pathText"
}

// [语法] 这是 ShareSnapshotFile 的扩展函数，相当于 Java 静态工具方法 SnapshotKeys.toStableKey(file)。
// [设计] 为什么这样写：快照 ID 可能在本地数据库中存在；若还没写入 ID，则用源文件和路径兜底，保证 LazyColumn key 尽量稳定。
private fun ShareSnapshotFile.toStableKey(): String {
    return if (snapshotId > 0L) {
        snapshotId.toString()
    } else {
        "${sourceFileId.orEmpty()}-${relativePath.orEmpty()}-$name"
    }
}

// [语法] 这是 String? 的扩展函数，相当于 Java 静态工具方法 PathDisplay.toPathText(path)。
// [设计] 为什么这样写：relativePath 只保存父级路径，根目录用统一文案兜底，避免 UI 出现空字符串。
private fun String?.toPathText(): String {
    return if (isNullOrBlank()) {
        "根目录"
    } else {
        this
    }
}

// [语法] 这是 ShareType 的扩展函数，相当于 Java 静态工具方法 ShareTypeDisplay.toDisplayName(type)。
// [设计] 为什么这样写：领域枚举不直接携带中文 UI 文案，展示转换留在 UI 层，后续改文案不会影响存储值。
private fun ShareType.toDisplayName(): String {
    return when (this) {
        ShareType.SingleFile -> "单文件分享"
        ShareType.Folder -> "文件夹分享"
        ShareType.MultiFile -> "多文件分享"
    }
}

// [语法] 这是 FileType 的扩展函数，相当于 Java 静态工具方法 FileTypeDisplay.toDisplayName(type)。
// [设计] 为什么这样写：分享预览页复用文件类型中文名，不让列表行直接认识 storageValue。
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

// [语法] 这是 FileType 的扩展函数，相当于 Java 静态工具方法 FileTypeDisplay.toShortLabel(type)。
// [设计] 为什么这样写：徽章需要短标签而不是完整中文名，集中映射后后续换成真实图标也只改这里。
private fun FileType.toShortLabel(): String {
    return when (this) {
        FileType.Folder -> "DIR"
        FileType.Video -> "MP4"
        FileType.Txt -> "TXT"
        FileType.Image -> "IMG"
        FileType.Audio -> "AUD"
        FileType.Other -> "FILE"
    }
}

// [语法] 这是 Long 的扩展函数，相当于 Java 静态工具方法 SizeFormatter.toSizeText(sizeBytes)。
// [设计] 为什么这样写：分享快照保留的是字节数，UI 层负责把它转成用户可读的 KB/MB 文案。
private fun Long.toSizeText(): String {
    val kb = 1024L
    val mb = kb * 1024L
    return when {
        this >= mb -> "${this / mb} MB"
        this >= kb -> "${this / kb} KB"
        else -> "$this B"
    }
}

// [设计] 为什么这样写：分享码脱敏时保留前 6 位，便于用户识别是哪一次分享。
private const val MASK_VISIBLE_PREFIX = 6

// [设计] 为什么这样写：分享码脱敏时保留后 4 位，和前缀组合能降低不同分享码看起来完全相同的概率。
private const val MASK_VISIBLE_SUFFIX = 4
