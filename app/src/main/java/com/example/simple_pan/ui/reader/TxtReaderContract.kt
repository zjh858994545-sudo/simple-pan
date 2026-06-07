package com.example.simple_pan.ui.reader

// [语法] data class 相当于 Java 的 POJO/Bean，适合承载不可变 UI 状态。
// [设计] 为什么这样写：v2 阅读器按真实文本测量结果分页，所以每页不仅保存文本，还保存它在全文中的起止位置，便于字号变化后尽量停留在原阅读位置。
data class TxtReaderPage(
    val text: String,
    val startIndex: Int,
    val endIndex: Int
)

// [语法] data class 的默认参数让页面首次进入时可以直接创建空状态。
// [设计] 为什么这样写：阅读器状态同时承载文件内容、测量分页结果、页码、字号和加载状态；Screen 只根据 State 渲染，不直接读取文件。
data class TxtReaderState(
    val fileId: String = "",
    val fileName: String = "",
    val content: String = "",
    val pages: List<TxtReaderPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val fontSizeSp: Int = TXT_READER_DEFAULT_FONT_SIZE_SP,
    val isLoading: Boolean = true,
    val isPaginating: Boolean = false,
    val errorMessage: String? = null,
    val paginationGeneration: Int = 0,
    val pendingAnchorIndex: Int = 0
) {
    val currentPage: TxtReaderPage?
        get() = pages.getOrNull(currentPageIndex)

    val currentPageText: String
        get() = currentPage?.text.orEmpty()

    val pageCount: Int
        get() = pages.size

    val currentPageNumber: Int
        get() = if (pages.isEmpty()) 0 else currentPageIndex + 1

    val canGoPrevious: Boolean
        get() = currentPageIndex > 0

    val canGoNext: Boolean
        get() = currentPageIndex < pages.lastIndex

    val canDecreaseFontSize: Boolean
        get() = fontSizeSp > TXT_READER_MIN_FONT_SIZE_SP

    val canIncreaseFontSize: Boolean
        get() = fontSizeSp < TXT_READER_MAX_FONT_SIZE_SP

    val readingPercent: Int
        get() = if (pageCount == 0) 0 else ((currentPageNumber * 100f) / pageCount).toInt().coerceIn(0, 100)
}

// [语法] sealed interface 表示受限事件类型，类似 Java 中固定子类型集合的抽象父类型。
// [设计] 为什么这样写：阅读器所有用户动作统一收敛成 Intent，按钮、滑动、字号调整和测量分页都走同一入口，方便排查状态变化。
sealed interface TxtReaderIntent {
    data class LoadFile(
        val fileId: String,
        val fallbackFileName: String
    ) : TxtReaderIntent

    data object PreviousPage : TxtReaderIntent

    data object NextPage : TxtReaderIntent

    data class JumpToPage(val pageIndex: Int) : TxtReaderIntent

    data class ChangeFontSize(val deltaSp: Int) : TxtReaderIntent

    data class ApplyMeasuredPages(
        val generation: Int,
        val pages: List<TxtReaderPage>
    ) : TxtReaderIntent
}

const val TXT_READER_MIN_FONT_SIZE_SP = 15
const val TXT_READER_DEFAULT_FONT_SIZE_SP = 18
const val TXT_READER_MAX_FONT_SIZE_SP = 26
