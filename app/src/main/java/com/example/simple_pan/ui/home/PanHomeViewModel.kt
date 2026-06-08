package com.example.simple_pan.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.OpenFileResult
import com.example.simple_pan.domain.repository.FileRepository
import com.example.simple_pan.domain.repository.RecentRepository
import com.example.simple_pan.domain.usecase.OpenFileUseCase
import com.example.simple_pan.domain.usecase.RecordOpenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// [语法] @HiltViewModel 告诉 Hilt 创建这个 ViewModel，类似 Java DI 框架创建 Controller/ViewModel。
// [设计] 为什么这样写：首页依赖 domain 层 Repository/UseCase，不直接访问 Room DAO，保持 UI 层和数据层边界清楚。
@HiltViewModel
class PanHomeViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val recentRepository: RecentRepository,
    private val openFileUseCase: OpenFileUseCase,
    private val recordOpenUseCase: RecordOpenUseCase
) : ViewModel() {
    // [语法] MutableStateFlow 类似带当前值缓存的 Observable；asStateFlow 暴露只读版本，避免外部直接改状态。
    private val _state = MutableStateFlow(PanHomeState())
    val state: StateFlow<PanHomeState> = _state.asStateFlow()

    // [设计] 为什么这样写：Snackbar、导航阅读器、拉起视频播放器都是一次性动作，使用 Effect 避免重组重复执行。
    private val _effect = MutableSharedFlow<PanHomeEffect>(extraBufferCapacity = 1)
    val effect: SharedFlow<PanHomeEffect> = _effect.asSharedFlow()

    private var loadJob: Job? = null

    init {
        loadHome()
    }

    // [设计] 为什么这样写：UI 只发送 Intent，ViewModel 决定如何处理，后续新增动作时入口仍然统一。
    fun onIntent(intent: PanHomeIntent) {
        when (intent) {
            PanHomeIntent.Retry -> loadHome()
            is PanHomeIntent.OpenRecentFile -> openRecentFile(intent.fileId)
            is PanHomeIntent.RecordOpenedFile -> recordOpenedFile(intent.fileId)
        }
    }

    private fun loadHome() {
        // [语法] ?. 是 Kotlin 安全调用，等价于 Java 先判断 loadJob != null 再 cancel。
        // [设计] 为什么这样写：重复重试时取消旧任务，避免多个 Flow 同时更新首页 State。
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(isLoading = true, errorMessage = null)
            }

            try {
                fileRepository.initializeFromMockIfNeeded()

                // [语法] combine 会把多个 Flow 的最新值合并，类似 Java Observable.combineLatest。
                // [设计] 为什么这样写：首页同时依赖根目录文件、最近浏览和最近转存，任意一类数据变化都刷新同一个 State。
                combine(
                    fileRepository.observeFiles(parentId = null),
                    recentRepository.observeRecentOpen(RECENT_LIMIT),
                    recentRepository.observeRecentTransfer(RECENT_LIMIT)
                ) { rootFiles, recentOpen, recentTransfer ->
                    PanHomeState(
                        usedBytes = rootFiles.sumOf { file -> file.sizeBytes },
                        fileCount = rootFiles.size,
                        recentOpen = recentOpen,
                        recentTransfer = recentTransfer,
                        isLoading = false,
                        errorMessage = null
                    )
                }.collect { homeState ->
                    _state.value = homeState
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

    // [设计] 为什么这样写：首页最近记录点击复用阶段 5 的打开用例，避免首页自己判断 TXT/视频/异常规则。
    private fun openRecentFile(fileId: String) {
        viewModelScope.launch {
            try {
                when (val result = openFileUseCase(fileId)) {
                    is OpenFileResult.ReadyForTxtReader -> {
                        _effect.emit(
                            PanHomeEffect.OpenTxtReader(
                                fileId = result.fileId,
                                fileName = result.fileName
                            )
                        )
                    }
                    is OpenFileResult.ReadyForVideoPlayer -> {
                        _effect.emit(
                            PanHomeEffect.OpenVideoPlayer(
                                fileId = result.fileId,
                                fileName = result.fileName,
                                localPath = result.localPath,
                                mimeType = result.mimeType
                            )
                        )
                    }
                    OpenFileResult.FileNotFound -> showOpenFileMessage("文件不存在或已被删除")
                    OpenFileResult.LocalPathMissing -> showOpenFileMessage("文件还没有本地内容，请先上传真实文件")
                    OpenFileResult.LocalFileMissing -> showOpenFileMessage("本地文件不存在，请重新上传")
                    is OpenFileResult.UnsupportedType -> {
                        showOpenFileMessage("${result.fileType.toOpenTypeName()} 暂不支持打开")
                    }
                }
            } catch (throwable: Throwable) {
                showOpenFileMessage(throwable.toOpenFileMessage())
            }
        }
    }

    private fun recordOpenedFile(fileId: String) {
        viewModelScope.launch {
            try {
                recordOpenUseCase(fileId)
            } catch (throwable: Throwable) {
                showOpenFileMessage(throwable.toRecordOpenMessage())
            }
        }
    }

    private suspend fun showOpenFileMessage(message: String) {
        _effect.emit(PanHomeEffect.ShowMessage(message))
    }

    private fun FileType.toOpenTypeName(): String {
        return when (this) {
            FileType.Folder -> "文件夹"
            FileType.Video -> "视频"
            FileType.Txt -> "文档"
            FileType.Image -> "图片"
            FileType.Audio -> "音频"
            FileType.Other -> "其他文件"
        }
    }

    private fun Throwable.toUserMessage(): String {
        val detail = message
        return if (detail == null || detail.isBlank()) {
            "首页数据加载失败，请重试"
        } else {
            "首页数据加载失败：$detail"
        }
    }

    private fun Throwable.toOpenFileMessage(): String {
        val detail = message
        return if (detail.isNullOrBlank()) {
            "打开失败，请重试"
        } else {
            "打开失败：$detail"
        }
    }

    private fun Throwable.toRecordOpenMessage(): String {
        val detail = message
        return if (detail.isNullOrBlank()) {
            "最近浏览记录失败"
        } else {
            "最近浏览记录失败：$detail"
        }
    }

    companion object {
        private const val RECENT_LIMIT = 3
    }
}
