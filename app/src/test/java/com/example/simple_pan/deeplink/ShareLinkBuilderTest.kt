package com.example.simple_pan.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// [设计] 为什么这样写：分享链接是后续 DeepLink 和剪贴板识别的地基，先用单元测试锁住 token-only 格式，避免未来误把 file_id/path 拼进链接。
class ShareLinkBuilderTest {
    @Test
    fun buildShareLink_onlyIncludesToken() {
        val link = ShareLinkBuilder.buildShareLink(TOKEN)

        assertEquals("simplepan://share?token=$TOKEN", link)
        assertFalse(link.contains("file_id"))
        assertFalse(link.contains("path"))
        assertFalse(link.contains("sourceFileId"))
    }

    @Test
    fun buildShareText_containsTitleCountAndShareLink() {
        val shareText = ShareLinkBuilder.buildShareText(
            title = "学习资料",
            fileCount = 3,
            token = TOKEN
        )

        assertTrue(shareText.contains("标题：学习资料"))
        assertTrue(shareText.contains("数量：3 项"))
        assertTrue(shareText.contains("链接：simplepan://share?token=$TOKEN"))
    }

    @Test
    fun buildShareLink_rejectsInvalidToken() {
        // [语法] assertThrows 后面的 { } 是尾随 lambda，里面放预期会抛异常的代码。
        assertThrows(IllegalArgumentException::class.java) {
            ShareLinkBuilder.buildShareLink("file_id=plain-text")
        }
    }

    // [语法] companion object 相当于 Java 的 static 常量区域。
    // [设计] 为什么这样写：测试 token 与仓库层生成规则保持一致，长度 22 且只包含 URL 安全字符。
    private companion object {
        const val TOKEN = "0123456789ABCDEFGHIJKL"
    }
}
