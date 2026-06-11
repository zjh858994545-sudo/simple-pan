package com.example.simple_pan.ui.reader

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.ReadTxtFileResult
import com.example.simple_pan.domain.usecase.ReadTxtFileUseCase
import com.example.simple_pan.domain.usecase.RecordOpenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TXT_READER_PERF_TAG = "TxtReaderPerf"

// [语法] @HiltViewModel 表示这个 ViewModel 由 Hilt 创建并注入依赖，类似 Java 项目里由 DI 容器创建 Controller。
// [设计] 为什么这样写：阅读器的文件读取由 domain UseCase 处理，ViewModel 只负责把读取结果、分页结果和用户操作转成稳定 UI State。
@HiltViewModel
class TxtReaderViewModel @Inject constructor(
    private val readTxtFileUseCase: ReadTxtFileUseCase,
    private val recordOpenUseCase: RecordOpenUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(TxtReaderState())
    val state: StateFlow<TxtReaderState> = _state.asStateFlow()

    fun onIntent(intent: TxtReaderIntent) {
        when (intent) {
            is TxtReaderIntent.LoadFile -> loadFile(
                fileId = intent.fileId,
                fallbackFileName = intent.fallbackFileName
            )
            TxtReaderIntent.PreviousPage -> goToPreviousPage()
            TxtReaderIntent.NextPage -> goToNextPage()
            is TxtReaderIntent.JumpToPage -> jumpToPage(intent.pageIndex)
            is TxtReaderIntent.ChangeFontSize -> changeFontSize(intent.deltaSp)
            is TxtReaderIntent.ApplyMeasuredPages -> applyMeasuredPages(
                generation = intent.generation,
                pages = intent.pages
            )
        }
    }

    // [设计] 为什么这样写：每次进入阅读器都从 UseCase 重新读取，能处理文件被删除、重命名或本地路径丢失后的真实状态。
    private fun loadFile(fileId: String, fallbackFileName: String) {
        if (fileId.isBlank()) {
            _state.update { currentState ->
                currentState.copy(
                    fileId = fileId,
                    fileName = fallbackFileName,
                    isLoading = false,
                    isPaginating = false,
                    errorMessage = "缺少 TXT 文件参数"
                )
            }
            return
        }

        val currentFontSize = _state.value.fontSizeSp
        _state.update { currentState ->
            TxtReaderState(
                fileId = fileId,
                fileName = fallbackFileName,
                fontSizeSp = currentFontSize,
                isLoading = true,
                isPaginating = false,
                errorMessage = null,
                paginationGeneration = currentState.paginationGeneration + 1
            )
        }

        viewModelScope.launch {
            val readStartMs = SystemClock.elapsedRealtime()
            try {
                val result = readTxtFileUseCase(fileId)
                val readCostMs = SystemClock.elapsedRealtime() - readStartMs
                if (result is ReadTxtFileResult.Loaded) {
                    Timber.tag(TXT_READER_PERF_TAG).d(
                        "read success fileId=%s chars=%d costMs=%d",
                        result.fileId,
                        result.content.length,
                        readCostMs
                    )
                } else {
                    Timber.tag(TXT_READER_PERF_TAG).w(
                        "read failed fileId=%s result=%s costMs=%d",
                        fileId,
                        result.javaClass.simpleName,
                        readCostMs
                    )
                }

                when (result) {
                    is ReadTxtFileResult.Loaded -> {
                        _state.update { currentState ->
                            currentState.copy(
                                fileId = result.fileId,
                                fileName = result.fileName,
                                content = result.content,
                                pages = emptyList(),
                                currentPageIndex = 0,
                                isLoading = false,
                                isPaginating = result.content.isNotEmpty(),
                                errorMessage = null,
                                pendingAnchorIndex = 0,
                                paginationGeneration = currentState.paginationGeneration + 1
                            )
                        }
                        recordOpenSafely(result.fileId)
                    }
                    ReadTxtFileResult.FileNotFound -> showReadError("文件不存在或已被删除")
                    ReadTxtFileResult.LocalPathMissing -> showReadError("文件还没有本地内容，请先上传真实 TXT 文件")
                    ReadTxtFileResult.LocalFileMissing -> showReadError("本地 TXT 文件不存在，请重新上传")
                    is ReadTxtFileResult.UnsupportedType -> {
                        showReadError("${result.fileType.toReaderTypeName()}暂不支持用 TXT 阅读器打开")
                    }
                    ReadTxtFileResult.DecodeFailed -> showReadError("文本编码不支持，请换成 UTF-8 或 GB18030 编码")
                    is ReadTxtFileResult.Failed -> showReadError(result.message.toReadFailedMessage())
                }
            } catch (throwable: Throwable) {
                Timber.tag(TXT_READER_PERF_TAG).e(
                    throwable,
                    "read exception fileId=%s costMs=%d",
                    fileId,
                    SystemClock.elapsedRealtime() - readStartMs
                )
                showReadError(throwable.message.toReadFailedMessage())
            }
        }
    }

    // [设计] 为什么这样写：最近浏览属于首页联动能力，不能因为历史写入偶发失败而打断用户阅读。
    private suspend fun recordOpenSafely(fileId: String) {
        try {
            recordOpenUseCase(fileId)
        } catch (throwable: Throwable) {
            // 阅读正文已经成功展示，浏览历史失败只影响首页联动，不覆盖正文状态。
        }
    }

    private fun showReadError(message: String) {
        _state.update { currentState ->
            currentState.copy(
                content = "",
                pages = emptyList(),
                currentPageIndex = 0,
                isLoading = false,
                isPaginating = false,
                errorMessage = message,
                pendingAnchorIndex = 0,
                paginationGeneration = currentState.paginationGeneration + 1
            )
        }
    }

    private fun goToPreviousPage() {
        _state.update { currentState ->
            if (currentState.canGoPrevious) {
                val nextIndex = currentState.currentPageIndex - 1
                currentState.copy(
                    currentPageIndex = nextIndex,
                    pendingAnchorIndex = currentState.pages.getOrNull(nextIndex)?.startIndex ?: 0
                )
            } else {
                currentState
            }
        }
    }

    private fun goToNextPage() {
        _state.update { currentState ->
            if (currentState.canGoNext) {
                val nextIndex = currentState.currentPageIndex + 1
                currentState.copy(
                    currentPageIndex = nextIndex,
                    pendingAnchorIndex = currentState.pages.getOrNull(nextIndex)?.startIndex ?: 0
                )
            } else {
                currentState
            }
        }
    }

    private fun jumpToPage(pageIndex: Int) {
        _state.update { currentState ->
            if (currentState.pages.isEmpty()) {
                currentState
            } else {
                val nextIndex = pageIndex.coerceIn(0, currentState.pages.lastIndex)
                currentState.copy(
                    currentPageIndex = nextIndex,
                    pendingAnchorIndex = currentState.pages[nextIndex].startIndex
                )
            }
        }
    }

    // [设计] 为什么这样写：字号变化会让测量分页结果失效，所以这里清空旧页并递增 generation，等待 Screen 按新字号重新测量。
    private fun changeFontSize(deltaSp: Int) {
        if (deltaSp == 0) {
            return
        }
        _state.update { currentState ->
            val nextFontSize = (currentState.fontSizeSp + deltaSp)
                .coerceIn(TXT_READER_MIN_FONT_SIZE_SP, TXT_READER_MAX_FONT_SIZE_SP)
            if (nextFontSize == currentState.fontSizeSp) {
                currentState
            } else {
                currentState.copy(
                    fontSizeSp = nextFontSize,
                    pages = emptyList(),
                    currentPageIndex = 0,
                    isPaginating = currentState.content.isNotEmpty(),
                    pendingAnchorIndex = currentState.currentPage?.startIndex ?: currentState.pendingAnchorIndex,
                    paginationGeneration = currentState.paginationGeneration + 1
                )
            }
        }
    }

    // [设计] 为什么这样写：测量分页依赖正文区域尺寸和字体样式，只能由 Screen 计算；ViewModel 负责接收结果并恢复到最接近原阅读位置的页面。
    private fun applyMeasuredPages(generation: Int, pages: List<TxtReaderPage>) {
        val applyStartMs = SystemClock.elapsedRealtime()
        var applied = false
        var finalPageCount = pages.size
        var finalCurrentPage = 0
        _state.update { currentState ->
            if (generation != currentState.paginationGeneration) {
                finalPageCount = currentState.pages.size
                finalCurrentPage = currentState.currentPageNumber
                currentState
            } else {
                val boundedPages = pages.filter { page -> page.startIndex < page.endIndex }
                val nextIndex = boundedPages.findPageIndexForAnchor(currentState.pendingAnchorIndex)
                applied = true
                finalPageCount = boundedPages.size
                finalCurrentPage = if (boundedPages.isEmpty()) 0 else nextIndex + 1
                currentState.copy(
                    pages = boundedPages,
                    currentPageIndex = nextIndex,
                    isPaginating = false,
                    pendingAnchorIndex = boundedPages.getOrNull(nextIndex)?.startIndex ?: 0
                )
            }
        }
        Timber.tag(TXT_READER_PERF_TAG).d(
            "apply measured pages generation=%d applied=%s incomingPages=%d finalPages=%d currentPage=%d costMs=%d",
            generation,
            applied,
            pages.size,
            finalPageCount,
            finalCurrentPage,
            SystemClock.elapsedRealtime() - applyStartMs
        )
    }

    private fun List<TxtReaderPage>.findPageIndexForAnchor(anchorIndex: Int): Int {
        if (isEmpty()) {
            return 0
        }
        val foundIndex = indexOfFirst { page ->
            anchorIndex >= page.startIndex && anchorIndex < page.endIndex
        }
        return if (foundIndex >= 0) {
            foundIndex
        } else {
            indexOfLast { page -> page.startIndex <= anchorIndex }.coerceAtLeast(0)
        }
    }

    private fun String?.toReadFailedMessage(): String {
        return if (isNullOrBlank()) {
            "读取 TXT 失败，请重试"
        } else {
            "读取 TXT 失败：$this"
        }
    }

    private fun FileType.toReaderTypeName(): String {
        return when (this) {
            FileType.Folder -> "文件夹"
            FileType.Video -> "视频"
            FileType.Txt -> "文档"
            FileType.Image -> "图片"
            FileType.Audio -> "音频"
            FileType.Other -> "其他文件"
        }
    }
}
