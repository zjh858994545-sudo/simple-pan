package com.example.simple_pan.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simple_pan.domain.model.SaveShareResult
import com.example.simple_pan.domain.repository.ShareRepository
import com.example.simple_pan.domain.usecase.SaveShareToPanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// [语法] @HiltViewModel 告诉 Hilt 这个 ViewModel 由依赖注入创建，类似 Java 项目中 DI 容器创建 Controller/ViewModel。
// [设计] 为什么这样写：分享预览页只依赖 domain 层 ShareRepository，不直接认识 Room 的 share_entity 和 snapshot 表结构。
@HiltViewModel
class SharePreviewViewModel @Inject constructor(
    private val shareRepository: ShareRepository,
    private val saveShareToPanUseCase: SaveShareToPanUseCase
) : ViewModel() {
    // [语法] MutableStateFlow 类似 Java Observable 加当前值缓存；asStateFlow 暴露只读版本，避免 UI 直接改状态。
    // [设计] 为什么这样写：分享内容可能随 Room 变化刷新，StateFlow 能让 Compose 自动重组并保持单向数据流。
    private val _state = MutableStateFlow(SharePreviewState())
    val state: StateFlow<SharePreviewState> = _state.asStateFlow()

    private var loadJob: Job? = null

    // [设计] 为什么这样写：Composable 只发送 Intent，ViewModel 统一决定加载、重试和异常处理，便于后续加入保存到网盘动作。
    fun onIntent(intent: SharePreviewIntent) {
        when (intent) {
            is SharePreviewIntent.Load -> loadShare(intent.token)
            SharePreviewIntent.Retry -> loadShare(_state.value.token)
            SharePreviewIntent.SaveToPan -> saveCurrentShareToPan()
        }
    }

    // [设计] 为什么这样写：每次切换 token 前取消旧观察任务，避免多个 Flow 同时向同一个 State 写入不同分享的数据。
    private fun loadShare(token: String) {
        val normalizedToken = token.trim()
        loadJob?.cancel()

        if (normalizedToken.isBlank()) {
            _state.value = SharePreviewState(
                token = normalizedToken,
                isLoading = false,
                errorMessage = "分享链接缺少 token"
            )
            return
        }

        loadJob = viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(
                    token = normalizedToken,
                    isLoading = true,
                    isSaving = false,
                    isNotFound = false,
                    saveMessage = null,
                    saveErrorMessage = null,
                    errorMessage = null
                )
            }

            try {
                // [语法] collect 是 Flow 的收集函数，类似订阅 Observable；Room 中分享记录变化时这里会重新收到数据。
                // [设计] 为什么这样写：分享预览页需要跟随本地快照刷新，不手动缓存列表，也不直接从 UI 触发 DAO 查询。
                shareRepository.observeShareBundle(normalizedToken).collect { shareBundle ->
                    if (shareBundle == null) {
                        _state.update { currentState ->
                            currentState.copy(
                                title = "",
                                files = emptyList(),
                                isLoading = false,
                                isNotFound = true,
                                errorMessage = null
                            )
                        }
                    } else {
                        _state.update { currentState ->
                            currentState.copy(
                                title = shareBundle.title,
                                shareType = shareBundle.shareType,
                                files = shareBundle.snapshotFiles,
                                isLoading = false,
                                isNotFound = false,
                                errorMessage = null
                            )
                        }
                    }
                }
            } catch (throwable: Throwable) {
                _state.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        isNotFound = false,
                        errorMessage = throwable.toUserMessage()
                    )
                }
            }
        }
    }

    // [设计] 为什么这样写：保存分享从当前 token 重新读取快照并写入网盘，避免页面状态过期时保存到旧数据。
    private fun saveCurrentShareToPan() {
        val currentState = _state.value
        if (currentState.isSaving) {
            return
        }
        if (currentState.token.isBlank()) {
            _state.update { state ->
                state.copy(saveErrorMessage = "分享链接缺少 token")
            }
            return
        }

        _state.update { state ->
            state.copy(
                isSaving = true,
                saveMessage = null,
                saveErrorMessage = null
            )
        }

        viewModelScope.launch {
            when (val result = saveShareToPanUseCase(currentState.token)) {
                is SaveShareResult.Saved -> {
                    _state.update { state ->
                        state.copy(
                            isSaving = false,
                            saveMessage = "已保存 ${result.files.size} 项到网盘",
                            saveErrorMessage = null
                        )
                    }
                }
                SaveShareResult.MissingToken -> {
                    showSaveError("分享链接缺少 token")
                }
                SaveShareResult.ShareNotFound -> {
                    _state.update { state ->
                        state.copy(
                            isSaving = false,
                            isNotFound = true,
                            saveMessage = null,
                            saveErrorMessage = null
                        )
                    }
                }
                SaveShareResult.EmptySnapshot -> {
                    showSaveError("分享内容为空，无法保存")
                }
                is SaveShareResult.Failed -> {
                    showSaveError(result.message.toSaveFailedMessage())
                }
            }
        }
    }

    // [设计] 为什么这样写：保存失败只影响按钮下方提示，不替换整个分享预览列表，用户可以检查内容后再次保存。
    private fun showSaveError(message: String) {
        _state.update { state ->
            state.copy(
                isSaving = false,
                saveMessage = null,
                saveErrorMessage = message
            )
        }
    }

    // [语法] 这是 String? 的扩展函数，相当于 Java 静态工具方法 ShareSaveErrors.failedMessage(message)。
    // [设计] 为什么这样写：保存失败可能没有底层 message，统一兜底能避免页面显示空白错误。
    private fun String?.toSaveFailedMessage(): String {
        return if (isNullOrBlank()) {
            "保存失败，请重试"
        } else {
            "保存失败：$this"
        }
    }

    // [语法] 这是 Throwable 的扩展函数，相当于 Java 静态工具方法 SharePreviewErrors.toMessage(throwable)。
    // [设计] 为什么这样写：分享预览页的错误文案独立于文件页，用户能从提示直接知道失败发生在分享读取链路。
    private fun Throwable.toUserMessage(): String {
        val detail = message
        return if (detail == null || detail.isBlank()) {
            "分享内容加载失败，请重试"
        } else {
            "分享内容加载失败：$detail"
        }
    }
}
