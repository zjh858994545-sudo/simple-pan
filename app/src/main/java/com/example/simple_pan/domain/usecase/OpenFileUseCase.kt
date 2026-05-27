package com.example.simple_pan.domain.usecase

import com.example.simple_pan.di.IoDispatcher
import com.example.simple_pan.domain.model.CloudFile
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.OpenFileResult
import com.example.simple_pan.domain.repository.FileRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

// [设计] 为什么这样写：文件打开前要统一做“是否存在、类型是否支持、本地文件是否还在”的业务判断，避免文件列表、阅读器和播放器各写一套校验。
class OpenFileUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    // [语法] suspend operator fun invoke 让调用方可以写 openFileUseCase(fileId)，类似 Java 里调用一个单方法服务对象。
    // [设计] 为什么这样写：打开前需要查 Room 和检查磁盘文件，放到 IO 调度器能避免阻塞主线程，也符合项目统一注入 Dispatcher 的约束。
    suspend operator fun invoke(fileId: String): OpenFileResult = withContext(ioDispatcher) {
        val file = fileRepository.findActiveFile(fileId)
            ?: return@withContext OpenFileResult.FileNotFound

        when (file.type) {
            FileType.Txt -> file.toTxtOpenResult()
            FileType.Video -> file.toVideoOpenResult()
            FileType.Folder,
            FileType.Image,
            FileType.Audio,
            FileType.Other -> OpenFileResult.UnsupportedType(file.type)
        }
    }

    // [设计] 为什么这样写：TXT 和视频都需要本地文件存在，先复用同一个 localPath 校验，再返回阅读器专用结果。
    private fun CloudFile.toTxtOpenResult(): OpenFileResult {
        val localPath = checkedLocalPath()
            ?: return OpenFileResult.LocalPathMissing
        if (!localPath.hasReadableFile()) {
            return OpenFileResult.LocalFileMissing
        }
        return OpenFileResult.ReadyForTxtReader(
            fileId = fileId,
            fileName = name,
            localPath = localPath
        )
    }

    // [设计] 为什么这样写：视频打开后续要通过 FileProvider 转 content Uri，当前步骤先保证本地文件路径和 MIME 已准备好。
    private fun CloudFile.toVideoOpenResult(): OpenFileResult {
        val localPath = checkedLocalPath()
            ?: return OpenFileResult.LocalPathMissing
        if (!localPath.hasReadableFile()) {
            return OpenFileResult.LocalFileMissing
        }
        return OpenFileResult.ReadyForVideoPlayer(
            fileId = fileId,
            fileName = name,
            localPath = localPath,
            mimeType = mimeType ?: DEFAULT_VIDEO_MIME_TYPE
        )
    }

    // [语法] 这是 CloudFile 的扩展函数，相当于 Java 静态工具方法 OpenFileChecks.checkedLocalPath(file)。
    // [设计] 为什么这样写：localPath 的空值和空白字符串都不能打开，集中处理后 TXT/视频分支不会写重复判断。
    private fun CloudFile.checkedLocalPath(): String? {
        return localPath?.takeIf { path -> path.isNotBlank() }
    }

    // [语法] 这是 String 的扩展函数，相当于 Java 静态工具方法 OpenFileChecks.hasReadableFile(path)。
    // [设计] 为什么这样写：数据库记录可能比磁盘文件寿命更长，打开前检查 exists/isFile/canRead 能提前给出稳定错误结果。
    private fun String.hasReadableFile(): Boolean {
        val file = File(this)
        return file.exists() && file.isFile && file.canRead()
    }

    // [语法] companion object 相当于 Java 的 static 常量区域。
    // [设计] 为什么这样写：部分视频来源可能没有 MIME，系统播放器兜底用 video/* 比空字符串更容易被系统识别。
    companion object {
        private const val DEFAULT_VIDEO_MIME_TYPE = "video/*"
    }
}
