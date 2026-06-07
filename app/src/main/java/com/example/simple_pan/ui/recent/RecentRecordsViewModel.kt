package com.example.simple_pan.ui.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simple_pan.domain.repository.RecentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// [语法] @HiltViewModel 告诉 Hilt 这个 ViewModel 可以被 Compose 的 hiltViewModel() 创建。
// [设计] 为什么这样写：最近全量页只依赖 RecentRepository 抽象，不直接访问 Room DAO，保持 UI 层和数据层解耦。
@HiltViewModel
class RecentRecordsViewModel @Inject constructor(
    private val recentRepository: RecentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RecentRecordsState())
    val state: StateFlow<RecentRecordsState> = _state.asStateFlow()

    private var loadJob: Job? = null

    // [设计] 为什么这样写：Screen 只发送 Load/Retry 意图，具体观察最近浏览还是最近转存由 ViewModel 决定。
    fun onIntent(intent: RecentRecordsIntent) {
        when (intent) {
            is RecentRecordsIntent.Load -> loadRecords(intent.type)
            RecentRecordsIntent.Retry -> loadRecords(_state.value.type)
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
                // [语法] collect 会持续订阅 Flow；历史表变化后，这里会自动收到新列表。
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

    // [语法] 这是 Throwable 的扩展函数，相当于 Java 静态工具方法 RecentRecordsErrors.toUserMessage(throwable)。
    // [设计] 为什么这样写：异常转 UI 文案集中处理，避免页面直接显示数据库或 Flow 的底层错误。
    private fun Throwable.toUserMessage(): String {
        val detail = message
        return if (detail == null || detail.isBlank()) {
            "最近记录加载失败，请重试"
        } else {
            "最近记录加载失败：$detail"
        }
    }

    companion object {
        private const val RECENT_FULL_LIMIT = 100
    }
}
