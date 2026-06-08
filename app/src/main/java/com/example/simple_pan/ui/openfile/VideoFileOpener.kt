package com.example.simple_pan.ui.openfile

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.AndroidRuntimeException
import androidx.core.content.FileProvider
import java.io.File

// [语法] 这是 Context 的扩展函数，相当于 Java 静态工具方法 VideoFileOpener.openVideoFile(context, path, mimeType)。
// [设计] 为什么这样写：系统播放器是 Android 平台动作，只能放在 UI 层；多个页面共用同一套 FileProvider 逻辑，避免首页、文件页、搜索页各写一份。
fun Context.openVideoFile(localPath: String, mimeType: String): String? {
    val videoFile = File(localPath)
    if (!videoFile.exists() || !videoFile.isFile) {
        return "本地视频文件不存在，请重新上传"
    }

    val contentUri = try {
        FileProvider.getUriForFile(
            this,
            packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
            videoFile
        )
    } catch (exception: IllegalArgumentException) {
        return "视频文件无法授权给系统播放器"
    }

    val safeMimeType = if (mimeType.isBlank()) {
        DEFAULT_VIDEO_MIME_TYPE
    } else {
        mimeType
    }
    val videoIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, safeMimeType)
        clipData = ClipData.newUri(contentResolver, "video", contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return try {
        startActivity(videoIntent)
        null
    } catch (exception: ActivityNotFoundException) {
        "没有可用的视频播放器"
    } catch (exception: SecurityException) {
        "无法授权视频文件给播放器"
    } catch (exception: AndroidRuntimeException) {
        "无法启动视频播放器"
    }
}

// [设计] 为什么这样写：FileProvider authority 必须和 Manifest 中的 `${applicationId}.fileprovider` 保持一致，避免暴露 file:// 真实路径。
private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

private const val DEFAULT_VIDEO_MIME_TYPE = "video/*"
