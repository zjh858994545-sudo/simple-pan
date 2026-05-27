package com.example.simple_pan.ui.reader

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

// [语法] @HiltViewModel 表示这个 ViewModel 由 Hilt 创建并注入依赖，类似 Java 项目里 DI 容器创建 Controller。
// [设计] 为什么这样写：阅读器的文件读取由 domain UseCase 处理，ViewModel 只负责把结果转成 UI State，保持 UI 层和文件系统解耦。
@HiltViewModel
class TxtReaderViewModel @Inject constructor(
    private val readTxtFileUseCase: ReadTxtFileUseCase,
    private val recordOpenUseCase: RecordOpenUseCase
) : ViewModel() {
    // [语法] MutableStateFlow 类似 Java Observable + 当前值缓存；私有可变、公开只读是 Kotlin 常见封装方式。
    // [设计] 为什么这样写：阅读器页面只观察 state，不直接修改 state，所有加载和错误转换都收敛在 ViewModel。
    private val _state = MutableStateFlow(TxtReaderState())
    val state: StateFlow<TxtReaderState> = _state.asStateFlow()

    // [设计] 为什么这样写：Composable 只发送 Intent，ViewModel 决定何时读取和如何展示错误，后续加重试或翻页也能沿用同一入口。
    fun onIntent(intent: TxtReaderIntent) {
        when (intent) {
            is TxtReaderIntent.LoadFile -> {
                loadFile(
                    fileId = intent.fileId,
                    fallbackFileName = intent.fallbackFileName
                )
            }
            TxtReaderIntent.PreviousPage -> {
                goToPreviousPage()
            }
            TxtReaderIntent.NextPage -> {
                goToNextPage()
            }
        }
    }

    // [设计] 为什么这样写：每次进入阅读器都从 UseCase 重新读取，能处理文件被重命名、删除或磁盘文件丢失后的真实状态。
    private fun loadFile(fileId: String, fallbackFileName: String) {
        if (fileId.isBlank()) {
            _state.update { currentState ->
                currentState.copy(
                    fileId = fileId,
                    fileName = fallbackFileName,
                    isLoading = false,
                    errorMessage = "缺少 TXT 文件参数"
                )
            }
            return
        }

        _state.update {
            TxtReaderState(
                fileId = fileId,
                fileName = fallbackFileName,
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                when (val result = readTxtFileUseCase(fileId)) {
                    is ReadTxtFileResult.Loaded -> {
                        val pages = result.content.toFixedLengthPages()
                        _state.update { currentState ->
                            currentState.copy(
                                fileId = result.fileId,
                                fileName = result.fileName,
                                content = result.content,
                                pages = pages,
                                currentPageIndex = 0,
                                isLoading = false,
                                errorMessage = null
                            )
                        }
                        recordOpenSafely(result.fileId)
                    }
                    ReadTxtFileResult.FileNotFound -> {
                        showReadError("文件不存在或已被删除")
                    }
                    ReadTxtFileResult.LocalPathMissing -> {
                        showReadError("文件还没有本地内容，请先上传真实 TXT 文件")
                    }
                    ReadTxtFileResult.LocalFileMissing -> {
                        showReadError("本地 TXT 文件不存在，请重新上传")
                    }
                    is ReadTxtFileResult.UnsupportedType -> {
                        showReadError("${result.fileType.toReaderTypeName()} 暂不支持用 TXT 阅读器打开")
                    }
                    ReadTxtFileResult.DecodeFailed -> {
                        showReadError("文本编码不支持，请换成 UTF-8 或 GB18030 编码")
                    }
                    is ReadTxtFileResult.Failed -> {
                        showReadError(result.message.toReadFailedMessage())
                    }
                }
            } catch (throwable: Throwable) {
                showReadError(throwable.message.toReadFailedMessage())
            }
        }
    }

    // [设计] 为什么这样写：最近浏览是首页联动能力，不能因为历史写入偶发失败而打断用户阅读；读取成功后尽力记录即可。
    private suspend fun recordOpenSafely(fileId: String) {
        try {
            recordOpenUseCase(fileId)
        } catch (throwable: Throwable) {
            // [设计] 为什么这样写：阅读器正文已经成功展示，浏览历史失败只影响首页联动，不覆盖正文为错误页。
        }
    }

    // [设计] 为什么这样写：读取失败只影响正文区域，不退出页面；用户仍能看到标题并通过返回按钮回到文件列表。
    private fun showReadError(message: String) {
        _state.update { currentState ->
            currentState.copy(
                content = "",
                pages = emptyList(),
                currentPageIndex = 0,
                isLoading = false,
                errorMessage = message
            )
        }
    }

    // [设计] 为什么这样写：上一页只移动页码，不重新读取文件；边界判断集中在 ViewModel，按钮、滑动等入口都能复用。
    private fun goToPreviousPage() {
        _state.update { currentState ->
            if (currentState.canGoPrevious) {
                currentState.copy(currentPageIndex = currentState.currentPageIndex - 1)
            } else {
                currentState
            }
        }
    }

    // [设计] 为什么这样写：下一页只依赖当前 State 的页数和页码，避免 UI 根据列表长度自行计算导致边界不一致。
    private fun goToNextPage() {
        _state.update { currentState ->
            if (currentState.canGoNext) {
                currentState.copy(currentPageIndex = currentState.currentPageIndex + 1)
            } else {
                currentState
            }
        }
    }

    // [语法] 这是 String 的扩展函数，相当于 Java 静态工具方法 ReaderPaginator.toFixedLengthPages(content)。
    // [设计] 为什么这样写：阶段 5 明确要求 v1 固定字数分页，先用确定的字符数建立可演示版本；后续 v2 再把这里替换成基于文本测量的分页算法。
    private fun String.toFixedLengthPages(): List<String> {
        if (isEmpty()) {
            return emptyList()
        }

        val pages = mutableListOf<String>()
        var startIndex = 0
        while (startIndex < length) {
            val endIndex = minOf(startIndex + FIXED_PAGE_CHAR_COUNT, length)
            pages += substring(startIndex, endIndex)
            startIndex = endIndex
        }
        return pages
    }

    // [语法] 这是 String? 的扩展函数，相当于 Java 静态工具方法 ReaderErrors.failedMessage(message)。
    // [设计] 为什么这样写：底层异常可能没有 message，统一兜底能避免 UI 显示空错误。
    private fun String?.toReadFailedMessage(): String {
        return if (isNullOrBlank()) {
            "读取 TXT 失败，请重试"
        } else {
            "读取 TXT 失败：$this"
        }
    }

    // [语法] 这是 FileType 的扩展函数，相当于 Java 静态工具方法 FileTypeDisplay.toReaderTypeName(fileType)。
    // [设计] 为什么这样写：错误提示需要面向用户的类型名称，集中转换能避免多个错误分支重复写 when。
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

    // [语法] companion object 相当于 Java 的 static 常量区域。
    // [设计] 为什么这样写：固定字数是 v1 分页策略参数，集中命名方便后续和 v2 测量分页做对比。
    companion object {
        private const val FIXED_PAGE_CHAR_COUNT = 500
    }
}
