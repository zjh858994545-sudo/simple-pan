package com.example.simple_pan.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.OpenFileResult
import com.example.simple_pan.domain.repository.FileRepository
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

// [语法] @HiltViewModel 表示这个 ViewModel 由 Hilt 创建，构造参数由依赖注入提供。
// [设计] 为什么这样写：搜索页依赖 domain 层 Repository/UseCase，不直接认识 Room、DAO 或 Android Intent，保持 UI 层边界清楚。
@HiltViewModel
class PanSearchViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val openFileUseCase: OpenFileUseCase,
    private val recordOpenUseCase: RecordOpenUseCase
) : ViewModel() {
    // [语法] MutableStateFlow 保存当前状态；公开 asStateFlow 后 UI 只能观察，不能直接修改。
    // [设计] 为什么这样写：输入框、加载、结果列表都由同一个 State 驱动，Compose 重组时不会丢状态。
    private val _state = MutableStateFlow(PanSearchState())
    val state: StateFlow<PanSearchState> = _state.asStateFlow()

    // [语法] SharedFlow 不保存“当前值”，适合 Snackbar 或导航这种一次性事件。
    // [设计] 为什么这样写：搜索结果点击后的打开动作不能写进 State，否则页面重组可能重复打开文件。
    private val _effect = MutableSharedFlow<PanSearchEffect>(extraBufferCapacity = 1)
    val effect: SharedFlow<PanSearchEffect> = _effect.asSharedFlow()

    private var searchJob: Job? = null

    fun onIntent(intent: PanSearchIntent) {
        when (intent) {
            is PanSearchIntent.ChangeKeyword -> {
                changeKeyword(intent.keyword)
            }
            PanSearchIntent.SubmitSearch -> {
                submitSearch(_state.value.keyword)
            }
            PanSearchIntent.Retry -> {
                submitSearch(_state.value.submittedKeyword)
            }
            is PanSearchIntent.OpenFile -> {
                openFile(intent.fileId)
            }
            is PanSearchIntent.RecordOpenedFile -> {
                recordOpenedFile(intent.fileId)
            }
        }
    }

    private fun changeKeyword(keyword: String) {
        _state.update { currentState ->
            val shouldClearResults = keyword.isBlank()
            currentState.copy(
                keyword = keyword,
                results = if (shouldClearResults) emptyList() else currentState.results,
                hasSearched = if (shouldClearResults) false else currentState.hasSearched,
                errorMessage = null
            )
        }
        if (keyword.isBlank()) {
            searchJob?.cancel()
        }
    }

    private fun submitSearch(rawKeyword: String) {
        val keyword = rawKeyword.trim()
        if (keyword.isBlank()) {
            searchJob?.cancel()
            _state.update { currentState ->
                currentState.copy(
                    keyword = rawKeyword,
                    submittedKeyword = "",
                    results = emptyList(),
                    isLoading = false,
                    hasSearched = false,
                    errorMessage = null
                )
            }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(
                    keyword = rawKeyword,
                    submittedKeyword = keyword,
                    results = emptyList(),
                    isLoading = true,
                    hasSearched = true,
                    errorMessage = null
                )
            }

            try {
                // [语法] collect 是 Flow 的订阅动作；Room 数据变化后，搜索结果会继续推送新列表。
                // [设计] 为什么这样写：搜索提交后保持观察同一个关键词，后续上传、删除、重命名会自动刷新结果。
                fileRepository.observeSearchResults(keyword).collect { results ->
                    _state.update { currentState ->
                        currentState.copy(
                            results = results,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
            } catch (throwable: Throwable) {
                _state.update { currentState ->
                    currentState.copy(
                        results = emptyList(),
                        isLoading = false,
                        errorMessage = throwable.toSearchMessage()
                    )
                }
            }
        }
    }

    // [设计] 为什么这样写：搜索结果点击文件时复用阶段 5 的打开用例，搜索页不用重复判断 TXT/视频/异常规则。
    private fun openFile(fileId: String) {
        viewModelScope.launch {
            try {
                when (val result = openFileUseCase(fileId)) {
                    is OpenFileResult.ReadyForTxtReader -> {
                        _effect.emit(
                            PanSearchEffect.OpenTxtReader(
                                fileId = result.fileId,
                                fileName = result.fileName
                            )
                        )
                    }
                    is OpenFileResult.ReadyForVideoPlayer -> {
                        _effect.emit(
                            PanSearchEffect.OpenVideoPlayer(
                                fileId = result.fileId,
                                fileName = result.fileName,
                                localPath = result.localPath,
                                mimeType = result.mimeType
                            )
                        )
                    }
                    OpenFileResult.FileNotFound -> {
                        showMessage("文件不存在或已被删除")
                    }
                    OpenFileResult.LocalPathMissing -> {
                        showMessage("文件还没有本地内容，请先上传真实文件")
                    }
                    OpenFileResult.LocalFileMissing -> {
                        showMessage("本地文件不存在，请重新上传")
                    }
                    is OpenFileResult.UnsupportedType -> {
                        showMessage("${result.fileType.toOpenTypeName()} 暂不支持打开")
                    }
                }
            } catch (throwable: Throwable) {
                showMessage(throwable.toOpenFileMessage())
            }
        }
    }

    // [设计] 为什么这样写：视频是否真正启动播放器只有 Screen 层知道，成功后再回传记录最近浏览，避免失败打开污染首页历史。
    private fun recordOpenedFile(fileId: String) {
        viewModelScope.launch {
            try {
                recordOpenUseCase(fileId)
            } catch (throwable: Throwable) {
                showMessage(throwable.toRecordOpenMessage())
            }
        }
    }

    private suspend fun showMessage(message: String) {
        _effect.emit(PanSearchEffect.ShowMessage(message))
    }

    // [语法] 这是 FileType 的扩展函数，相当于 Java 静态工具方法 FileTypeDisplay.toOpenTypeName(fileType)。
    // [设计] 为什么这样写：错误提示需要面向用户的类型名称，集中转换能避免多个错误分支重复写 when。
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

    // [语法] 这是 Throwable 的扩展函数，相当于 Java 静态工具方法 SearchErrors.toMessage(throwable)。
    // [设计] 为什么这样写：搜索失败统一转成用户可读文案，避免直接把数据库异常栈暴露在页面上。
    private fun Throwable.toSearchMessage(): String {
        val detail = message
        return if (detail.isNullOrBlank()) {
            "搜索失败，请重试"
        } else {
            "搜索失败：$detail"
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
}
