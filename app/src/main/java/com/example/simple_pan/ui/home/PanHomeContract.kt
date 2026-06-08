package com.example.simple_pan.ui.home

import com.example.simple_pan.domain.model.RecentRecord

// [语法] data class 相当于 Java 的 POJO/Bean，Kotlin 会自动生成 equals/hashCode/toString/copy。
// [设计] 为什么这样写：首页用单一 State 收拢个人信息、空间信息和最近记录，Compose 只根据 State 重组。
data class PanHomeState(
    val userName: String = "SimplePan 用户",
    val usedBytes: Long = 0L,
    val fileCount: Int = 0,
    val recentOpen: List<RecentRecord> = emptyList(),
    val recentTransfer: List<RecentRecord> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

// [语法] sealed interface 表示有限事件集合，when 处理时编译器能检查分支是否遗漏。
// [设计] 为什么这样写：首页重试、点击最近记录、记录打开成功都通过 Intent 进入 ViewModel，保持单向数据流。
sealed interface PanHomeIntent {
    data object Retry : PanHomeIntent

    // [设计] 为什么这样写：最近转存/最近浏览展示的是历史记录，但点击后仍然应该复用统一的文件打开链路。
    data class OpenRecentFile(val fileId: String) : PanHomeIntent

    // [设计] 为什么这样写：视频是否成功拉起系统播放器只有 Screen 层知道，成功后再通知 ViewModel 写最近浏览。
    data class RecordOpenedFile(val fileId: String) : PanHomeIntent
}

// [设计] 为什么这样写：打开 TXT、拉起视频播放器、显示错误提示都是一次性动作，不应该放进可重组的 PanHomeState。
sealed interface PanHomeEffect {
    data class ShowMessage(val message: String) : PanHomeEffect

    data class OpenTxtReader(val fileId: String, val fileName: String) : PanHomeEffect

    data class OpenVideoPlayer(
        val fileId: String,
        val fileName: String,
        val localPath: String,
        val mimeType: String
    ) : PanHomeEffect
}
