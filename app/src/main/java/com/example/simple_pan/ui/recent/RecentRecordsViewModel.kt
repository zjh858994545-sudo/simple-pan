package com.example.simple_pan.ui.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.OpenFileResult
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// [语法] @HiltViewModel 告诉 Hilt 这个 ViewModel 可以被 Compose 的 hiltViewModel() 创建。
// [设计] 为什么这样写：最近全量页通过 Repository 观察历史，通过 UseCase 打开文件，不直接访问 Room 或 Android 平台 API。
@HiltViewModel
class RecentRecordsViewModel @Inject constructor(
    private val recentRepository: RecentRepository,
    private val openFileUseCase: OpenFileUseCase,
    private val recordOpenUseCase: RecordOpenUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(RecentRecordsState())
    val state: StateFlow<RecentRecordsState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<RecentRecordsEffect>(extraBufferCapacity = 1)
    val effect: SharedFlow<RecentRecordsEffect> = _effect.asSharedFlow()

    private var loadJob: Job? = null

    // [设计] 为什么这样写：Screen 只发送 Load/Retry/OpenFile 意图，具体观察哪类历史和如何打开文件由 ViewModel 决定。
    fun onIntent(intent: RecentRecordsIntent) {
        when (intent) {
            is RecentRecordsIntent.Load -> loadRecords(intent.type)
            RecentRecordsIntent.Retry -> loadRecords(_state.value.type)
            is RecentRecordsIntent.OpenFile -> openFile(intent.fileId)
            is RecentRecordsIntent.RecordOpenedFile -> recordOpenedFile(intent.fileId)
        }
    }

    private fun loadRecords(type: RecentRecordsType) {
        // [语法] ?. 是 Kotlin 安全调用，相当于 Java 先判断 loadJob 是否为空再调用 cancel。
        // [设计] 为什么这样写：用户从最近转存切到最近浏览时，取消旧 Flow 收集，避免旧数据覆盖新页面。
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(
                    type = type,
                    isLoading = true,
                    errorMessage = null
                )
            }

            try {
                val recordsFlow = when (type) {
                    RecentRecordsType.Transfer -> recentRepository.observeRecentTransfer(RECENT_FULL_LIMIT)
                    RecentRecordsType.Open -> recentRepository.observeRecentOpen(RECENT_FULL_LIMIT)
                }
                // [设计] 为什么这样写：上传、分享保存、打开 TXT/视频后，全量页和首页一样能被 Room Flow 自动刷新。
                recordsFlow.collect { records ->
                    _state.update { currentState ->
                        currentState.copy(
                            records = records,
                            isLoading = false,
                            errorMessage = null
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

    // [设计] 为什么这样写：最近页点击记录复用阶段 5 的打开用例，避免重复实现 TXT/视频/异常规则。
    private fun openFile(fileId: String) {
        viewModelScope.launch {
            try {
                when (val result = openFileUseCase(fileId)) {
                    is OpenFileResult.ReadyForTxtReader -> {
                        _effect.emit(
                            RecentRecordsEffect.OpenTxtReader(
                                fileId = result.fileId,
                                fileName = result.fileName
                            )
                        )
                    }
                    is OpenFileResult.ReadyForVideoPlayer -> {
                        _effect.emit(
                            RecentRecordsEffect.OpenVideoPlayer(
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
        _effect.emit(RecentRecordsEffect.ShowMessage(message))
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
            "最近记录加载失败，请重试"
        } else {
            "最近记录加载失败：$detail"
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
        private const val RECENT_FULL_LIMIT = 100
    }
}
