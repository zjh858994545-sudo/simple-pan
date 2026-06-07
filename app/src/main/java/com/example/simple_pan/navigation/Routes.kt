package com.example.simple_pan.navigation

import android.net.Uri

// [语法] object 是 Kotlin 单例，相当于 Java 里 private 构造函数 + static INSTANCE。
// [设计] 为什么这样写：路由字符串集中管理，避免 Navigation 和底部 Tab 各写一份字符串导致跳转拼错。
object Routes {
    const val HOME = "home"
    const val FILES = "files"
    const val SEARCH = "search"
    const val TRANSFER_LIST = "transfer_list"
    const val TRANSFER_SETTINGS = "transfer_settings"
    const val SPACE_MANAGEMENT = "space_management"
    const val TOTAL_SPACE_DETAIL = "total_space_detail"
    const val MY_SUBSCRIPTION = "my_subscription"
    const val MY_SHARE = "my_share"
    const val CLOUD_COLLECTION = "cloud_collection"
    const val RECENT_RECORD_TYPE_ARG = "recordType"
    const val RECENT_RECORD_TYPE_TRANSFER = "transfer"
    const val RECENT_RECORD_TYPE_OPEN = "open"
    const val RECENT_RECORDS_ROUTE = "recent_records/{$RECENT_RECORD_TYPE_ARG}"

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

    // [设计] 为什么这样写：首页“最近转存/最近浏览”的全部按钮必须进入历史列表，而不是复用文件根目录。
    fun recentRecords(recordType: String): String {
        return "recent_records/${Uri.encode(recordType)}"
    }

    // [设计] 为什么这样写：分享预览页只通过 token 定位快照，不在路由里暴露 file_id、path 或文件名。
    fun sharePreview(token: String): String {
        return "share_preview/${Uri.encode(token)}"
    }
}
