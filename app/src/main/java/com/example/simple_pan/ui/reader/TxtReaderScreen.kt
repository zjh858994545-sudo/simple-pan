package com.example.simple_pan.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

// [设计] 为什么这样写：阶段 5 第 5 步只建立阅读器页面骨架和导航入口，不提前读取 TXT 内容，避免把“导航可达”和“文件解码/分页”两个验收点混在一起。
@Composable
fun TxtReaderScreen(
    fileId: String,
    fileName: String,
    onBackClick: () -> Unit
) {
    val displayName = if (fileName.isBlank()) {
        fileId
    } else {
        fileName
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            TxtReaderHeader(
                title = displayName,
                onBackClick = onBackClick
            )
            HorizontalDivider()
            TxtReaderBody(
                modifier = Modifier.weight(1f)
            )
            HorizontalDivider()
            TxtReaderPagerBar()
        }
    }
}

// [设计] 为什么这样写：阅读器需要自己的返回入口，因为二级页面隐藏底部 Tab；标题先使用路由传入的文件名，后续读库后可替换成最新名称。
@Composable
private fun TxtReaderHeader(
    title: String,
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBackClick) {
            Text(text = "返回")
        }
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// [设计] 为什么这样写：正文区域先保留稳定版式，下一步接入文件读取时只替换这里的数据来源，不影响导航和顶部/底部分页布局。
@Composable
private fun TxtReaderBody(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "正在准备文本内容",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "第 1 页",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// [设计] 为什么这样写：分页控制先固定在底部，后续 v1 固定字数分页接入后只需要把页码和按钮可用状态改成真实 State。
@Composable
private fun TxtReaderPagerBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            enabled = false,
            onClick = {}
        ) {
            Text(text = "上一页")
        }
        Text(
            text = "1 / 1",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Button(
            enabled = false,
            onClick = {}
        ) {
            Text(text = "下一页")
        }
    }
}
