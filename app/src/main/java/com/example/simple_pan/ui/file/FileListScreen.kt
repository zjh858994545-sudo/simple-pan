package com.example.simple_pan.ui.file

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    val snackbarHostState = remember { SnackbarHostState() }
    // [语法] LaunchedEffect 会在 Composable 进入组合时启动协程，并在离开组合时自动取消。
    // [设计] 为什么这样写：Snackbar 是一次性 Effect，必须在 Screen 层收集；这样 ViewModel 只发事件，不直接依赖 Compose UI 组件。
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            // [设计] 为什么这样写：当前步骤只验证“点击 -> OpenFileUseCase -> Effect”链路，TXT 页面和系统播放器会在后续步骤把这里的占位 Snackbar 换成真实打开动作。
            when (effect) {
                is FileListEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is FileListEffect.OpenTxtReader -> {
                    snackbarHostState.showSnackbar("TXT 阅读器下一步接入：${effect.fileName}")
                }
                is FileListEffect.OpenVideoPlayer -> {
                    snackbarHostState.showSnackbar("视频播放器下一步接入：${effect.fileName}")
                }
            }
        }
    }
    // [语法] rememberLauncherForActivityResult 是 Compose 的状态保存 API，尾随 lambda 会在系统选择器返回结果时执行。
    // [设计] 为什么这样写：SAF 文件选择属于 Activity Result API，放在 Screen 层最合适；选中后的 Uri 立即交给 ViewModel，业务层不依赖 Composable。
    val uploadFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onIntent(FileListIntent.UploadPickedFile(uri.toString()))
        }
    }

    // [设计] 为什么这样写：Scaffold 只承载 SnackbarHost，不改变文件列表的数据来源；上传提示不会覆盖或替换列表内容。
    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { contentPadding ->
        FileListContent(
            modifier = Modifier.padding(contentPadding),
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
            onFileClick = { file ->
                viewModel.onIntent(FileListIntent.OpenFile(file.fileId))
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
            },
            onToggleSelectAllVisible = {
                viewModel.onIntent(FileListIntent.ToggleSelectAllVisible)
            },
            onOpenRenameDialog = {
                viewModel.onIntent(FileListIntent.OpenRenameDialog)
            },
            onDismissRenameDialog = {
                viewModel.onIntent(FileListIntent.DismissRenameDialog)
            },
            onRenameInputChange = { inputName ->
                viewModel.onIntent(FileListIntent.ChangeRenameInput(inputName))
            },
            onConfirmRename = {
                viewModel.onIntent(FileListIntent.ConfirmRename)
            },
            onOpenDeleteDialog = {
                viewModel.onIntent(FileListIntent.OpenDeleteDialog)
            },
            onDismissDeleteDialog = {
                viewModel.onIntent(FileListIntent.DismissDeleteDialog)
            },
            onConfirmDelete = {
                viewModel.onIntent(FileListIntent.ConfirmDelete)
            },
            onOpenMoveDialog = {
                viewModel.onIntent(FileListIntent.OpenMoveDialog)
            },
            onDismissMoveDialog = {
                viewModel.onIntent(FileListIntent.DismissMoveDialog)
            },
            onEnterMoveTargetFolder = { folder ->
                viewModel.onIntent(
                    FileListIntent.EnterMoveTargetFolder(
                        folderId = folder.fileId,
                        folderName = folder.name
                    )
                )
            },
            onBackMoveTargetFolder = {
                viewModel.onIntent(FileListIntent.BackMoveTargetFolder)
            },
            onConfirmMove = {
                viewModel.onIntent(FileListIntent.ConfirmMove)
            },
            onUploadClick = {
                uploadFileLauncher.launch(UPLOAD_MIME_TYPES)
            }
        )
    }
}

// [设计] 为什么这样写：把纯展示内容拆出来，后续写 Preview 或 UI 测试时可以直接传入 State，不需要真的启动 Hilt 和数据库。
@Composable
private fun FileListContent(
    modifier: Modifier = Modifier,
    state: FileListState,
    onRetry: () -> Unit,
    onFolderClick: (CloudFile) -> Unit,
    onFileClick: (CloudFile) -> Unit,
    onBackToParent: () -> Unit,
    onFilterChange: (FileFilter) -> Unit,
    onEnterManageMode: () -> Unit,
    onExitManageMode: () -> Unit,
    onToggleFileSelection: (CloudFile) -> Unit,
    onToggleSelectAllVisible: () -> Unit,
    onOpenRenameDialog: () -> Unit,
    onDismissRenameDialog: () -> Unit,
    onRenameInputChange: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onOpenDeleteDialog: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDelete: () -> Unit,
    onOpenMoveDialog: () -> Unit,
    onDismissMoveDialog: () -> Unit,
    onEnterMoveTargetFolder: (CloudFile) -> Unit,
    onBackMoveTargetFolder: () -> Unit,
    onConfirmMove: () -> Unit,
    onUploadClick: () -> Unit
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                    selectedCount = state.selectedFileIds.size,
                    canSelectAllVisible = state.files.isNotEmpty(),
                    isAllVisibleSelected = state.files.isNotEmpty() &&
                        state.files.all { file -> file.fileId in state.selectedFileIds },
                    onBackToParent = onBackToParent,
                    onEnterManageMode = onEnterManageMode,
                    onExitManageMode = onExitManageMode,
                    onToggleSelectAllVisible = onToggleSelectAllVisible
                )
                Spacer(modifier = Modifier.height(12.dp))
                FileFilterBar(
                    selectedFilter = state.filter,
                    onFilterChange = onFilterChange
                )
                Spacer(modifier = Modifier.height(8.dp))
                FileSortSummary(sortType = state.sortType)
                Spacer(modifier = Modifier.height(12.dp))

                // [设计] 为什么这样写：列表区域用 weight 占据剩余空间，管理态底部操作栏才能稳定固定在底部，不会被 LazyColumn 撑出屏幕。
                Box(modifier = Modifier.weight(1f)) {
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
                            onFileClick = onFileClick,
                            onToggleFileSelection = onToggleFileSelection
                        )
                    }
                }

                if (state.isManageMode) {
                    FileManageActionBar(
                        selectedCount = state.selectedFileIds.size,
                        onShareClick = {},
                        onDeleteClick = onOpenDeleteDialog,
                        onMoveClick = onOpenMoveDialog,
                        onRenameClick = onOpenRenameDialog
                    )
                }

                if (state.renameDialog.isVisible) {
                    RenameFileDialog(
                        dialogState = state.renameDialog,
                        onNameChange = onRenameInputChange,
                        onDismiss = onDismissRenameDialog,
                        onConfirm = onConfirmRename
                    )
                }

                if (state.deleteDialog.isVisible) {
                    DeleteFilesDialog(
                        dialogState = state.deleteDialog,
                        onDismiss = onDismissDeleteDialog,
                        onConfirm = onConfirmDelete
                    )
                }

                if (state.moveDialog.isVisible) {
                    MoveFilesDialog(
                        dialogState = state.moveDialog,
                        onEnterFolder = onEnterMoveTargetFolder,
                        onBackToParent = onBackMoveTargetFolder,
                        onDismiss = onDismissMoveDialog,
                        onConfirm = onConfirmMove
                    )
                }
            }

            if (!state.isManageMode) {
                UploadFloatingActionButton(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    isUploading = state.isUploading,
                    onUploadClick = onUploadClick
                )
            }
        }
    }
}

// [设计] 为什么这样写：上传入口固定在文件页右下角，符合“+ 按钮添加文件”的常见交互；管理模式隐藏它，避免和底部操作栏语义冲突。
@Composable
private fun UploadFloatingActionButton(
    modifier: Modifier,
    isUploading: Boolean,
    onUploadClick: () -> Unit
) {
    FloatingActionButton(
        modifier = modifier
            .padding(20.dp)
            .width(56.dp)
            .height(56.dp),
        containerColor = if (isUploading) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        onClick = {
            if (!isUploading) {
                onUploadClick()
            }
        }
    ) {
        if (isUploading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .width(24.dp)
                    .height(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// [设计] 为什么这样写：阶段 4 先支持单文件上传且不限制文件种类，后续 TXT 阅读器和视频播放会根据文件类型分别处理。
private val UPLOAD_MIME_TYPES = arrayOf("*/*")

// [设计] 为什么这样写：底部操作栏只依赖 selectedCount，就能先完成管理态交互骨架；真正的分享/删除/移动/重命名逻辑留给后续步骤分别接入。
@Composable
private fun FileManageActionBar(
    selectedCount: Int,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoveClick: () -> Unit,
    onRenameClick: () -> Unit
) {
    val hasSelection = selectedCount > 0
    val canRename = selectedCount == 1

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FileManageActionButton(
                    modifier = Modifier.weight(1f),
                    text = "分享",
                    enabled = hasSelection,
                    onClick = onShareClick
                )
                FileManageActionButton(
                    modifier = Modifier.weight(1f),
                    text = "删除",
                    enabled = hasSelection,
                    onClick = onDeleteClick
                )
                FileManageActionButton(
                    modifier = Modifier.weight(1f),
                    text = "移动",
                    enabled = hasSelection,
                    onClick = onMoveClick
                )
                FileManageActionButton(
                    modifier = Modifier.weight(1f),
                    text = "重命名",
                    enabled = canRename,
                    onClick = onRenameClick
                )
            }
        }
    }
}

// [设计] 为什么这样写：四个操作按钮共享同一套尺寸和文字样式，后续接入真实动作时只改回调，不改布局规则。
@Composable
private fun FileManageActionButton(
    modifier: Modifier,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick
    ) {
        Text(
            text = text,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

// [设计] 为什么这样写：弹窗只展示和收集输入，不直接访问 Repository；重命名校验和写库统一回到 ViewModel，保持 MVI 数据流清晰。
@Composable
private fun RenameFileDialog(
    dialogState: RenameDialogState,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!dialogState.isSubmitting) {
                onDismiss()
            }
        },
        title = {
            Text(text = "重命名")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "原名称：${dialogState.originalName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = dialogState.editableName,
                        onValueChange = onNameChange,
                        enabled = !dialogState.isSubmitting,
                        singleLine = true,
                        isError = dialogState.errorMessage != null,
                        label = {
                            Text(text = "名称")
                        },
                        supportingText = {
                            if (dialogState.errorMessage != null) {
                                Text(text = dialogState.errorMessage)
                            }
                        }
                    )
                    if (dialogState.preservedExtension.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            modifier = Modifier.padding(top = 18.dp),
                            text = dialogState.preservedExtension,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !dialogState.isSubmitting,
                onClick = onConfirm
            ) {
                Text(text = if (dialogState.isSubmitting) "处理中" else "确定")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !dialogState.isSubmitting,
                onClick = onDismiss
            ) {
                Text(text = "取消")
            }
        }
    )
}

// [设计] 为什么这样写：删除确认是防误操作边界，弹窗只负责展示风险和收集确认，真正软删除仍回到 ViewModel 执行。
@Composable
private fun DeleteFilesDialog(
    dialogState: DeleteDialogState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!dialogState.isSubmitting) {
                onDismiss()
            }
        },
        title = {
            Text(text = "确认删除")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "将删除选中的 ${dialogState.selectedCount} 项。",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (dialogState.containsFolder) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "其中包含文件夹，将同时删除文件夹内文件。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "本操作为软删除，列表会在 Room 更新后自动刷新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dialogState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = dialogState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !dialogState.isSubmitting,
                onClick = onConfirm
            ) {
                Text(text = if (dialogState.isSubmitting) "删除中" else "删除")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !dialogState.isSubmitting,
                onClick = onDismiss
            ) {
                Text(text = "取消")
            }
        }
    )
}

// [设计] 为什么这样写：移动弹窗内部维护目标目录选择，但不直接改数据库；用户确认后仍由 ViewModel 统一校验并调用 Repository。
@Composable
private fun MoveFilesDialog(
    dialogState: MoveDialogState,
    onEnterFolder: (CloudFile) -> Unit,
    onBackToParent: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isCurrentTargetForbidden = dialogState.currentTargetFolderId != null &&
        dialogState.currentTargetFolderId in dialogState.forbiddenFolderIds
    AlertDialog(
        onDismissRequest = {
            if (!dialogState.isSubmitting) {
                onDismiss()
            }
        },
        title = {
            Text(text = "移动到")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "已选择 ${dialogState.selectedCount} 项",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "目标：${dialogState.currentTargetFolderName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (dialogState.targetFolderStack.isNotEmpty()) {
                        TextButton(
                            enabled = !dialogState.isLoading && !dialogState.isSubmitting,
                            onClick = onBackToParent
                        ) {
                            Text(text = "返回")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    dialogState.isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    dialogState.targetFolders.isEmpty() -> {
                        Text(
                            text = "当前目录下没有子文件夹",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.height(180.dp)) {
                            items(
                                items = dialogState.targetFolders,
                                key = { folder -> folder.fileId }
                            ) { folder ->
                                MoveTargetFolderRow(
                                    folder = folder,
                                    isForbidden = folder.fileId in dialogState.forbiddenFolderIds,
                                    isSubmitting = dialogState.isSubmitting,
                                    onEnterFolder = onEnterFolder
                                )
                            }
                        }
                    }
                }
                if (dialogState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = dialogState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !dialogState.isLoading && !dialogState.isSubmitting && !isCurrentTargetForbidden,
                onClick = onConfirm
            ) {
                Text(text = if (dialogState.isSubmitting) "移动中" else "移动到此处")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !dialogState.isSubmitting,
                onClick = onDismiss
            ) {
                Text(text = "取消")
            }
        }
    )
}

// [设计] 为什么这样写：目标文件夹行只表达“进入这个候选目录”，非法目录禁用显示，真正移动动作只由弹窗确认按钮触发。
@Composable
private fun MoveTargetFolderRow(
    folder: CloudFile,
    isForbidden: Boolean,
    isSubmitting: Boolean,
    onEnterFolder: (CloudFile) -> Unit
) {
    TextButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !isForbidden && !isSubmitting,
        onClick = {
            onEnterFolder(folder)
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = folder.name,
                maxLines = 1
            )
            Text(
                text = if (isForbidden) "不可移动" else "进入",
                style = MaterialTheme.typography.bodySmall,
                color = if (isForbidden) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
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
    selectedCount: Int,
    canSelectAllVisible: Boolean,
    isAllVisibleSelected: Boolean,
    onBackToParent: () -> Unit,
    onEnterManageMode: () -> Unit,
    onExitManageMode: () -> Unit,
    onToggleSelectAllVisible: () -> Unit
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
        if (isManageMode) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "已选择 $selectedCount 项",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                // [设计] 为什么这样写：全选按钮跟随当前可见列表状态切换文案，用户能一眼知道下一次点击会执行什么动作。
                Button(
                    onClick = onToggleSelectAllVisible,
                    enabled = canSelectAllVisible
                ) {
                    Text(text = if (isAllVisibleSelected) "取消全选" else "全选")
                }
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
    onFileClick: (CloudFile) -> Unit,
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
                onFileClick = onFileClick,
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
    onFileClick: (CloudFile) -> Unit,
    onToggleFileSelection: (CloudFile) -> Unit
) {
    // [语法] pointerInput + detectTapGestures 类似 Java 里给 View 设置手势监听器，可以同时处理点击和长按。
    // [设计] 为什么这样写：普通态点击文件夹负责浏览、点击普通文件只表达打开意图；管理态点击整行切换选择，避免浏览和批量操作两套交互互相抢焦点。
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
                } else {
                    onFileClick(file)
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
