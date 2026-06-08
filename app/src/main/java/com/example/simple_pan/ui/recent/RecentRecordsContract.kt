package com.example.simple_pan.ui.recent

import com.example.simple_pan.domain.model.RecentRecord

// [语法] enum class 和 Java enum 类似，用固定常量表达有限页面类型。
// [设计] 为什么这样写：最近转存和最近浏览共用一套列表 UI，但数据来源不同，用类型参数区分。
enum class RecentRecordsType {
    Transfer,
    Open
}

// [语法] data class 相当于 Java 的 POJO/Bean，Kotlin 会自动生成 copy/equals 等方法。
// [设计] 为什么这样写：最近记录页只需要类型、列表、加载和错误四类状态，集中成 State 后 UI 可以完全按状态渲染。
data class RecentRecordsState(
    val type: RecentRecordsType = RecentRecordsType.Transfer,
    val records: List<RecentRecord> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

// [语法] sealed interface 表示有限事件集合，when 处理时编译器能检查分支是否遗漏。
// [设计] 为什么这样写：页面加载、重试、打开记录都通过 Intent 进入 ViewModel，保持和首页/文件页一致的 MVI 数据流。
sealed interface RecentRecordsIntent {
    data class Load(val type: RecentRecordsType) : RecentRecordsIntent

    data object Retry : RecentRecordsIntent

    data class OpenFile(val fileId: String) : RecentRecordsIntent

    data class RecordOpenedFile(val fileId: String) : RecentRecordsIntent
}

// [设计] 为什么这样写：Snackbar、阅读器导航、系统播放器都是一次性动作，不应该存进 RecentRecordsState。
sealed interface RecentRecordsEffect {
    data class ShowMessage(val message: String) : RecentRecordsEffect

    data class OpenTxtReader(val fileId: String, val fileName: String) : RecentRecordsEffect

    data class OpenVideoPlayer(
        val fileId: String,
        val fileName: String,
        val localPath: String,
        val mimeType: String
    ) : RecentRecordsEffect
}
