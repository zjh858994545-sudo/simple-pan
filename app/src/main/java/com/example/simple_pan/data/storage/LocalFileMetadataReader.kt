package com.example.simple_pan.data.storage

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.example.simple_pan.di.IoDispatcher
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.LocalFileMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

// [设计] 为什么这样写：读取本地文件信息依赖 Android ContentResolver，属于 data/storage 边界；上层只拿整理好的 LocalFileMetadata。
class LocalFileMetadataReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    // [语法] suspend fun 表示挂起函数，必须在协程里调用；withContext 会把阻塞式查询切到 IO 线程。
    // [设计] 为什么这样写：SAF 的 Uri 可能来自不同文档提供者，查询耗时不确定，放到 IO 调度器能避免卡住主线程。
    suspend fun read(uri: Uri): LocalFileMetadata = withContext(ioDispatcher) {
        val resolverMetadata = queryResolverMetadata(uri)
        val displayName = resolverMetadata.displayName ?: uri.fallbackDisplayName()
        val mimeType = context.contentResolver.getType(uri)

        LocalFileMetadata(
            uriString = uri.toString(),
            displayName = displayName,
            sizeBytes = resolverMetadata.sizeBytes ?: UNKNOWN_SIZE_BYTES,
            mimeType = mimeType,
            fileType = inferFileType(mimeType = mimeType, displayName = displayName)
        )
    }

    // [设计] 为什么这样写：OpenableColumns 是 SAF 标准字段，优先从系统提供者读取展示名和大小，比自己解析 Uri 更可靠。
    private fun queryResolverMetadata(uri: Uri): ResolverMetadata {
        val cursor = context.contentResolver.query(
            uri,
            OPENABLE_COLUMNS,
            null,
            null,
            null
        )

        // [语法] ?. 是安全调用，use 会在代码块结束后自动关闭 Cursor，类似 Java try-with-resources。
        cursor?.use {
            if (it.moveToFirst()) {
                return ResolverMetadata(
                    displayName = it.readString(OpenableColumns.DISPLAY_NAME),
                    sizeBytes = it.readLong(OpenableColumns.SIZE)
                )
            }
        }

        return ResolverMetadata(displayName = null, sizeBytes = null)
    }

    // [设计] 为什么这样写：不同文件来源的 MIME 可能缺失或不准确，所以先看 MIME，再用扩展名兜底推断项目内的 FileType。
    private fun inferFileType(mimeType: String?, displayName: String): FileType {
        val normalizedMimeType = mimeType.orEmpty().lowercase()
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()

        return when {
            normalizedMimeType.startsWith("image/") -> FileType.Image
            normalizedMimeType.startsWith("video/") -> FileType.Video
            normalizedMimeType.startsWith("audio/") -> FileType.Audio
            normalizedMimeType == "text/plain" || extension == "txt" -> FileType.Txt
            else -> FileType.Other
        }
    }

    // [设计] 为什么这样写：查询不到 DISPLAY_NAME 时，用 Uri 最后一段做兜底，保证列表里始终有可展示的名字。
    private fun Uri.fallbackDisplayName(): String {
        return lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "未命名文件"
    }

    // [设计] 为什么这样写：Cursor 读取字段前必须判断列是否存在和是否为空，部分系统文件提供者不会返回完整字段。
    private fun Cursor.readString(columnName: String): String? {
        val columnIndex = getColumnIndex(columnName)
        if (columnIndex < 0 || isNull(columnIndex)) {
            return null
        }
        return getString(columnIndex)
    }

    // [设计] 为什么这样写：大小字段缺失时返回 null，由领域策略转成 UnknownSize，而不是把未知大小误当作 0B。
    private fun Cursor.readLong(columnName: String): Long? {
        val columnIndex = getColumnIndex(columnName)
        if (columnIndex < 0 || isNull(columnIndex)) {
            return null
        }
        return getLong(columnIndex)
    }

    // [语法] private data class 只在当前文件内部可见，用于临时承载 Cursor 查询结果。
    // [设计] 为什么这样写：把 ContentResolver 原始结果和领域模型拆开，便于在 fallback 后再统一生成 LocalFileMetadata。
    private data class ResolverMetadata(
        val displayName: String?,
        val sizeBytes: Long?
    )

    // [语法] companion object 相当于 Java 的 static 工具区，放常量和只需创建一次的数组。
    // [设计] 为什么这样写：OpenableColumns 投影字段和未知大小哨兵值都是读取策略的一部分，集中存放避免散落魔法值。
    companion object {
        const val UNKNOWN_SIZE_BYTES: Long = -1L

        private val OPENABLE_COLUMNS = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE
        )
    }
}
