package com.example.simple_pan.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simple_pan.domain.repository.FileRepository
import com.example.simple_pan.domain.repository.RecentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// [语法] @HiltViewModel 告诉 Hilt 创建这个 ViewModel，类似 Java DI 框架创建 Controller/ViewModel。
// [设计] 为什么这样写：首页只依赖 domain 层 Repository 接口，不直接碰 Room DAO，保证 UI 层和数据层边界清楚。
@HiltViewModel
class PanHomeViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val recentRepository: RecentRepository
) : ViewModel() {
    // [语法] MutableStateFlow 类似 Java Observable 加当前值缓存；asStateFlow 暴露只读版本，避免外部直接改状态。
    // [设计] 为什么这样写：所有首页状态变化都经过 ViewModel，Composable 只观察 State，符合 MVI 的单向数据流。
    private val _state = MutableStateFlow(PanHomeState())
    val state: StateFlow<PanHomeState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadHome()
    }

    // [设计] 为什么这样写：UI 只发送 Intent，ViewModel 决定如何处理，后续新增动作时入口仍然统一。
    fun onIntent(intent: PanHomeIntent) {
        when (intent) {
            PanHomeIntent.Retry -> loadHome()
        }
    }

    private fun loadHome() {
        // [语法] ?. 是 Kotlin 安全调用，相当于 Java 先判断 loadJob != null 再 cancel，避免空指针。
        // [设计] 为什么这样写：重复重试时取消旧任务，避免多个 Flow 同时更新首页 State。
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(isLoading = true, errorMessage = null)
            }

            try {
                fileRepository.initializeFromMockIfNeeded()

                // [语法] combine 会把多个 Flow 的最新值合并，类似 Java Observable.combineLatest。
                // [设计] 为什么这样写：首页同时依赖根目录文件、最近浏览和最近转存，用 combine 能让任意一类数据变化都刷新同一个 State。
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

    // [语法] 这是扩展函数，相当于 Java 静态工具方法 PanHomeViewModel.toUserMessage(Throwable)。
    // [设计] 为什么这样写：异常到 UI 文案的转换集中处理，避免不同 catch 分支出现不一致提示。
    private fun Throwable.toUserMessage(): String {
        val detail = message
        return if (detail == null || detail.isBlank()) {
            "首页数据加载失败，请重试"
        } else {
            "首页数据加载失败：$detail"
        }
    }

    // [语法] companion object 相当于 Java static 常量区域。
    // [设计] 为什么这样写：首页最近记录数量集中定义，UI 和 Repository 调用都不会散落魔法数字。
    companion object {
        private const val RECENT_LIMIT = 3
    }
}
