package com.example.simple_pan.data.storage

import android.content.Context
import android.net.Uri
import com.example.simple_pan.di.IoDispatcher
import com.example.simple_pan.domain.model.LocalFileMetadata
import com.example.simple_pan.domain.model.StoredUploadFile
import com.example.simple_pan.domain.model.UploadFileCopyResult
import com.example.simple_pan.domain.model.UploadSizeCheckResult
import com.example.simple_pan.domain.usecase.UploadSizePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

// [设计] 为什么这样写：私有目录复制依赖 Android Context、ContentResolver 和 File API，放在 data/storage 层可以让 domain/usecase 只面对结构化结果。
class PrivateUploadStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val uploadSizePolicy: UploadSizePolicy
) {
    // [语法] suspend fun 表示挂起函数，类似 Java 里返回 Future 的异步方法；withContext 会把文件 IO 切到注入的 IO 调度器。
    // [设计] 为什么这样写：复制 SAF 文件可能读写较久，必须离开主线程；同时复制前后都做大小保护，避免超过 100MB 的文件进入私有目录。
    suspend fun copy(metadata: LocalFileMetadata): UploadFileCopyResult = withContext(ioDispatcher) {
        when (val sizeCheck = uploadSizePolicy.validate(metadata)) {
            UploadSizeCheckResult.Allowed -> copyAllowedFile(metadata)
            UploadSizeCheckResult.UnknownSize,
            is UploadSizeCheckResult.TooLarge -> UploadFileCopyResult.RejectedBySize(sizeCheck)
        }
    }

    // [语法] suspend fun 表示挂起函数，调用方可以在协程中等待删除完成，类似 Java Future。
    // [设计] 为什么这样写：复制成功但数据库写入失败时，需要清理刚复制的私有文件，避免出现 Room 没记录但磁盘残留的半成功状态。
    suspend fun delete(localPath: String): Boolean = withContext(ioDispatcher) {
        val file = File(localPath)
        if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }

    // [设计] 为什么这样写：只有通过大小策略的文件才会进入真正复制流程，把校验分支和 IO 分支拆开后，后续排查失败原因更直观。
    private fun copyAllowedFile(metadata: LocalFileMetadata): UploadFileCopyResult {
        val uploadRoot = resolveUploadRoot()
            ?: return UploadFileCopyResult.StorageUnavailable
        val targetFile = createTargetFile(uploadRoot, metadata.displayName)
        val uri = Uri.parse(metadata.uriString)

        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return UploadFileCopyResult.SourceUnavailable

            // [语法] use 是 Kotlin 作用域函数，类似 Java try-with-resources，会在代码块结束后自动 close。
            val copiedBytes = inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    copyWithSizeLimit(
                        input = input,
                        output = output
                    )
                }
            }

            if (copiedBytes > UploadSizePolicy.MAX_UPLOAD_SIZE_BYTES) {
                deletePartialFile(targetFile)
                UploadFileCopyResult.RejectedBySize(
                    UploadSizeCheckResult.TooLarge(
                        sizeBytes = copiedBytes,
                        maxBytes = UploadSizePolicy.MAX_UPLOAD_SIZE_BYTES
                    )
                )
            } else {
                UploadFileCopyResult.Copied(
                    StoredUploadFile(
                        displayName = metadata.displayName,
                        fileType = metadata.fileType,
                        mimeType = metadata.mimeType,
                        sizeBytes = copiedBytes,
                        localPath = targetFile.absolutePath,
                        originalUri = metadata.uriString
                    )
                )
            }
        } catch (exception: IOException) {
            deletePartialFile(targetFile)
            UploadFileCopyResult.Failed(exception.message)
        } catch (exception: SecurityException) {
            deletePartialFile(targetFile)
            UploadFileCopyResult.SourceUnavailable
        }
    }

    // [设计] 为什么这样写：上传文件统一放到 files/uploads 下，属于 App 私有目录，卸载 App 时会随应用数据一起清理。
    private fun resolveUploadRoot(): File? {
        val uploadRoot = File(context.filesDir, UPLOAD_DIR_NAME)
        if (uploadRoot.exists()) {
            return if (uploadRoot.isDirectory) uploadRoot else null
        }
        return if (uploadRoot.mkdirs()) uploadRoot else null
    }

    // [设计] 为什么这样写：私有目录文件名用 UUID 前缀保证不会覆盖同名上传，同时保留原始文件名方便开发调试。
    private fun createTargetFile(uploadRoot: File, displayName: String): File {
        val safeName = displayName.toSafeFileName()
        return File(uploadRoot, "${UUID.randomUUID()}_$safeName")
    }

    // [设计] 为什么这样写：即使元信息显示小于 100MB，也要在真实读取流时再次计数，防止文件提供者返回的 size 不准。
    private fun copyWithSizeLimit(
        input: InputStream,
        output: OutputStream
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE_BYTES)
        var copiedBytes = 0L

        while (true) {
            val readCount = input.read(buffer)
            if (readCount == END_OF_STREAM) {
                return copiedBytes
            }

            copiedBytes += readCount.toLong()
            if (copiedBytes > UploadSizePolicy.MAX_UPLOAD_SIZE_BYTES) {
                return copiedBytes
            }

            output.write(buffer, 0, readCount)
        }
    }

    // [语法] 扩展函数是在不继承 String 的情况下给 String 增加工具方法，类似 Java 里的静态工具函数 StringUtils.safeFileName(name)。
    // [设计] 为什么这样写：Uri 的展示名可能包含路径分隔符或控制字符，写入私有目录前统一清洗，避免生成非法文件名。
    private fun String.toSafeFileName(): String {
        val safeName = replace(UNSAFE_FILE_NAME_CHARS, "_").trim()
        return safeName.ifBlank { FALLBACK_FILE_NAME }
    }

    // [设计] 为什么这样写：失败或超限后删除半成品文件，避免私有目录留下不可见的垃圾数据。
    private fun deletePartialFile(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }

    // [语法] companion object 相当于 Java 的 static 工具区，适合放常量和可复用的 Regex。
    // [设计] 为什么这样写：上传目录名、缓冲区大小和文件名清洗规则都属于复制策略，集中放在同一个类里便于维护。
    companion object {
        private const val UPLOAD_DIR_NAME = "uploads"
        private const val BUFFER_SIZE_BYTES = 8 * 1024
        private const val END_OF_STREAM = -1
        private const val FALLBACK_FILE_NAME = "upload_file"

        private val UNSAFE_FILE_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    }
}
