package com.example.simple_pan.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// [设计] 为什么这样写：分享预览页先建立可导航的 UI 骨架，本步骤只验证 token 路由能进入；真实快照列表加载放到下一步接 ViewModel/Repository。
@Composable
fun SharePreviewScreen(
    token: String,
    onBackClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SharePreviewHeader(onBackClick = onBackClick)
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))
            SharePreviewBody(
                maskedToken = token.toMaskedShareToken(),
                onBackClick = onBackClick
            )
        }
    }
}

// [设计] 为什么这样写：预览页是二级页面，头部提供明确返回入口，底部 Tab 会由导航层隐藏。
@Composable
private fun SharePreviewHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [语法] TextButton 后面的 { } 是尾随 lambda，用来声明按钮内部要显示的 Compose 内容。
        TextButton(onClick = onBackClick) {
            Text(text = "返回")
        }
        Text(
            text = "分享预览",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// [设计] 为什么这样写：当前骨架只展示分享码和加载占位，用户能确认已经进入分享页；文件列表会在下一步由快照数据驱动。
@Composable
private fun SharePreviewBody(
    maskedToken: String,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "分享内容",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "分享码 $maskedToken",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "正在加载文件列表",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onBackClick) {
            Text(text = "返回文件")
        }
    }
}

// [语法] 这是 String 的扩展函数，相当于 Java 静态工具方法 ShareTokenDisplay.mask(token)。
// [设计] 为什么这样写：预览页只需要让用户识别分享码，不必完整暴露 token；后续真实查询仍使用未脱敏 token。
private fun String.toMaskedShareToken(): String {
    val normalizedToken = trim()
    return if (normalizedToken.length <= MASK_VISIBLE_PREFIX + MASK_VISIBLE_SUFFIX) {
        normalizedToken
    } else {
        normalizedToken.take(MASK_VISIBLE_PREFIX) + "..." + normalizedToken.takeLast(MASK_VISIBLE_SUFFIX)
    }
}

// [设计] 为什么这样写：分享码脱敏时保留前 6 位，便于用户识别是哪一次分享。
private const val MASK_VISIBLE_PREFIX = 6

// [设计] 为什么这样写：分享码脱敏时保留后 4 位，和前缀组合能降低不同分享码看起来完全相同的概率。
private const val MASK_VISIBLE_SUFFIX = 4
