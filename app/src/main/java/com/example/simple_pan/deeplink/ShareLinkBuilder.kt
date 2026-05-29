package com.example.simple_pan.deeplink

// [语法] object 是 Kotlin 单例对象，适合承载无状态的链接构建函数。
// [设计] 为什么这样写：分享链接生成必须统一走一个入口，保证复制到剪贴板、后续分享页和测试使用同一套 token-only 规则。
object ShareLinkBuilder {
    private const val DEFAULT_SHARE_TITLE = "我的分享"

    // [设计] 为什么这样写：分享链接只暴露不可猜测 token，不把 file_id、path、文件名放进 URL，降低链接外泄时的明文信息。
    fun buildShareLink(token: String): String {
        val normalizedToken = requireNotNull(ShareLinkSpec.normalizeToken(token)) {
            "分享 token 格式不正确"
        }
        return "${ShareLinkSpec.SCHEME}://${ShareLinkSpec.SHARE_HOST}" +
            "?${ShareLinkSpec.TOKEN_QUERY_KEY}=$normalizedToken"
    }

    // [设计] 为什么这样写：剪贴板里给用户的不只是裸链接，还要有标题和数量，便于粘贴后确认自己分享的内容。
    fun buildShareText(
        title: String,
        fileCount: Int,
        token: String
    ): String {
        require(fileCount > 0) {
            "分享文件数量必须大于 0"
        }
        val normalizedTitle = title.ifBlank { DEFAULT_SHARE_TITLE }
        val shareLink = buildShareLink(token)
        // [语法] buildString 后面的 { } 是尾随 lambda，类似 Java 里把 StringBuilder 拼接逻辑传给一个构建函数。
        return buildString {
            appendLine("SimplePan 分享")
            appendLine("标题：$normalizedTitle")
            appendLine("数量：$fileCount 项")
            append("链接：$shareLink")
        }
    }
}
