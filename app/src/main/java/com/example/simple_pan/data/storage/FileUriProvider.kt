package com.example.simple_pan.data.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

// [设计] 为什么这样写：系统播放器不能读取 App 私有 file:// 路径，必须通过 FileProvider 生成临时可授权的 content:// Uri。
class FileUriProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    // [设计] 为什么这样写：上层通常从 Room 拿到 localPath 字符串，这里统一转 File 并做私有目录校验，避免每个 UseCase 重复写路径保护。
    fun buildContentUri(localPath: String): Uri? {
        return buildContentUri(File(localPath))
    }

    // [设计] 为什么这样写：只允许已存在的普通文件生成 Uri；目录或不存在的文件交给上层显示“文件不存在或已被删除”。
    fun buildContentUri(file: File): Uri? {
        if (!file.exists() || !file.isFile || !file.isInsideUploadRoot()) {
            return null
        }

        return FileProvider.getUriForFile(
            context,
            buildAuthority(context.packageName),
            file
        )
    }

    // [语法] 这是 File 的扩展函数，相当于 Java 静态工具方法 FileUriProvider.isInsideUploadRoot(file)。
    // [设计] 为什么这样写：FileProvider 配置只开放 uploads 目录，代码层也做同样边界判断，防止未来传错 localPath 时暴露非上传文件。
    private fun File.isInsideUploadRoot(): Boolean {
        val uploadRoot = File(context.filesDir, UPLOAD_DIR_NAME).canonicalFile
        val targetFile = canonicalFile
        return targetFile.path == uploadRoot.path ||
            targetFile.path.startsWith(uploadRoot.path + File.separator)
    }

    // [语法] companion object 相当于 Java 的 static 常量和静态方法区域。
    // [设计] 为什么这样写：Manifest 和代码必须使用同一个 authority 规则，集中成函数能避免后续拼错。
    companion object {
        private const val AUTHORITY_SUFFIX = ".fileprovider"
        private const val UPLOAD_DIR_NAME = "uploads"

        fun buildAuthority(packageName: String): String = packageName + AUTHORITY_SUFFIX
    }
}
