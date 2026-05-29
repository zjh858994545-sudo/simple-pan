package com.example.simple_pan.navigation

import android.net.Uri

// [语法] object 是 Kotlin 单例，相当于 Java 里 private 构造函数 + static INSTANCE。
// [设计] 为什么这样写：路由字符串集中管理，避免 Navigation 和底部 Tab 各写一份字符串导致跳转拼错。
object Routes {
    const val HOME = "home"
    const val FILES = "files"

    const val TXT_READER_FILE_ID_ARG = "fileId"
    const val TXT_READER_FILE_NAME_ARG = "fileName"
    const val TXT_READER_ROUTE =
        "txt_reader/{$TXT_READER_FILE_ID_ARG}?$TXT_READER_FILE_NAME_ARG={$TXT_READER_FILE_NAME_ARG}"

    const val SHARE_PREVIEW_TOKEN_ARG = "token"
    const val SHARE_PREVIEW_ROUTE = "share_preview/{$SHARE_PREVIEW_TOKEN_ARG}"

    // [设计] 为什么这样写：TXT 阅读器是二级页面，不放到底部 Tab；通过 fileId 定位文件，fileName 只用于先显示标题骨架。
    fun txtReader(fileId: String, fileName: String): String {
        return "txt_reader/${Uri.encode(fileId)}?$TXT_READER_FILE_NAME_ARG=${Uri.encode(fileName)}"
    }

    // [设计] 为什么这样写：分享预览页只通过 token 定位快照，不在路由里暴露 file_id、path 或文件名。
    fun sharePreview(token: String): String {
        return "share_preview/${Uri.encode(token)}"
    }
}
