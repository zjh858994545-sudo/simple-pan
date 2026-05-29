package com.example.simple_pan.deeplink

// [语法] object 是 Kotlin 单例对象，类似 Java 里的 static 工具类实例。
// [设计] 为什么这样写：分享链接的 scheme、host、query key 和 token 校验规则必须在构建器/解析器之间保持一致，集中到一个内部规范对象能避免两边各写一份。
internal object ShareLinkSpec {
    const val SCHEME = "simplepan"
    const val SHARE_HOST = "share"
    const val TOKEN_QUERY_KEY = "token"
    const val SHARE_TOKEN_LENGTH = 22

    private val tokenRegex = Regex("[0-9A-Za-z_-]{$SHARE_TOKEN_LENGTH}")

    // [设计] 为什么这样写：token 只允许仓库层生成的 URL 安全字符，分享链接里就不需要出现 file_id、path、name 等明文信息。
    fun normalizeToken(token: String): String? {
        val normalizedToken = token.trim()
        return if (tokenRegex.matches(normalizedToken)) {
            normalizedToken
        } else {
            null
        }
    }
}
