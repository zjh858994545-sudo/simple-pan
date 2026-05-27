package com.example.simple_pan.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.ReadTxtFileResult
import com.example.simple_pan.domain.usecase.ReadTxtFileUseCase
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
    private val readTxtFileUseCase: ReadTxtFileUseCase
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
                        _state.update { currentState ->
                            currentState.copy(
                                fileId = result.fileId,
                                fileName = result.fileName,
                                content = result.content,
                                isLoading = false,
                                errorMessage = null
                            )
                        }
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

    // [设计] 为什么这样写：读取失败只影响正文区域，不退出页面；用户仍能看到标题并通过返回按钮回到文件列表。
    private fun showReadError(message: String) {
        _state.update { currentState ->
            currentState.copy(
                content = "",
                isLoading = false,
                errorMessage = message
            )
        }
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
}
