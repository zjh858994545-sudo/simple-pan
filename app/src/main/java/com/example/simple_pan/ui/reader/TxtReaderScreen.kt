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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// [设计] 为什么这样写：阅读器页面只连接路由参数和 ViewModel State，真正的磁盘读取交给 UseCase，页面本身保持纯展示。
@Composable
fun TxtReaderScreen(
    fileId: String,
    fileName: String,
    onBackClick: () -> Unit,
    viewModel: TxtReaderViewModel = hiltViewModel()
) {
    // [语法] by 是 Kotlin 委托语法，这里把 State<TxtReaderState> 解包成普通变量，类似 Java 每次调用 state.getValue()。
    // [设计] 为什么这样写：阅读器内容来自异步读取结果，collectAsStateWithLifecycle 能随页面生命周期自动收集，避免后台页面继续读取状态。
    val state by viewModel.state.collectAsStateWithLifecycle()
    // [语法] LaunchedEffect 会在参数变化时启动协程，类似 Java 里监听参数变化后触发一次异步任务。
    // [设计] 为什么这样写：进入同一个阅读器 Composable 时只按 fileId/fileName 触发读取，避免每次重组都重新读磁盘。
    LaunchedEffect(fileId, fileName) {
        viewModel.onIntent(
            TxtReaderIntent.LoadFile(
                fileId = fileId,
                fallbackFileName = fileName
            )
        )
    }

    val displayName = state.fileName.ifBlank { fileName.ifBlank { fileId } }

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
                modifier = Modifier.weight(1f),
                state = state,
                onRetry = {
                    viewModel.onIntent(
                        TxtReaderIntent.LoadFile(
                            fileId = fileId,
                            fallbackFileName = fileName
                        )
                    )
                }
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

// [设计] 为什么这样写：正文区域统一承载加载、错误、空文件和内容状态，底部分页骨架不需要关心文件读取细节。
@Composable
private fun TxtReaderBody(
    modifier: Modifier = Modifier,
    state: TxtReaderState,
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isLoading -> TxtReaderLoading()
            state.errorMessage != null -> TxtReaderError(
                message = state.errorMessage,
                onRetry = onRetry
            )
            state.content.isEmpty() -> TxtReaderEmpty()
            else -> TxtReaderContent(content = state.content)
        }
    }
}

// [设计] 为什么这样写：加载状态放在正文中心，用户能确认已经进入阅读器，只是在等待磁盘读取完成。
@Composable
private fun TxtReaderLoading() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "正在读取 TXT 内容",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// [设计] 为什么这样写：读取错误不退出页面，提供重试入口，方便用户在文件恢复或重新上传后立刻验证。
@Composable
private fun TxtReaderError(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(text = "重试")
        }
    }
}

// [设计] 为什么这样写：空 TXT 是合法文件，必须和读取失败区分开，避免用户误以为文件损坏。
@Composable
private fun TxtReaderEmpty() {
    Text(
        text = "TXT 文件为空",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// [设计] 为什么这样写：第 6 步先直接展示完整文本并允许滚动，下一步 v1 固定字数分页会基于同一个 content 字段切成页。
@Composable
private fun TxtReaderContent(content: String) {
    Text(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        text = content,
        style = MaterialTheme.typography.bodyLarge
    )
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
