package com.example.simple_pan.ui.file

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simple_pan.domain.model.CloudFile
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

// [语法] @HiltViewModel 告诉 Hilt 这个 ViewModel 由依赖注入创建，类似 Java 项目里用 DI 容器创建 Controller/ViewModel。
// [设计] 为什么这样写：ViewModel 依赖 domain 层 FileRepository 接口，不直接认识 Room、DAO 或 mock JSON，保持 UI 层边界干净。
@HiltViewModel
class FileListViewModel @Inject constructor(
    private val fileRepository: FileRepository
) : ViewModel() {
    // [语法] MutableStateFlow 类似 Java Observable + 当前值缓存；私有可变、公开只读是 Kotlin 常见封装方式。
    // [设计] 为什么这样写：UI 只能观察 state，不能直接改 state，所有状态变化都通过 ViewModel 处理。
    private val _state = MutableStateFlow(FileListState())
    val state: StateFlow<FileListState> = _state.asStateFlow()

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
                    currentState.copy(
                        filter = intent.filter,
                        files = currentFolderFiles.toVisibleFiles(
                            filter = intent.filter,
                            sortType = currentState.sortType
                        )
                    )
                }
            }
            is FileListIntent.ChangeSort -> {
                _state.update { currentState ->
                    currentState.copy(
                        sortType = intent.sortType,
                        files = currentFolderFiles.toVisibleFiles(
                            filter = currentState.filter,
                            sortType = intent.sortType
                        )
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
        }
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
                        currentState.copy(
                            files = currentFolderFiles.toVisibleFiles(
                                filter = currentState.filter,
                                sortType = currentState.sortType
                            ),
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
