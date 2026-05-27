package com.example.simple_pan.ui.file

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simple_pan.domain.model.CloudFile
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.UploadFileResult
import com.example.simple_pan.domain.model.UploadSizeCheckResult
import com.example.simple_pan.domain.repository.FileRepository
import com.example.simple_pan.domain.usecase.UploadFileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

// [语法] @HiltViewModel 告诉 Hilt 这个 ViewModel 由依赖注入创建，类似 Java 项目里用 DI 容器创建 Controller/ViewModel。
// [设计] 为什么这样写：ViewModel 依赖 domain 层 Repository/UseCase，不直接认识 Room、DAO、SAF 复制细节或 mock JSON，保持 UI 层边界干净。
@HiltViewModel
class FileListViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val uploadFileUseCase: UploadFileUseCase
) : ViewModel() {
    // [语法] MutableStateFlow 类似 Java Observable + 当前值缓存；私有可变、公开只读是 Kotlin 常见封装方式。
    // [设计] 为什么这样写：UI 只能观察 state，不能直接改 state，所有状态变化都通过 ViewModel 处理。
    private val _state = MutableStateFlow(FileListState())
    val state: StateFlow<FileListState> = _state.asStateFlow()

    // [语法] SharedFlow 类似 Java 的事件流；和 StateFlow 不同，它不保存“当前状态”，更适合 Toast/Snackbar 这种一次性事件。
    // [设计] 为什么这样写：上传成功或失败提示不应该写进 FileListState，否则重组后可能重复显示；Effect 流能保持 MVI 的 State/Effect 边界。
    private val _effect = MutableSharedFlow<FileListEffect>(extraBufferCapacity = 1)
    val effect: SharedFlow<FileListEffect> = _effect.asSharedFlow()

    private var loadJob: Job? = null
    private var currentFolderFiles: List<CloudFile> = emptyList()

    init {
        loadFiles(
            folderId = null,
            folderName = "根目录",
            folderStack = emptyList(),
            shouldInitializeMock = true
        )
    }

    // [设计] 为什么这样写：Composable 只发送 Intent，不直接调用加载函数，保留 MVI 的“用户行为 -> 状态变化”链路。
    fun onIntent(intent: FileListIntent) {
        when (intent) {
            FileListIntent.Retry -> {
                val currentState = _state.value
                loadFiles(
                    folderId = currentState.currentFolderId,
                    folderName = currentState.currentFolderName,
                    folderStack = currentState.folderStack,
                    shouldInitializeMock = true
                )
            }
            is FileListIntent.UploadPickedFile -> {
                uploadPickedFile(intent.uriString)
            }
            is FileListIntent.EnterFolder -> {
                val currentState = _state.value
                // [语法] + 用在 List 上会生成新列表，不会修改原列表，类似 Java 里 new ArrayList(old).add(item) 后返回新对象。
                // [设计] 为什么这样写：进入子目录前把当前目录压入路径栈，下一步实现“返回上一级”时可以直接从栈顶恢复。
                val nextStack = currentState.folderStack + FolderCrumb(
                    folderId = currentState.currentFolderId,
                    folderName = currentState.currentFolderName
                )
                loadFiles(
                    folderId = intent.folderId,
                    folderName = intent.folderName,
                    folderStack = nextStack,
                    shouldInitializeMock = false
                )
            }
            FileListIntent.BackToParent -> {
                val currentState = _state.value
                // [语法] lastOrNull() 是 Kotlin 标准库函数，相当于 Java 里先判断 list 是否为空再取最后一个，避免空列表异常。
                // [设计] 为什么这样写：返回上一级的目标必须由 ViewModel 根据路径栈决定，UI 只表达“我要返回”这个意图。
                val parentCrumb = currentState.folderStack.lastOrNull()
                if (parentCrumb != null) {
                    loadFiles(
                        folderId = parentCrumb.folderId,
                        folderName = parentCrumb.folderName,
                        // [语法] dropLast(1) 会生成去掉最后一个元素的新列表，不修改原列表。
                        // [设计] 为什么这样写：路径栈保持不可变更新，StateFlow 才能清楚表达“路径发生变化”。
                        folderStack = currentState.folderStack.dropLast(1),
                        shouldInitializeMock = false
                    )
                } else {
                    _state.update { state ->
                        state.copy(errorMessage = null)
                    }
                }
            }
            is FileListIntent.ChangeFilter -> {
                _state.update { currentState ->
                    val nextFiles = currentFolderFiles.toVisibleFiles(
                        filter = intent.filter,
                        sortType = currentState.sortType
                    )
                    currentState.copy(
                        filter = intent.filter,
                        files = nextFiles,
                        selectedFileIds = currentState.selectedFileIds.keepOnlyVisible(nextFiles)
                    )
                }
            }
            is FileListIntent.ChangeSort -> {
                _state.update { currentState ->
                    val nextFiles = currentFolderFiles.toVisibleFiles(
                        filter = currentState.filter,
                        sortType = intent.sortType
                    )
                    currentState.copy(
                        sortType = intent.sortType,
                        files = nextFiles,
                        selectedFileIds = currentState.selectedFileIds.keepOnlyVisible(nextFiles)
                    )
                }
            }
            FileListIntent.EnterManageMode -> {
                _state.update { currentState ->
                    // [设计] 为什么这样写：每次进入管理模式都从空选择开始，避免上一次残留选择影响当前目录的操作。
                    currentState.copy(
                        isManageMode = true,
                        selectedFileIds = emptySet()
                    )
                }
            }
            FileListIntent.ExitManageMode -> {
                _state.update { currentState ->
                    // [设计] 为什么这样写：退出管理模式时集中清空选中集合，后续无论从“完成”按钮还是其它入口退出，都不会留下脏状态。
                    currentState.copy(
                        isManageMode = false,
                        selectedFileIds = emptySet()
                    )
                }
            }
            is FileListIntent.ToggleFileSelection -> {
                _state.update { currentState ->
                    // [语法] in 用来判断元素是否在集合中，类似 Java 的 selectedFileIds.contains(fileId)。
                    // [设计] 为什么这样写：用不可变 Set 生成新集合，StateFlow 能明确发出新状态，Compose 也能稳定刷新对应行。
                    val nextSelectedFileIds = if (intent.fileId in currentState.selectedFileIds) {
                        currentState.selectedFileIds - intent.fileId
                    } else {
                        currentState.selectedFileIds + intent.fileId
                    }
                    currentState.copy(
                        isManageMode = true,
                        selectedFileIds = nextSelectedFileIds
                    )
                }
            }
            FileListIntent.ToggleSelectAllVisible -> {
                _state.update { currentState ->
                    val visibleFileIds = currentState.files.map { file -> file.fileId }.toSet()
                    // [语法] all 是 Kotlin 集合函数，相当于 Java Stream 的 allMatch。
                    // [设计] 为什么这样写：全选只针对当前可见列表；如果已经全部选中，再次点击就只取消当前可见项。
                    val isAllVisibleSelected = visibleFileIds.isNotEmpty() &&
                        visibleFileIds.all { fileId -> fileId in currentState.selectedFileIds }
                    val nextSelectedFileIds = if (isAllVisibleSelected) {
                        currentState.selectedFileIds - visibleFileIds
                    } else {
                        currentState.selectedFileIds + visibleFileIds
                    }
                    currentState.copy(
                        isManageMode = true,
                        selectedFileIds = nextSelectedFileIds
                    )
                }
            }
            FileListIntent.OpenRenameDialog -> {
                val currentState = _state.value
                // [语法] singleOrNull() 会在集合只有一个元素时返回它，否则返回 null，相当于 Java 里先判断 size == 1。
                // [设计] 为什么这样写：重命名只允许单选，ViewModel 再兜底一次，避免 UI 状态异常时打开错误目标。
                val selectedFileId = currentState.selectedFileIds.singleOrNull()
                val targetFile = currentState.files.firstOrNull { file -> file.fileId == selectedFileId }
                if (targetFile != null) {
                    _state.update { state ->
                        state.copy(
                            renameDialog = RenameDialogState(
                                isVisible = true,
                                fileId = targetFile.fileId,
                                originalName = targetFile.name,
                                editableName = targetFile.toEditableRenameName(),
                                preservedExtension = targetFile.toPreservedExtension()
                            )
                        )
                    }
                }
            }
            FileListIntent.DismissRenameDialog -> {
                _state.update { currentState ->
                    currentState.copy(renameDialog = RenameDialogState())
                }
            }
            is FileListIntent.ChangeRenameInput -> {
                _state.update { currentState ->
                    currentState.copy(
                        renameDialog = currentState.renameDialog.copy(
                            editableName = intent.inputName,
                            errorMessage = null
                        )
                    )
                }
            }
            FileListIntent.ConfirmRename -> {
                confirmRename()
            }
            FileListIntent.OpenDeleteDialog -> {
                val currentState = _state.value
                if (currentState.selectedFileIds.isNotEmpty()) {
                    val selectedFiles = currentState.files.filter { file ->
                        file.fileId in currentState.selectedFileIds
                    }
                    _state.update { state ->
                        state.copy(
                            deleteDialog = DeleteDialogState(
                                isVisible = true,
                                fileIds = currentState.selectedFileIds,
                                selectedCount = currentState.selectedFileIds.size,
                                containsFolder = selectedFiles.any { file -> file.type == FileType.Folder }
                            )
                        )
                    }
                }
            }
            FileListIntent.DismissDeleteDialog -> {
                _state.update { currentState ->
                    currentState.copy(deleteDialog = DeleteDialogState())
                }
            }
            FileListIntent.ConfirmDelete -> {
                confirmDelete()
            }
            FileListIntent.OpenMoveDialog -> {
                openMoveDialog()
            }
            FileListIntent.DismissMoveDialog -> {
                _state.update { currentState ->
                    currentState.copy(moveDialog = MoveDialogState())
                }
            }
            is FileListIntent.EnterMoveTargetFolder -> {
                enterMoveTargetFolder(
                    folderId = intent.folderId,
                    folderName = intent.folderName
                )
            }
            FileListIntent.BackMoveTargetFolder -> {
                backMoveTargetFolder()
            }
            FileListIntent.ConfirmMove -> {
                confirmMove()
            }
        }
    }

    // [设计] 为什么这样写：上传目标目录取自当前 State，UI 只负责把 SAF Uri 交回来；成功后的列表刷新继续依赖 Room Flow，不在这里手动插入列表项。
    private fun uploadPickedFile(uriString: String) {
        if (_state.value.isUploading) {
            return
        }

        val targetFolderId = _state.value.currentFolderId
        _state.update { currentState ->
            currentState.copy(
                isUploading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                when (val result = uploadFileUseCase(uriString, targetFolderId)) {
                    is UploadFileResult.Uploaded -> {
                        _state.update { currentState ->
                            currentState.copy(isUploading = false)
                        }
                        showUploadMessage("上传成功：${result.file.name}")
                    }
                    is UploadFileResult.RejectedBySize -> {
                        showUploadMessage(result.reason.toUploadErrorMessage())
                    }
                    UploadFileResult.TargetFolderUnavailable -> {
                        showUploadMessage("目标文件夹不存在或已被删除")
                    }
                    UploadFileResult.SourceUnavailable -> {
                        showUploadMessage("文件读取失败，请重新选择")
                    }
                    UploadFileResult.StorageUnavailable -> {
                        showUploadMessage("App 私有存储不可用，请稍后重试")
                    }
                    is UploadFileResult.Failed -> {
                        showUploadMessage(result.message.toUploadFailedMessage())
                    }
                }
            } catch (throwable: Throwable) {
                showUploadMessage(throwable.toUploadMessage())
            }
        }
    }

    // [语法] suspend fun 表示挂起函数，类似 Java Future/回调；这里需要在协程里发送 SharedFlow 事件。
    // [设计] 为什么这样写：上传提示只通过 Effect 发出一次，同时把 isUploading 复位，避免失败后 + 按钮一直处于不可用状态。
    private suspend fun showUploadMessage(message: String) {
        _state.update { currentState ->
            currentState.copy(
                isUploading = false,
                errorMessage = null
            )
        }
        _effect.emit(FileListEffect.ShowMessage(message))
    }

    // [设计] 为什么这样写：确认重命名包含校验、查库和写库，集中在 ViewModel 能保持 UI 弹窗足够薄，也让 MVI 状态变化可追踪。
    private fun confirmRename() {
        val dialog = _state.value.renameDialog
        val fileId = dialog.fileId ?: return
        val trimmedEditableName = dialog.editableName.trim()

        if (trimmedEditableName.isBlank()) {
            _state.update { currentState ->
                currentState.copy(
                    renameDialog = currentState.renameDialog.copy(
                        errorMessage = "名称不能为空"
                    )
                )
            }
            return
        }

        val finalName = trimmedEditableName + dialog.preservedExtension
        _state.update { currentState ->
            currentState.copy(
                renameDialog = currentState.renameDialog.copy(
                    errorMessage = null,
                    isSubmitting = true
                )
            )
        }

        viewModelScope.launch {
            try {
                val activeFile = fileRepository.findActiveFile(fileId)
                if (activeFile == null) {
                    showRenameError("文件不存在或已被删除")
                    return@launch
                }

                val hasDuplicateName = fileRepository.hasActiveNameInFolder(
                    parentId = activeFile.parentId,
                    name = finalName,
                    excludeFileId = activeFile.fileId
                )
                if (hasDuplicateName) {
                    showRenameError("当前目录已存在同名文件")
                    return@launch
                }

                val renamed = if (finalName == activeFile.name) {
                    true
                } else {
                    fileRepository.renameFile(
                        fileId = activeFile.fileId,
                        newName = finalName,
                        updatedAt = System.currentTimeMillis()
                    )
                }

                if (renamed) {
                    _state.update { currentState ->
                        currentState.copy(
                            renameDialog = RenameDialogState(),
                            isManageMode = false,
                            selectedFileIds = emptySet()
                        )
                    }
                } else {
                    showRenameError("重命名失败，请重试")
                }
            } catch (throwable: Throwable) {
                showRenameError(throwable.toUserMessage())
            }
        }
    }

    // [设计] 为什么这样写：重命名错误只显示在弹窗里，不污染页面级 errorMessage，避免列表被错误页替换。
    private fun showRenameError(message: String) {
        _state.update { currentState ->
            currentState.copy(
                renameDialog = currentState.renameDialog.copy(
                    errorMessage = message,
                    isSubmitting = false
                )
            )
        }
    }

    // [设计] 为什么这样写：删除确认后只调用 Repository 的软删除能力，列表刷新交给 Room Flow，ViewModel 不手动从列表里移除项目。
    private fun confirmDelete() {
        val dialog = _state.value.deleteDialog
        if (dialog.fileIds.isEmpty()) {
            return
        }

        _state.update { currentState ->
            currentState.copy(
                deleteDialog = currentState.deleteDialog.copy(
                    errorMessage = null,
                    isSubmitting = true
                )
            )
        }

        viewModelScope.launch {
            try {
                val deletedCount = fileRepository.deleteFiles(
                    fileIds = dialog.fileIds.toList(),
                    deletedAt = System.currentTimeMillis()
                )
                if (deletedCount > 0) {
                    _state.update { currentState ->
                        currentState.copy(
                            deleteDialog = DeleteDialogState(),
                            isManageMode = false,
                            selectedFileIds = emptySet()
                        )
                    }
                } else {
                    showDeleteError("文件不存在或已被删除")
                }
            } catch (throwable: Throwable) {
                showDeleteError(throwable.toDeleteMessage())
            }
        }
    }

    // [设计] 为什么这样写：删除错误显示在确认弹窗里，用户可以取消或重试，不把整个文件列表切到错误页。
    private fun showDeleteError(message: String) {
        _state.update { currentState ->
            currentState.copy(
                deleteDialog = currentState.deleteDialog.copy(
                    errorMessage = message,
                    isSubmitting = false
                )
            )
        }
    }

    // [语法] 这是 Throwable 的扩展函数，相当于 Java 静态工具方法 DeleteErrors.toMessage(throwable)。
    // [设计] 为什么这样写：删除失败文案和列表加载失败文案分开，避免弹窗里出现“文件列表加载失败”这种不准确提示。
    private fun Throwable.toDeleteMessage(): String {
        val detail = message
        return if (detail == null || detail.isBlank()) {
            "删除失败，请重试"
        } else {
            "删除失败：$detail"
        }
    }

    // [设计] 为什么这样写：打开移动弹窗时冻结当前选中集合，并预先计算禁止选择的目录，后续 UI 才能明确禁用非法目标。
    private fun openMoveDialog() {
        val selectedFileIds = _state.value.selectedFileIds
        if (selectedFileIds.isEmpty()) {
            return
        }

        _state.update { currentState ->
            currentState.copy(
                moveDialog = MoveDialogState(
                    isVisible = true,
                    fileIds = selectedFileIds,
                    selectedCount = selectedFileIds.size,
                    isLoading = true
                )
            )
        }

        viewModelScope.launch {
            try {
                val selectedFiles = fileRepository.findActiveFiles(selectedFileIds.toList())
                if (selectedFiles.isEmpty()) {
                    showMoveError("文件不存在或已被删除")
                    return@launch
                }

                val forbiddenFolderIds = buildForbiddenMoveTargetIds(selectedFiles)
                val rootFolders = fileRepository.findActiveChildFolders(parentId = null)
                _state.update { currentState ->
                    if (!currentState.moveDialog.isVisible) {
                        currentState
                    } else {
                        currentState.copy(
                            moveDialog = currentState.moveDialog.copy(
                                fileIds = selectedFiles.map { file -> file.fileId }.toSet(),
                                selectedCount = selectedFiles.size,
                                currentTargetFolderId = null,
                                currentTargetFolderName = "根目录",
                                targetFolderStack = emptyList(),
                                targetFolders = rootFolders,
                                forbiddenFolderIds = forbiddenFolderIds,
                                errorMessage = null,
                                isLoading = false
                            )
                        )
                    }
                }
            } catch (throwable: Throwable) {
                showMoveError(throwable.toMoveMessage())
            }
        }
    }

    // [设计] 为什么这样写：进入目标文件夹前先拦截自身/子目录，防止用户在弹窗里浏览到非法目标后再提交。
    private fun enterMoveTargetFolder(folderId: String, folderName: String) {
        val dialog = _state.value.moveDialog
        if (!dialog.isVisible) {
            return
        }
        if (folderId in dialog.forbiddenFolderIds) {
            showMoveError("不能移动到自身或子目录")
            return
        }

        val nextStack = dialog.targetFolderStack + FolderCrumb(
            folderId = dialog.currentTargetFolderId,
            folderName = dialog.currentTargetFolderName
        )
        loadMoveTargetFolder(
            folderId = folderId,
            folderName = folderName,
            folderStack = nextStack
        )
    }

    // [设计] 为什么这样写：移动弹窗里的返回只影响目标选择器，不改变主文件列表当前目录。
    private fun backMoveTargetFolder() {
        val dialog = _state.value.moveDialog
        val parentCrumb = dialog.targetFolderStack.lastOrNull() ?: return
        loadMoveTargetFolder(
            folderId = parentCrumb.folderId,
            folderName = parentCrumb.folderName,
            folderStack = dialog.targetFolderStack.dropLast(1)
        )
    }

    // [设计] 为什么这样写：目标目录候选由 Repository 查询，弹窗只根据 State 渲染；这样移动弹窗和主列表数据流保持一致。
    private fun loadMoveTargetFolder(
        folderId: String?,
        folderName: String,
        folderStack: List<FolderCrumb>
    ) {
        _state.update { currentState ->
            currentState.copy(
                moveDialog = currentState.moveDialog.copy(
                    currentTargetFolderId = folderId,
                    currentTargetFolderName = folderName,
                    targetFolderStack = folderStack,
                    errorMessage = null,
                    isLoading = true
                )
            )
        }

        viewModelScope.launch {
            try {
                val folders = fileRepository.findActiveChildFolders(folderId)
                _state.update { currentState ->
                    if (!currentState.moveDialog.isVisible) {
                        currentState
                    } else {
                        currentState.copy(
                            moveDialog = currentState.moveDialog.copy(
                                targetFolders = folders,
                                errorMessage = null,
                                isLoading = false
                            )
                        )
                    }
                }
            } catch (throwable: Throwable) {
                showMoveError(throwable.toMoveMessage())
            }
        }
    }

    // [设计] 为什么这样写：确认移动时再次从 Repository 读取活动文件和目标文件夹，防止弹窗打开期间文件被删除或目标失效。
    private fun confirmMove() {
        val dialog = _state.value.moveDialog
        if (dialog.fileIds.isEmpty()) {
            return
        }

        val targetParentId = dialog.currentTargetFolderId
        if (targetParentId != null && targetParentId in dialog.forbiddenFolderIds) {
            showMoveError("不能移动到自身或子目录")
            return
        }

        _state.update { currentState ->
            currentState.copy(
                moveDialog = currentState.moveDialog.copy(
                    errorMessage = null,
                    isSubmitting = true
                )
            )
        }

        viewModelScope.launch {
            try {
                val activeFiles = fileRepository.findActiveFiles(dialog.fileIds.toList())
                if (activeFiles.isEmpty()) {
                    showMoveError("文件不存在或已被删除")
                    return@launch
                }

                if (targetParentId != null) {
                    val targetFolder = fileRepository.findActiveFile(targetParentId)
                    if (targetFolder == null || targetFolder.type != FileType.Folder) {
                        showMoveError("目标文件夹不存在")
                        return@launch
                    }
                }

                val forbiddenFolderIds = buildForbiddenMoveTargetIds(activeFiles)
                if (targetParentId != null && targetParentId in forbiddenFolderIds) {
                    showMoveError("不能移动到自身或子目录")
                    return@launch
                }

                if (activeFiles.all { file -> file.parentId == targetParentId }) {
                    showMoveError("文件已在目标文件夹中")
                    return@launch
                }

                val movedCount = fileRepository.moveFiles(
                    fileIds = activeFiles.map { file -> file.fileId },
                    targetParentId = targetParentId,
                    updatedAt = System.currentTimeMillis()
                )
                if (movedCount > 0) {
                    _state.update { currentState ->
                        currentState.copy(
                            moveDialog = MoveDialogState(),
                            isManageMode = false,
                            selectedFileIds = emptySet()
                        )
                    }
                } else {
                    showMoveError("移动失败，请重试")
                }
            } catch (throwable: Throwable) {
                showMoveError(throwable.toMoveMessage())
            }
        }
    }

    // [设计] 为什么这样写：禁止目标由“被移动的文件夹自身 + 所有后代文件夹”组成，普通文件不会产生非法子目录问题。
    private suspend fun buildForbiddenMoveTargetIds(selectedFiles: List<CloudFile>): Set<String> {
        val forbiddenFolderIds = mutableSetOf<String>()
        for (file in selectedFiles) {
            if (file.type == FileType.Folder) {
                forbiddenFolderIds += file.fileId
                forbiddenFolderIds += fileRepository.findActiveDescendantFolderIds(file.fileId)
            }
        }
        return forbiddenFolderIds
    }

    // [设计] 为什么这样写：移动失败文案与删除/重命名分开，弹窗能展示准确的业务动作名称。
    private fun showMoveError(message: String) {
        _state.update { currentState ->
            currentState.copy(
                moveDialog = currentState.moveDialog.copy(
                    errorMessage = message,
                    isLoading = false,
                    isSubmitting = false
                )
            )
        }
    }

    // [语法] 这是 Throwable 的扩展函数，相当于 Java 静态工具方法 MoveErrors.toMessage(throwable)。
    // [设计] 为什么这样写：移动失败时保留底层异常细节，便于开发阶段定位数据库或校验问题。
    private fun Throwable.toMoveMessage(): String {
        val detail = message
        return if (detail == null || detail.isBlank()) {
            "移动失败，请重试"
        } else {
            "移动失败：$detail"
        }
    }

    // [语法] 这是 UploadSizeCheckResult 的扩展函数，相当于 Java 静态工具方法 UploadErrors.toMessage(result)。
    // [设计] 为什么这样写：上传大小错误文案集中转换，后续 UI 改成 Snackbar 时可以直接复用这套业务文案。
    private fun UploadSizeCheckResult.toUploadErrorMessage(): String {
        return when (this) {
            UploadSizeCheckResult.Allowed -> "文件可以上传"
            UploadSizeCheckResult.UnknownSize -> "无法读取文件大小，请换一个文件重试"
            is UploadSizeCheckResult.TooLarge -> "文件超过 100MB，无法上传"
        }
    }

    // [语法] 这是 String? 的扩展函数，相当于 Java 静态工具方法 UploadErrors.failedMessage(message)。
    // [设计] 为什么这样写：底层失败可能没有 message，统一兜底能避免页面显示空错误。
    private fun String?.toUploadFailedMessage(): String {
        return if (isNullOrBlank()) {
            "上传失败，请重试"
        } else {
            "上传失败：$this"
        }
    }

    // [语法] 这是 Throwable 的扩展函数，相当于 Java 静态工具方法 UploadErrors.toMessage(throwable)。
    // [设计] 为什么这样写：异常转上传文案和列表加载文案分开，避免把“文件列表加载失败”误显示在上传场景。
    private fun Throwable.toUploadMessage(): String {
        return message.toUploadFailedMessage()
    }

    private fun loadFiles(
        folderId: String?,
        folderName: String,
        folderStack: List<FolderCrumb>,
        shouldInitializeMock: Boolean
    ) {
        // [语法] ?. 是 Kotlin 安全调用，相当于 Java 里先判断 loadJob != null 再调用 cancel，避免空指针。
        // [设计] 为什么这样写：用户重复点击重试时先取消旧收集任务，避免多个 Flow 同时更新同一份 State。
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(
                    currentFolderId = folderId,
                    currentFolderName = folderName,
                    folderStack = folderStack,
                    isLoading = true,
                    errorMessage = null
                )
            }

            try {
                val insertedMock = if (shouldInitializeMock) {
                    fileRepository.initializeFromMockIfNeeded()
                } else {
                    false
                }
                // [语法] collect 是 Flow 的收集函数，类似订阅 Observable；Room 表变化时这里会收到新列表。
                // [设计] 为什么这样写：当前目录 ID 是观察 Room 的唯一输入，进入文件夹只切换 parentId，不让 UI 直接碰 DAO 查询。
                fileRepository.observeFiles(parentId = folderId).collect { files ->
                    currentFolderFiles = files
                    _state.update { currentState ->
                        val nextFiles = currentFolderFiles.toVisibleFiles(
                            filter = currentState.filter,
                            sortType = currentState.sortType
                        )
                        currentState.copy(
                            files = nextFiles,
                            selectedFileIds = currentState.selectedFileIds.keepOnlyVisible(nextFiles),
                            isLoading = false,
                            errorMessage = null,
                            initializedFromMock = currentState.initializedFromMock || insertedMock
                        )
                    }
                }
            } catch (throwable: Throwable) {
                _state.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        errorMessage = throwable.toUserMessage()
                    )
                }
            }
        }
    }

    // [语法] 这是 Set<String> 的扩展函数，相当于 Java 静态工具方法 SelectionFilters.keepOnlyVisible(selectedIds, files)。
    // [设计] 为什么这样写：筛选、排序或 Room 刷新后，选中集合只保留当前可见文件，避免后续操作误作用到用户看不见的文件。
    private fun Set<String>.keepOnlyVisible(visibleFiles: List<CloudFile>): Set<String> {
        val visibleFileIds = visibleFiles.map { file -> file.fileId }.toSet()
        return intersect(visibleFileIds)
    }

    // [语法] 这是 CloudFile 的扩展函数，相当于 Java 静态工具方法 RenameNames.toEditableRenameName(file)。
    // [设计] 为什么这样写：弹窗只允许编辑基础名，扩展名由 preservedExtension 保留，避免用户把 .txt/.mp4 等后缀改丢。
    private fun CloudFile.toEditableRenameName(): String {
        val extension = toPreservedExtension()
        return if (extension.isEmpty()) {
            name
        } else {
            name.removeSuffix(extension)
        }
    }

    // [语法] 这是 CloudFile 的扩展函数；lastIndexOf 返回最后一个点号位置，和 Java String.lastIndexOf 类似。
    // [设计] 为什么这样写：只有普通文件保留最后一段扩展名，文件夹和无扩展名文件不强行制造后缀。
    private fun CloudFile.toPreservedExtension(): String {
        if (type == FileType.Folder) {
            return ""
        }
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0 && dotIndex < name.lastIndex) {
            name.substring(dotIndex)
        } else {
            ""
        }
    }

    // [语法] 这是 List<CloudFile> 的扩展函数，相当于 Java 静态工具方法 FileListDeriver.toVisibleFiles(files, filter, sortType)。
    // [设计] 为什么这样写：筛选和排序都是展示派生规则，统一在 ViewModel 里生成可见列表，UI 只渲染最终 State.files。
    private fun List<CloudFile>.toVisibleFiles(
        filter: FileFilter,
        sortType: FileSortType
    ): List<CloudFile> {
        return applyFilter(filter).applySort(sortType)
    }

    // [语法] 这是 List<CloudFile> 的扩展函数，相当于 Java 静态工具方法 FileFilters.applyFilter(files, filter)。
    // [设计] 为什么这样写：筛选规则放在 ViewModel 层，UI 只展示 State.files，不自己判断哪些文件该出现。
    private fun List<CloudFile>.applyFilter(filter: FileFilter): List<CloudFile> {
        return when (filter) {
            FileFilter.All -> this
            FileFilter.Image -> filter { file -> file.type == FileType.Image }
            FileFilter.Video -> filter { file -> file.type == FileType.Video }
            FileFilter.Document -> filter { file -> file.type == FileType.Txt }
        }
    }

    // [语法] sortedWith + compareByDescending/thenBy 类似 Java Comparator 链式比较器，用多个字段依次决定顺序。
    // [设计] 为什么这样写：综合排序是文件列表的默认产品规则，放在 ViewModel 可以保证筛选后、进入目录后都使用同一套稳定排序。
    private fun List<CloudFile>.applySort(sortType: FileSortType): List<CloudFile> {
        return when (sortType) {
            FileSortType.Comprehensive -> sortedWith(
                compareByDescending<CloudFile> { file -> file.type == FileType.Folder }
                    .thenByDescending { file -> file.isPinned }
                    .thenByDescending { file -> file.updatedAt }
                    .thenBy { file -> file.name.lowercase(Locale.ROOT) }
            )
        }
    }

    // [语法] 这是扩展函数，相当于 Java 静态工具方法 FileListViewModel.toUserMessage(Throwable)。
    // [设计] 为什么这样写：异常转 UI 文案集中处理，避免不同 catch 分支写出不一致的错误提示。
    private fun Throwable.toUserMessage(): String {
        val detail = message
        return if (detail == null || detail.isBlank()) {
            "文件列表加载失败，请重试"
        } else {
            "文件列表加载失败：$detail"
        }
    }
}
