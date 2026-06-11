package com.example.simple_pan.ui.search

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.AndroidRuntimeException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.simple_pan.domain.model.CloudFile
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.ui.component.WukongFileTypeIcon
import com.example.simple_pan.ui.component.WukongPageBackground
import java.io.File
import kotlinx.coroutines.delay

// [设计] 为什么这样写：搜索页连接 ViewModel 的真实 Room 查询结果，Screen 只负责输入、展示和 Android 平台打开动作。
@Composable
fun PanSearchScreen(
    onBackClick: () -> Unit,
    onOpenTxtReader: (fileId: String, fileName: String) -> Unit,
    viewModel: PanSearchViewModel = hiltViewModel()
) {
    // [语法] by 是 Kotlin 委托语法，这里把 State<PanSearchState> 解包成普通变量。
    // [设计] 为什么这样写：collectAsStateWithLifecycle 能随页面生命周期自动订阅/停止订阅，避免后台搜索页继续刷新 UI。
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // [语法] LaunchedEffect 会在页面进入组合后启动协程；这里用于自动聚焦输入框。
    // [设计] 为什么这样写：参考图进入搜索页时输入框已获得焦点，延迟一小段时间能等 TextField 完成布局后再请求焦点。
    LaunchedEffect(Unit) {
        delay(120L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // [设计] 为什么这样写：Effect 是一次性事件，集中在 Screen 消费；ViewModel 不直接依赖 Context、Intent 或导航控制器。
    LaunchedEffect(viewModel, context) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PanSearchEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is PanSearchEffect.OpenTxtReader -> {
                    onOpenTxtReader(effect.fileId, effect.fileName)
                }
                is PanSearchEffect.OpenVideoPlayer -> {
                    val errorMessage = context.openVideoFile(
                        localPath = effect.localPath,
                        mimeType = effect.mimeType
                    )
                    if (errorMessage == null) {
                        viewModel.onIntent(PanSearchIntent.RecordOpenedFile(effect.fileId))
                    } else {
                        snackbarHostState.showSnackbar(errorMessage)
                    }
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { contentPadding ->
        PanSearchContent(
            modifier = Modifier.padding(contentPadding),
            state = state,
            focusRequester = focusRequester,
            onBackClick = onBackClick,
            onKeywordChange = { keyword ->
                viewModel.onIntent(PanSearchIntent.ChangeKeyword(keyword))
            },
            onSubmitSearch = {
                keyboardController?.hide()
                viewModel.onIntent(PanSearchIntent.SubmitSearch)
            },
            onRetry = {
                viewModel.onIntent(PanSearchIntent.Retry)
            },
            onFileClick = { file ->
                viewModel.onIntent(PanSearchIntent.OpenFile(file.fileId))
            }
        )
    }
}

// [设计] 为什么这样写：纯展示函数只依赖 State 和回调，后续 Preview 或 UI 测试可以直接构造状态，不需要真实数据库。
@Composable
private fun PanSearchContent(
    modifier: Modifier = Modifier,
    state: PanSearchState,
    focusRequester: FocusRequester,
    onBackClick: () -> Unit,
    onKeywordChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onRetry: () -> Unit,
    onFileClick: (CloudFile) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = WukongPageBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            SearchHeader(
                keyword = state.keyword,
                focusRequester = focusRequester,
                onBackClick = onBackClick,
                onKeywordChange = onKeywordChange,
                onSubmitSearch = onSubmitSearch
            )
            SearchBody(
                modifier = Modifier.weight(1f),
                state = state,
                onRetry = onRetry,
                onFileClick = onFileClick
            )
        }
    }
}

// [设计] 为什么这样写：顶部结构按参考图固定为返回按钮 + 搜索框 + 搜索按钮，提交动作同时支持键盘搜索键和按钮点击。
@Composable
private fun SearchHeader(
    keyword: String,
    focusRequester: FocusRequester,
    onBackClick: () -> Unit,
    onKeywordChange: (String) -> Unit,
    onSubmitSearch: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBackClick) {
            Text(
                text = "<",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .focusRequester(focusRequester),
            value = keyword,
            onValueChange = onKeywordChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = "搜索网盘文件",
                    color = Color(0xFF999999)
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSubmitSearch()
                }
            ),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF0F0F0),
                unfocusedContainerColor = Color(0xFFF0F0F0),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )
        TextButton(onClick = onSubmitSearch) {
            Text(
                text = "搜索",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// [设计] 为什么这样写：正文区域统一承载未搜索、加载、错误、空结果和结果列表，顶部搜索框不需要关心数据状态细节。
@Composable
private fun SearchBody(
    modifier: Modifier = Modifier,
    state: PanSearchState,
    onRetry: () -> Unit,
    onFileClick: (CloudFile) -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        when {
            !state.hasSearched -> SearchHint()
            state.isLoading -> SearchLoading(keyword = state.submittedKeyword)
            state.errorMessage != null -> SearchError(
                message = state.errorMessage,
                onRetry = onRetry
            )
            state.results.isEmpty() -> SearchEmpty(keyword = state.submittedKeyword)
            else -> SearchResultList(
                files = state.results,
                onFileClick = onFileClick
            )
        }
    }
}

@Composable
private fun SearchHint() {
    Text(
        text = "输入关键词搜索网盘文件",
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFF9A9A9A)
    )
}

@Composable
private fun SearchLoading(keyword: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Color.Black)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "正在搜索“$keyword”",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF666666)
        )
    }
}

@Composable
private fun SearchError(
    message: String,
    onRetry: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(text = "重试")
        }
    }
}

@Composable
private fun SearchEmpty(keyword: String) {
    Text(
        text = "没有找到“$keyword”相关文件",
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFF9A9A9A)
    )
}

// [设计] 为什么这样写：搜索结果按 Room 返回顺序展示，排序规则和文件页综合排序保持一致，用户搜索后更容易找到文件夹和最近更新文件。
@Composable
private fun SearchResultList(
    files: List<CloudFile>,
    onFileClick: (CloudFile) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = files,
            key = { file -> file.fileId }
        ) { file ->
            SearchResultRow(
                file = file,
                onClick = { onFileClick(file) }
            )
        }
    }
}

// [设计] 为什么这样写：结果行复用文件页“类型标识 + 名称 + 类型/大小”的信息层级，但保持搜索页更简洁。
@Composable
private fun SearchResultRow(
    file: CloudFile,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        onClick = onClick
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                WukongFileTypeIcon(fileType = file.type)
            },
            headlineContent = {
                Text(
                    text = file.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827)
                )
            },
            supportingContent = {
                Text(
                    text = "${file.type.toDisplayName()} · ${file.sizeBytes.toSizeText()}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF6B7280)
                )
            },
            trailingContent = {
                Text(
                    text = "打开",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF4B5563)
                )
            }
        )
        HorizontalDivider(color = Color.Transparent)
    }
}

private fun FileType.toDisplayName(): String {
    return when (this) {
        FileType.Folder -> "文件夹"
        FileType.Video -> "视频"
        FileType.Txt -> "文档"
        FileType.Image -> "图片"
        FileType.Audio -> "音频"
        FileType.Other -> "其他"
    }
}

private fun Long.toSizeText(): String {
    val kb = 1024L
    val mb = kb * 1024L
    return when {
        this >= mb -> "${this / mb} MB"
        this >= kb -> "${this / kb} KB"
        else -> "$this B"
    }
}

// [设计] 为什么这样写：FileProvider authority 必须和 Manifest 中的 `${applicationId}.fileprovider` 保持一致，避免暴露 file:// 真实路径。
private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

private const val DEFAULT_VIDEO_MIME_TYPE = "video/*"

// [语法] 这是 Context 的扩展函数，相当于 Java 静态工具方法 VideoOpeners.openVideoFile(context, path, mimeType)。
// [设计] 为什么这样写：打开系统播放器是 Android 平台动作，只能放在 Screen 层；ViewModel 只发出“准备打开视频”的 Effect。
private fun Context.openVideoFile(localPath: String, mimeType: String): String? {
    val videoFile = File(localPath)
    if (!videoFile.exists() || !videoFile.isFile) {
        return "本地视频文件不存在，请重新上传"
    }

    val contentUri = try {
        FileProvider.getUriForFile(
            this,
            packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
            videoFile
        )
    } catch (exception: IllegalArgumentException) {
        return "视频文件无法授权给系统播放器"
    }

    val safeMimeType = if (mimeType.isBlank()) {
        DEFAULT_VIDEO_MIME_TYPE
    } else {
        mimeType
    }
    val videoIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, safeMimeType)
        clipData = ClipData.newUri(contentResolver, "video", contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return try {
        startActivity(videoIntent)
        null
    } catch (exception: ActivityNotFoundException) {
        "没有可用的视频播放器"
    } catch (exception: SecurityException) {
        "无法授权视频文件给播放器"
    } catch (exception: AndroidRuntimeException) {
        "无法启动视频播放器"
    }
}
