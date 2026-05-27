package com.example.simple_pan.domain.usecase

import com.example.simple_pan.di.IoDispatcher
import com.example.simple_pan.domain.model.CloudFile
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.ReadTxtFileResult
import com.example.simple_pan.domain.repository.FileRepository
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

// [设计] 为什么这样写：TXT 阅读器需要查 Room、校验本地文件、读取字节和处理编码，集中到 UseCase 能让 ViewModel 只关心“加载成功/失败状态”。
class ReadTxtFileUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    // [语法] suspend operator fun invoke 让调用方可以写 readTxtFileUseCase(fileId)，类似 Java 里调用单方法服务对象。
    // [设计] 为什么这样写：文件读取是 IO 操作，必须切到注入的 IO Dispatcher，遵守项目“不直接写 Dispatchers.IO”的协作约束。
    suspend operator fun invoke(fileId: String): ReadTxtFileResult = withContext(ioDispatcher) {
        val file = fileRepository.findActiveFile(fileId)
            ?: return@withContext ReadTxtFileResult.FileNotFound

        if (file.type != FileType.Txt) {
            return@withContext ReadTxtFileResult.UnsupportedType(file.type)
        }

        val localPath = file.checkedLocalPath()
            ?: return@withContext ReadTxtFileResult.LocalPathMissing
        val txtFile = File(localPath)
        if (!txtFile.exists() || !txtFile.isFile || !txtFile.canRead()) {
            return@withContext ReadTxtFileResult.LocalFileMissing
        }

        val bytes = try {
            txtFile.readBytes()
        } catch (exception: IOException) {
            return@withContext ReadTxtFileResult.Failed(exception.message)
        } catch (exception: SecurityException) {
            return@withContext ReadTxtFileResult.Failed(exception.message)
        }

        val content = bytes.decodeSupportedText()
            ?: return@withContext ReadTxtFileResult.DecodeFailed
        ReadTxtFileResult.Loaded(
            fileId = file.fileId,
            fileName = file.name,
            content = content
        )
    }

    // [语法] 这是 CloudFile 的扩展函数，相当于 Java 静态工具方法 TxtFileChecks.checkedLocalPath(file)。
    // [设计] 为什么这样写：localPath 的空值和空白字符串都不能读取，集中处理后主流程能保持线性、易读。
    private fun CloudFile.checkedLocalPath(): String? {
        return localPath?.takeIf { path -> path.isNotBlank() }
    }

    // [语法] 这是 ByteArray 的扩展函数，相当于 Java 静态工具方法 TextDecoders.decodeSupportedText(bytes)。
    // [设计] 为什么这样写：严格解码能避免把乱码当成正常内容展示；先试 UTF-8，再试中文常见的 GB18030，仍失败才提示编码不支持。
    private fun ByteArray.decodeSupportedText(): String? {
        for (charset in SUPPORTED_CHARSETS) {
            val decodedText = decodeWithCharsetOrNull(charset)
            if (decodedText != null) {
                return decodedText
            }
        }
        return null
    }

    // [设计] 为什么这样写：每次解码都创建新的 decoder，并设置 REPORT，这样遇到非法字节会失败而不是自动替换成乱码字符。
    private fun ByteArray.decodeWithCharsetOrNull(charset: Charset): String? {
        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(this))
                .toString()
        } catch (exception: CharacterCodingException) {
            null
        }
    }

    // [语法] companion object 相当于 Java 的 static 常量区域。
    // [设计] 为什么这样写：支持编码列表属于读取策略，集中放在 UseCase 内部，后续扩展更多编码时不用影响 UI。
    companion object {
        private val SUPPORTED_CHARSETS = listOf(
            Charsets.UTF_8,
            Charset.forName("GB18030")
        )
    }
}
