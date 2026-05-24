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
        loadRootFiles()
    }

    // [设计] 为什么这样写：Composable 只发送 Intent，不直接调用加载函数，保留 MVI 的“用户行为 -> 状态变化”链路。
    fun onIntent(intent: FileListIntent) {
        when (intent) {
            FileListIntent.Retry -> loadRootFiles()
        }
    }

    private fun loadRootFiles() {
        // [语法] ?. 是 Kotlin 安全调用，相当于 Java 里先判断 loadJob != null 再调用 cancel，避免空指针。
        // [设计] 为什么这样写：用户重复点击重试时先取消旧收集任务，避免多个 Flow 同时更新同一份 State。
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(isLoading = true, errorMessage = null)
            }

            try {
                val insertedMock = fileRepository.initializeFromMockIfNeeded()
                // [语法] collect 是 Flow 的收集函数，类似订阅 Observable；Room 表变化时这里会收到新列表。
                // [设计] 为什么这样写：初始化后持续观察根目录，后续上传/删除/移动写库时列表能自动刷新。
                fileRepository.observeFiles(parentId = null).collect { files ->
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
