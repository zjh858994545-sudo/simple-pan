package com.example.simple_pan.deeplink

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// [语法] sealed interface 表示受限结果类型，类似 Java 里固定子类集合的抽象父类型。
// [设计] 为什么这样写：剪贴板或系统 DeepLink 解析会遇到“不是本 App 链接、路由不支持、缺 token、token 非法、解析成功”等分支，结构化结果比返回 null 更好排查。
sealed interface DeepLinkParseResult {
    // [语法] data class 相当于 Java 的只读结果 Bean，用来携带分享预览页需要的 token。
    // [设计] 为什么这样写：分享预览页只需要 token 再去 Room 查快照，链接本身不携带文件明文。
    data class Share(val token: String) : DeepLinkParseResult

    // [语法] data object 是无参数单例结果，类似 Java enum 的一个固定值。
    // [设计] 为什么这样写：剪贴板里可能是普通文本或其它链接，这种情况应静默忽略，不当作错误弹给用户。
    data object NotSimplePanLink : DeepLinkParseResult

    // [语法] data object 是无参数单例结果，适合表达固定失败原因。
    // [设计] 为什么这样写：未来可能有 simplepan://file 等其它路由，当前阶段只支持 share，明确区分“本 App 链接但路由未实现”。
    data object UnsupportedRoute : DeepLinkParseResult

    // [设计] 为什么这样写：缺 token 的分享链接无法查快照，单独建模能让后续 UI 显示更准确的提示。
    data object MissingToken : DeepLinkParseResult

    // [设计] 为什么这样写：token 格式不合法时不能进入分享页，避免把明显脏数据传入 Repository 查询。
    data object InvalidToken : DeepLinkParseResult
}

// [语法] object 是 Kotlin 单例对象，适合承载无状态的解析函数。
// [设计] 为什么这样写：DeepLink 解析会被系统入口和剪贴板检测复用，集中到一个 Parser 能保证所有入口只接受 token-only 分享链接。
object DeepLinkParser {
    // [设计] 为什么这样写：剪贴板里常常包含整段分享文案，所以 parse 先从文本中提取 simplepan:// 链接，再解析 token。
    fun parse(input: String): DeepLinkParseResult {
        val candidateLink = input.extractSimplePanLinkCandidate()
            ?: return DeepLinkParseResult.NotSimplePanLink
        val uri = candidateLink.toUriOrNull()
            ?: return DeepLinkParseResult.NotSimplePanLink

        if (uri.scheme != ShareLinkSpec.SCHEME) {
            return DeepLinkParseResult.NotSimplePanLink
        }
        if (uri.host != ShareLinkSpec.SHARE_HOST) {
            return DeepLinkParseResult.UnsupportedRoute
        }

        val token = uri.queryParameters()[ShareLinkSpec.TOKEN_QUERY_KEY]
        if (token.isNullOrBlank()) {
            return DeepLinkParseResult.MissingToken
        }

        val normalizedToken = ShareLinkSpec.normalizeToken(token)
            ?: return DeepLinkParseResult.InvalidToken
        return DeepLinkParseResult.Share(normalizedToken)
    }
}

// [设计] 为什么这样写：分享文案中可能有标题、数量、换行等普通文本；这里只截取第一个 simplepan:// 开头的候选链接。
private fun String.extractSimplePanLinkCandidate(): String? {
    return SIMPLEPAN_LINK_PATTERN.find(this)
        ?.value
        ?.trimTrailingLinkPunctuation()
}

// [语法] 这是 String 的扩展函数，相当于 Java 静态工具方法 UriParsers.toUriOrNull(text)。
// [设计] 为什么这样写：URI 构造可能因脏剪贴板内容抛异常，统一转成 null 让主流程用结构化结果处理。
private fun String.toUriOrNull(): URI? {
    return try {
        URI(this)
    } catch (exception: IllegalArgumentException) {
        null
    }
}

// [语法] 这是 URI 的扩展函数，相当于 Java 静态工具方法 QueryParsers.queryParameters(uri)。
// [设计] 为什么这样写：当前只需要 token，但用通用 query 解析能自然兼容后续添加来源标记等非敏感参数。
private fun URI.queryParameters(): Map<String, String> {
    val query = rawQuery ?: return emptyMap()
    // [语法] mapNotNull 后面的 { } 是尾随 lambda，pair 是每个 query 片段；返回 null 的片段会被自动过滤掉。
    return query.split("&")
        .mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size != 2) {
                null
            } else {
                parts[0].urlDecode() to parts[1].urlDecode()
            }
        }
        .toMap()
}

// [语法] 这是 String 的扩展函数，相当于 Java 静态工具方法 UrlCodecs.decode(value)。
// [设计] 为什么这样写：虽然当前 token 使用 URL 安全字符，query key/value 仍按 URL 规则解码，避免后续解析其它参数时踩坑。
private fun String.urlDecode(): String {
    return URLDecoder.decode(this, StandardCharsets.UTF_8.name())
}

// [语法] 这是 String 的扩展函数；trimEnd 会从字符串末尾裁掉符合条件的字符。
// [设计] 为什么这样写：用户粘贴的链接后可能跟中文句号或逗号，先裁掉尾部标点可以提高剪贴板识别成功率。
private fun String.trimTrailingLinkPunctuation(): String {
    // [语法] trimEnd 后面的 { } 是尾随 lambda，char 表示从末尾检查到的当前字符。
    return trimEnd { char ->
        char.isWhitespace() || char in TRAILING_LINK_PUNCTUATION
    }
}

// [设计] 为什么这样写：只扫描 simplepan:// 开头的候选链接，避免普通 http 文本触发分享解析。
private val SIMPLEPAN_LINK_PATTERN = Regex("simplepan://\\S+")

// [设计] 为什么这样写：剪贴板链接常会被中文标点包住，尾部标点不应成为 URI 的一部分。
private val TRAILING_LINK_PUNCTUATION = setOf('。', '，', '、', '；', ';', ',', '.', ')', '）')
