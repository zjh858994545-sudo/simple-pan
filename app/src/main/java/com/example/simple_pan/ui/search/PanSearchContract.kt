package com.example.simple_pan.ui.search

import com.example.simple_pan.domain.model.CloudFile

// [语法] data class 相当于 Java 的 POJO/Bean，适合保存搜索页当前要展示的不可变状态。
// [设计] 为什么这样写：搜索页需要同时表达输入框内容、提交过的关键词、结果列表、加载和错误，集中成 State 后 UI 只根据状态渲染。
data class PanSearchState(
    val keyword: String = "",
    val submittedKeyword: String = "",
    val results: List<CloudFile> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null
)

// [语法] sealed interface 表示固定的一组用户意图，when 处理时编译器能检查分支是否遗漏。
// [设计] 为什么这样写：输入、提交、重试、点击结果都收敛到 ViewModel，Composable 不直接查库或调用打开文件用例。
sealed interface PanSearchIntent {
    data class ChangeKeyword(val keyword: String) : PanSearchIntent

    data object SubmitSearch : PanSearchIntent

    data object Retry : PanSearchIntent

    data class OpenFile(val fileId: String) : PanSearchIntent

    data class RecordOpenedFile(val fileId: String) : PanSearchIntent
}

// [语法] sealed interface 适合表达一次性事件；这些事件不应该长期保存在 State 里。
// [设计] 为什么这样写：Snackbar、跳转阅读器、拉起系统播放器都是一次性动作，使用 Effect 能避免重组时重复执行。
sealed interface PanSearchEffect {
    data class ShowMessage(val message: String) : PanSearchEffect

    data class OpenTxtReader(val fileId: String, val fileName: String) : PanSearchEffect

    data class OpenVideoPlayer(
        val fileId: String,
        val fileName: String,
        val localPath: String,
        val mimeType: String
    ) : PanSearchEffect
}
