package com.example.simple_pan.ui.reader

// [语法] data class 相当于 Java 的 POJO/Bean，适合承载不可变 UI 状态。
// [设计] 为什么这样写：阅读器页用一个 State 同时表达标题、正文、加载和错误，Compose 只根据 State 渲染，不直接读文件。
data class TxtReaderState(
    val fileId: String = "",
    val fileName: String = "",
    val content: String = "",
    // [语法] List<String> 类似 Java 的 List<String>，这里保存已经按固定字数切好的每一页文本。
    // [设计] 为什么这样写：v1 分页先把全文切成稳定页数组，UI 只展示当前页；后续 v2 改成测量分页时仍可沿用 pages/currentPageIndex 这组状态。
    val pages: List<String> = emptyList(),
    val currentPageIndex: Int = 0,
    val isLoading: Boolean = true,
    // [语法] String? 表示可空字符串，相当于 Java 的 @Nullable String；没有错误时用 null。
    // [设计] 为什么这样写：读取失败需要在正文区域展示错误，成功或加载中则保持 null，避免额外布尔状态互相冲突。
    val errorMessage: String? = null
) {
    // [设计] 为什么这样写：当前页文本由 pages 和 currentPageIndex 派生，避免 UI 自己做越界判断。
    val currentPageText: String
        get() = pages.getOrNull(currentPageIndex).orEmpty()

    // [设计] 为什么这样写：页数相关展示集中在 State 内，Composable 只关心能否翻页和当前页码。
    val pageCount: Int
        get() = pages.size

    val currentPageNumber: Int
        get() = if (pages.isEmpty()) 0 else currentPageIndex + 1

    val canGoPrevious: Boolean
        get() = currentPageIndex > 0

    val canGoNext: Boolean
        get() = currentPageIndex < pages.lastIndex
}

// [语法] sealed interface 表示受限事件类型，类似 Java 里固定子类集合的抽象父类型。
// [设计] 为什么这样写：阅读器用户动作统一收敛成 Intent，后续分页、滑动翻页可以继续添加事件，不把逻辑散在 Composable 中。
sealed interface TxtReaderIntent {
    // [语法] data class 用来携带读取目标参数，相当于 Java 里一个只读事件对象。
    // [设计] 为什么这样写：fileId 用于真正读取文件，fallbackFileName 用于读取期间先展示标题，提升页面进入时的稳定感。
    data class LoadFile(
        val fileId: String,
        val fallbackFileName: String
    ) : TxtReaderIntent

    // [语法] data object 是 Kotlin 单例事件，类似 Java enum 里的一个固定动作值。
    // [设计] 为什么这样写：上一页没有额外参数，ViewModel 根据当前页状态自行判断边界，UI 不直接改 currentPageIndex。
    data object PreviousPage : TxtReaderIntent

    // [语法] data object 是 Kotlin 单例事件，适合表达无参数的下一页动作。
    // [设计] 为什么这样写：下一页边界统一放在 ViewModel，后续接入左右滑也能复用同一个 Intent。
    data object NextPage : TxtReaderIntent
}
