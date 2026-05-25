package com.example.simple_pan.ui.file

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simple_pan.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                _state.update { currentState ->
                    currentState.copy(errorMessage = null)
                }
            }
            is FileListIntent.ChangeFilter -> {
                _state.update { currentState ->
                    currentState.copy(filter = intent.filter)
                }
            }
            is FileListIntent.ChangeSort -> {
                _state.update { currentState ->
                    currentState.copy(sortType = intent.sortType)
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
                    _state.update { currentState ->
                        currentState.copy(
                            files = files,
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
