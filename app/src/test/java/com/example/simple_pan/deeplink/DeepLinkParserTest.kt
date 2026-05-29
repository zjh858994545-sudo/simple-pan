package com.example.simple_pan.deeplink

import org.junit.Assert.assertEquals
import org.junit.Test

// [设计] 为什么这样写：DeepLinkParser 后续会服务剪贴板前台检测和系统 DeepLink 入口，单元测试先锁住“只通过 token 进入分享预览”的核心行为。
class DeepLinkParserTest {
    @Test
    fun parseShareLink_returnsShareToken() {
        val result = DeepLinkParser.parse("simplepan://share?token=$TOKEN")

        assertEquals(DeepLinkParseResult.Share(TOKEN), result)
    }

    @Test
    fun parseShareText_extractsEmbeddedShareLink() {
        val shareText = ShareLinkBuilder.buildShareText(
            title = "演示视频",
            fileCount = 1,
            token = TOKEN
        )

        val result = DeepLinkParser.parse(shareText)

        assertEquals(DeepLinkParseResult.Share(TOKEN), result)
    }

    @Test
    fun parsePlainText_returnsNotSimplePanLink() {
        val result = DeepLinkParser.parse("这是一段普通剪贴板文本")

        assertEquals(DeepLinkParseResult.NotSimplePanLink, result)
    }

    @Test
    fun parseUnsupportedRoute_returnsUnsupportedRoute() {
        val result = DeepLinkParser.parse("simplepan://file?token=$TOKEN")

        assertEquals(DeepLinkParseResult.UnsupportedRoute, result)
    }

    @Test
    fun parseMissingToken_returnsMissingToken() {
        val result = DeepLinkParser.parse("simplepan://share")

        assertEquals(DeepLinkParseResult.MissingToken, result)
    }

    @Test
    fun parseInvalidToken_returnsInvalidToken() {
        val result = DeepLinkParser.parse("simplepan://share?token=file_id=plain-text")

        assertEquals(DeepLinkParseResult.InvalidToken, result)
    }

    // [语法] companion object 相当于 Java 的 static 常量区域。
    // [设计] 为什么这样写：测试 token 与仓库层生成规则保持一致，长度 22 且只包含 URL 安全字符。
    private companion object {
        const val TOKEN = "0123456789ABCDEFGHIJKL"
    }
}
