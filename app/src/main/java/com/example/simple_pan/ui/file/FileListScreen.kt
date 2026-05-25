package com.example.simple_pan.ui.file

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
        },
        onFolderClick = { folder ->
            viewModel.onIntent(
                FileListIntent.EnterFolder(
                    folderId = folder.fileId,
                    folderName = folder.name
                )
            )
        },
        onBackToParent = {
            viewModel.onIntent(FileListIntent.BackToParent)
        },
        onFilterChange = { filter ->
            viewModel.onIntent(FileListIntent.ChangeFilter(filter))
        },
        onEnterManageMode = {
            viewModel.onIntent(FileListIntent.EnterManageMode)
        },
        onExitManageMode = {
            viewModel.onIntent(FileListIntent.ExitManageMode)
        },
        onToggleFileSelection = { file ->
            viewModel.onIntent(FileListIntent.ToggleFileSelection(file.fileId))
        }
    )
}

// [设计] 为什么这样写：把纯展示内容拆出来，后续写 Preview 或 UI 测试时可以直接传入 State，不需要真的启动 Hilt 和数据库。
@Composable
private fun FileListContent(
    state: FileListState,
    onRetry: () -> Unit,
    onFolderClick: (CloudFile) -> Unit,
    onBackToParent: () -> Unit,
    onFilterChange: (FileFilter) -> Unit,
    onEnterManageMode: () -> Unit,
    onExitManageMode: () -> Unit,
    onToggleFileSelection: (CloudFile) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            FileListHeader(
                folderName = state.currentFolderName,
                fileCount = state.files.size,
                canBackToParent = state.folderStack.isNotEmpty(),
                isManageMode = state.isManageMode,
                onBackToParent = onBackToParent,
                onEnterManageMode = onEnterManageMode,
                onExitManageMode = onExitManageMode
            )
            Spacer(modifier = Modifier.height(12.dp))
            FileFilterBar(
                selectedFilter = state.filter,
                onFilterChange = onFilterChange
            )
            Spacer(modifier = Modifier.height(8.dp))
            FileSortSummary(sortType = state.sortType)
            Spacer(modifier = Modifier.height(12.dp))

            when {
                state.isLoading -> FileListLoading()
                state.errorMessage != null -> FileListError(
                    message = state.errorMessage,
                    onRetry = onRetry
                )
                state.files.isEmpty() -> FileListEmpty(filter = state.filter)
                else -> FileListItems(
                    files = state.files,
                    isManageMode = state.isManageMode,
                    selectedFileIds = state.selectedFileIds,
                    onFolderClick = onFolderClick,
                    onToggleFileSelection = onToggleFileSelection
                )
            }
        }
    }
}

// [设计] 为什么这样写：筛选栏放在列表页顶部，用户能在当前目录内快速切换全部/图片/视频/文档；筛选动作仍回到 ViewModel 处理。
@Composable
private fun FileFilterBar(
    selectedFilter: FileFilter,
    onFilterChange: (FileFilter) -> Unit
) {
    // [语法] rememberScrollState() 返回 Compose 状态对象，horizontalScroll 使用它保存横向滚动位置。
    // [设计] 为什么这样写：小屏下四个筛选项可能放不下，横向滚动能避免文字挤压或换行导致布局跳动。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (filter in FileFilter.entries) {
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(text = filter.toDisplayName())
                }
            )
        }
    }
}

// [设计] 为什么这样写：阶段 2 只要求综合排序，先显示当前排序规则而不做下拉菜单，避免提前引入更多排序状态。
@Composable
private fun FileSortSummary(sortType: FileSortType) {
    Text(
        text = "排序：${sortType.toDisplayName()}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// [设计] 为什么这样写：头部展示当前目录名称和数量，让用户确认已经进入子目录；返回按钮留到下一步单独实现。
@Composable
private fun FileListHeader(
    folderName: String,
    fileCount: Int,
    canBackToParent: Boolean,
    isManageMode: Boolean,
    onBackToParent: () -> Unit,
    onEnterManageMode: () -> Unit,
    onExitManageMode: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (canBackToParent) {
            // [设计] 为什么这样写：先做页面内返回按钮，不接系统返回键，符合本步骤“小而可验收”的边界。
            Button(onClick = onBackToParent) {
                Text(text = "返回上一级")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$fileCount 项",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // [设计] 为什么这样写：管理模式先放在列表头部入口，用户能主动进入；同一个按钮在管理态变成“完成”，让退出路径保持清晰。
            Button(
                onClick = if (isManageMode) onExitManageMode else onEnterManageMode
            ) {
                Text(text = if (isManageMode) "完成" else "管理")
            }
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
private fun FileListEmpty(filter: FileFilter) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (filter == FileFilter.All) "暂无文件" else "暂无该类型文件",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// [设计] 为什么这样写：LazyColumn 用稳定 key 绑定 fileId，后续排序/删除/管理态选择时不会因为 index 变化导致行状态串位。
@Composable
private fun FileListItems(
    files: List<CloudFile>,
    isManageMode: Boolean,
    selectedFileIds: Set<String>,
    onFolderClick: (CloudFile) -> Unit,
    onToggleFileSelection: (CloudFile) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // [语法] items 的 key = { file -> ... } 是尾随 lambda，file 是显式命名参数，类似 Java 回调里的参数名。
        items(
            items = files,
            key = { file -> file.fileId }
        ) { file ->
            FileRow(
                file = file,
                isManageMode = isManageMode,
                isSelected = file.fileId in selectedFileIds,
                onFolderClick = onFolderClick,
                onToggleFileSelection = onToggleFileSelection
            )
            HorizontalDivider()
        }
    }
}

// [设计] 为什么这样写：列表行只展示领域模型 CloudFile，不接触 Entity 或 DTO，证明 UI 层已经和数据源细节解耦。
@Composable
private fun FileRow(
    file: CloudFile,
    isManageMode: Boolean,
    isSelected: Boolean,
    onFolderClick: (CloudFile) -> Unit,
    onToggleFileSelection: (CloudFile) -> Unit
) {
    // [语法] pointerInput + detectTapGestures 类似 Java 里给 View 设置手势监听器，可以同时处理点击和长按。
    // [设计] 为什么这样写：普通态点击文件夹仍负责浏览；管理态点击整行切换选择，长按则进入管理态并选中当前文件。
    val rowModifier = Modifier.pointerInput(file.fileId, file.type, isManageMode, isSelected) {
        detectTapGestures(
            onLongPress = {
                onToggleFileSelection(file)
            },
            onTap = {
                if (isManageMode) {
                    onToggleFileSelection(file)
                } else if (file.type == FileType.Folder) {
                    onFolderClick(file)
                }
            }
        )
    }

    ListItem(
        modifier = rowModifier,
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
        },
        trailingContent = if (isManageMode) {
            {
                // [设计] 为什么这样写：管理态用系统 Checkbox 表达二元选择，视觉语义明确；状态仍由 selectedFileIds 驱动，避免控件自己记状态。
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                        onToggleFileSelection(file)
                    }
                )
            }
        } else {
            null
        }
    )
}

// [设计] 为什么这样写：当前阶段不额外引入图标依赖，用固定宽度的类型徽章表达“图标/标识”，既能演示类型差异，也不会破坏依赖约束。
@Composable
private fun FileTypeBadge(fileType: FileType) {
    val badge = fileType.toBadgeSpec()
    Surface(
        modifier = Modifier.width(48.dp),
        color = badge.containerColor,
        contentColor = badge.contentColor,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = badge.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    Spacer(modifier = Modifier.width(8.dp))
}

// [语法] data class 相当于 Java 的 POJO/Bean，自动生成 equals/hashCode/toString/copy，适合承载 UI 徽章需要的几个值。
// [设计] 为什么这样写：类型徽章不仅需要文字，还需要配色；集中成一个对象，FileTypeBadge 不需要写一堆分散的 when。
private data class FileTypeBadgeSpec(
    val shortLabel: String,
    val containerColor: Color,
    val contentColor: Color
)

// [语法] 这是扩展函数，相当于 Java 静态工具方法 FileTypeDisplay.toBadgeSpec(fileType, colorScheme)。
// [设计] 为什么这样写：文件类型到视觉标识的映射集中在一处，后续如果换成真实图标，只需要替换这里和 FileTypeBadge。
@Composable
private fun FileType.toBadgeSpec(): FileTypeBadgeSpec {
    val colorScheme = MaterialTheme.colorScheme
    return when (this) {
        FileType.Folder -> FileTypeBadgeSpec(
            shortLabel = "DIR",
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer
        )
        FileType.Video -> FileTypeBadgeSpec(
            shortLabel = "MP4",
            containerColor = colorScheme.tertiaryContainer,
            contentColor = colorScheme.onTertiaryContainer
        )
        FileType.Txt -> FileTypeBadgeSpec(
            shortLabel = "TXT",
            containerColor = colorScheme.secondaryContainer,
            contentColor = colorScheme.onSecondaryContainer
        )
        FileType.Image -> FileTypeBadgeSpec(
            shortLabel = "IMG",
            containerColor = colorScheme.surfaceVariant,
            contentColor = colorScheme.onSurfaceVariant
        )
        FileType.Audio -> FileTypeBadgeSpec(
            shortLabel = "AUD",
            containerColor = colorScheme.inversePrimary,
            contentColor = colorScheme.onPrimaryContainer
        )
        FileType.Other -> FileTypeBadgeSpec(
            shortLabel = "FILE",
            containerColor = colorScheme.surfaceVariant,
            contentColor = colorScheme.onSurfaceVariant
        )
    }
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

// [语法] 这是扩展函数，相当于 Java 静态工具方法 FileFilterDisplay.toDisplayName(filter)。
// [设计] 为什么这样写：筛选枚举本身只表达策略，中文展示文案属于 UI 层，集中在这里便于后续改文案。
private fun FileFilter.toDisplayName(): String {
    return when (this) {
        FileFilter.All -> "全部"
        FileFilter.Image -> "图片"
        FileFilter.Video -> "视频"
        FileFilter.Document -> "文档"
    }
}

// [语法] 这是扩展函数，相当于 Java 静态工具方法 FileSortTypeDisplay.toDisplayName(sortType)。
// [设计] 为什么这样写：排序枚举只表达策略，具体文案放 UI 层，后续如果增加排序菜单也能复用。
private fun FileSortType.toDisplayName(): String {
    return when (this) {
        FileSortType.Comprehensive -> "综合排序（文件夹优先 · 置顶优先 · 更新时间）"
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
