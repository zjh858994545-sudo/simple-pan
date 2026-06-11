package com.example.simple_pan.ui.reader

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.simple_pan.ui.component.WukongTopBarContentTopPadding
import com.example.simple_pan.ui.component.WukongTopBarHeight
import com.example.simple_pan.ui.component.WukongTopIconButton
import com.example.simple_pan.ui.component.WukongTopTitleFontSize
import com.example.simple_pan.ui.component.WukongTopTitleLineHeight
import java.util.LinkedHashMap
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import timber.log.Timber

private const val TXT_READER_PERF_TAG = "TxtReaderPerf"

private data class TxtPaginationCacheKey(
    val fileId: String,
    val contentLength: Int,
    val contentHash: Int,
    val fontSizeSp: Int,
    val pageWidthPx: Int,
    val pageHeightPx: Int
)

private object TxtPaginationCache {
    private const val MAX_ENTRIES = 8

    private val pagesByKey = object : LinkedHashMap<TxtPaginationCacheKey, List<TxtReaderPage>>(
        MAX_ENTRIES,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<TxtPaginationCacheKey, List<TxtReaderPage>>
        ): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun get(key: TxtPaginationCacheKey): List<TxtReaderPage>? {
        return pagesByKey[key]
    }

    @Synchronized
    fun put(key: TxtPaginationCacheKey, pages: List<TxtReaderPage>) {
        pagesByKey[key] = pages
    }
}

// [设计] 为什么这样写：阅读器页面只连接路由参数、ViewModel State 和测量分页 UI；磁盘读取仍交给 UseCase，真实分页则依赖 Compose 文本测量。
@Composable
fun TxtReaderScreen(
    fileId: String,
    fileName: String,
    onBackClick: () -> Unit,
    viewModel: TxtReaderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(fileId, fileName) {
        viewModel.onIntent(
            TxtReaderIntent.LoadFile(
                fileId = fileId,
                fallbackFileName = fileName
            )
        )
    }

    TxtReaderContent(
        state = state,
        fallbackTitle = fileName.ifBlank { fileId },
        onBackClick = onBackClick,
        onRetry = {
            viewModel.onIntent(
                TxtReaderIntent.LoadFile(
                    fileId = fileId,
                    fallbackFileName = fileName
                )
            )
        },
        onPreviousPage = {
            viewModel.onIntent(TxtReaderIntent.PreviousPage)
        },
        onNextPage = {
            viewModel.onIntent(TxtReaderIntent.NextPage)
        },
        onJumpToPage = { pageIndex ->
            viewModel.onIntent(TxtReaderIntent.JumpToPage(pageIndex))
        },
        onChangeFontSize = { deltaSp ->
            viewModel.onIntent(TxtReaderIntent.ChangeFontSize(deltaSp))
        },
        onMeasuredPages = { generation, pages ->
            viewModel.onIntent(
                TxtReaderIntent.ApplyMeasuredPages(
                    generation = generation,
                    pages = pages
                )
            )
        }
    )
}

@Composable
private fun TxtReaderContent(
    state: TxtReaderState,
    fallbackTitle: String,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onChangeFontSize: (Int) -> Unit,
    onMeasuredPages: (generation: Int, pages: List<TxtReaderPage>) -> Unit
) {
    val title = state.fileName.ifBlank { fallbackTitle }
    val readerTextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = state.fontSizeSp.sp,
        lineHeight = (state.fontSizeSp * 1.72f).sp,
        color = ReaderTextColor
    )
    var areReadingControlsVisible by remember { mutableStateOf(false) }
    val canShowReadingControls = state.pageCount > 0 &&
        !state.isLoading &&
        !state.isPaginating &&
        state.errorMessage == null

    LaunchedEffect(canShowReadingControls) {
        if (!canShowReadingControls) {
            areReadingControlsVisible = false
        }
    }

    // [设计] 底部控件只在用户需要时出现，自动隐藏后正文区域保持完整，阅读时更接近沉浸模式。
    LaunchedEffect(
        areReadingControlsVisible,
        state.currentPageIndex,
        state.fontSizeSp
    ) {
        if (areReadingControlsVisible && canShowReadingControls) {
            delay(TXT_READER_CONTROLS_AUTO_HIDE_MS)
            areReadingControlsVisible = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ReaderBackgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            TxtReaderHeader(
                title = title,
                state = state,
                onBackClick = onBackClick,
                onDecreaseFont = { onChangeFontSize(-1) },
                onIncreaseFont = { onChangeFontSize(1) }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.weight(1f)) {
                TxtReaderPageFrame(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    textStyle = readerTextStyle,
                    onRetry = onRetry,
                    onPreviousPage = onPreviousPage,
                    onNextPage = onNextPage,
                    onToggleControls = {
                        if (canShowReadingControls) {
                            areReadingControlsVisible = !areReadingControlsVisible
                        }
                    },
                    onMeasuredPages = onMeasuredPages
                )
                if (areReadingControlsVisible && canShowReadingControls) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        color = ReaderPaperColor.copy(alpha = 0.96f),
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, ReaderBorderColor)
                    ) {
                        TxtReaderBottomBar(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            state = state,
                            onPreviousPage = onPreviousPage,
                            onNextPage = onNextPage,
                            onJumpToPage = onJumpToPage
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TxtReaderHeader(
    title: String,
    state: TxtReaderState,
    onBackClick: () -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WukongTopBarHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = WukongTopBarContentTopPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WukongTopIconButton(
                    text = "<",
                    onClick = onBackClick
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = ReaderTextColor,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = WukongTopTitleFontSize,
                            lineHeight = WukongTopTitleLineHeight
                        ),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (state.pageCount == 0) {
                            "文档阅读器"
                        } else {
                            "第 ${state.currentPageNumber} 页，共 ${state.pageCount} 页 · ${state.readingPercent}%"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = ReaderSecondaryTextColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                ReaderRoundTextButton(
                    text = "A-",
                    enabled = state.canDecreaseFontSize && !state.isLoading,
                    onClick = onDecreaseFont
                )
                Spacer(modifier = Modifier.size(8.dp))
                ReaderRoundTextButton(
                    text = "A+",
                    enabled = state.canIncreaseFontSize && !state.isLoading,
                    onClick = onIncreaseFont
                )
            }
        }
    }
}
@Composable
private fun ReaderRoundTextButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(42.dp),
        color = if (enabled) ReaderPaperColor else ReaderDisabledColor,
        contentColor = if (enabled) ReaderTextColor else ReaderSecondaryTextColor,
        shape = CircleShape,
        border = BorderStroke(1.dp, ReaderBorderColor),
        onClick = onClick,
        enabled = enabled
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun TxtReaderPageFrame(
    modifier: Modifier,
    state: TxtReaderState,
    textStyle: TextStyle,
    onRetry: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onToggleControls: () -> Unit,
    onMeasuredPages: (generation: Int, pages: List<TxtReaderPage>) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { TXT_READER_SWIPE_THRESHOLD_DP.toPx() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ReaderPaperColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ReaderBorderColor)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            val pageWidthPx = with(density) { maxWidth.roundToPx() }
            val pageHeightPx = with(density) { maxHeight.roundToPx() }
            val generation = state.paginationGeneration

            LaunchedEffect(
                state.content,
                state.fontSizeSp,
                pageWidthPx,
                pageHeightPx,
                generation
            ) {
                if (
                    state.content.isNotEmpty() &&
                    pageWidthPx > 0 &&
                    pageHeightPx > 0 &&
                    state.errorMessage == null
                ) {
                    val cacheKeyStartMs = SystemClock.elapsedRealtime()
                    val cacheKey = TxtPaginationCacheKey(
                        fileId = state.fileId,
                        contentLength = state.content.length,
                        contentHash = state.content.hashCode(),
                        fontSizeSp = state.fontSizeSp,
                        pageWidthPx = pageWidthPx,
                        pageHeightPx = pageHeightPx
                    )
                    val cachedPages = TxtPaginationCache.get(cacheKey)
                    val cacheLookupCostMs = SystemClock.elapsedRealtime() - cacheKeyStartMs
                    if (cachedPages != null) {
                        Timber.tag(TXT_READER_PERF_TAG).d(
                            "pagination cache hit generation=%d chars=%d pages=%d fontSizeSp=%d pageWidthPx=%d pageHeightPx=%d lookupCostMs=%d",
                            generation,
                            state.content.length,
                            cachedPages.size,
                            state.fontSizeSp,
                            pageWidthPx,
                            pageHeightPx,
                            cacheLookupCostMs
                        )
                        onMeasuredPages(generation, cachedPages)
                    } else {
                        Timber.tag(TXT_READER_PERF_TAG).d(
                            "pagination cache miss generation=%d chars=%d fontSizeSp=%d pageWidthPx=%d pageHeightPx=%d lookupCostMs=%d",
                            generation,
                            state.content.length,
                            state.fontSizeSp,
                            pageWidthPx,
                            pageHeightPx,
                            cacheLookupCostMs
                        )
                        val paginateStartMs = SystemClock.elapsedRealtime()
                        val pages = paginateMeasuredText(
                            content = state.content,
                            textMeasurer = textMeasurer,
                            textStyle = textStyle,
                            maxWidthPx = pageWidthPx,
                            maxHeightPx = pageHeightPx
                        )
                        val paginateCostMs = SystemClock.elapsedRealtime() - paginateStartMs
                        val averageCharsPerPage = if (pages.isEmpty()) {
                            0
                        } else {
                            state.content.length / pages.size
                        }
                        TxtPaginationCache.put(cacheKey, pages)
                        Timber.tag(TXT_READER_PERF_TAG).d(
                            "paginate success generation=%d chars=%d pages=%d avgCharsPerPage=%d costMs=%d fontSizeSp=%d pageWidthPx=%d pageHeightPx=%d cached=true",
                            generation,
                            state.content.length,
                            pages.size,
                            averageCharsPerPage,
                            paginateCostMs,
                            state.fontSizeSp,
                            pageWidthPx,
                            pageHeightPx
                        )
                        onMeasuredPages(generation, pages)
                    }
                }
            }

            val bodyModifier = Modifier
                .fillMaxSize()
                .pointerInput(state.currentPageIndex, state.pageCount) {
                    var totalHorizontalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            totalHorizontalDrag = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            totalHorizontalDrag += dragAmount
                        },
                        onDragEnd = {
                            if (totalHorizontalDrag <= -swipeThresholdPx && state.canGoNext) {
                                onNextPage()
                            } else if (totalHorizontalDrag >= swipeThresholdPx && state.canGoPrevious) {
                                onPreviousPage()
                            }
                        },
                        onDragCancel = {
                            totalHorizontalDrag = 0f
                        }
                    )
                }
                .pointerInput(state.pageCount) {
                    // [设计] 轻点正文显示或隐藏底部控件；滑动翻页由上一层手势继续负责。
                    detectTapGestures(
                        onTap = {
                            onToggleControls()
                        }
                    )
                }

            Box(
                modifier = bodyModifier,
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.isLoading -> ReaderLoading(text = "正在读取 TXT 内容")
                    state.errorMessage != null -> ReaderError(
                        message = state.errorMessage,
                        onRetry = onRetry
                    )
                    state.content.isEmpty() -> ReaderEmpty()
                    state.isPaginating || state.pages.isEmpty() -> ReaderLoading(text = "正在排版文档")
                    else -> ReaderPageText(
                        text = state.currentPageText,
                        style = textStyle
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderLoading(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = ReaderTextColor)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = text,
            color = ReaderSecondaryTextColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ReaderError(
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

@Composable
private fun ReaderEmpty() {
    Text(
        text = "TXT 文件为空",
        style = MaterialTheme.typography.bodyLarge,
        color = ReaderSecondaryTextColor
    )
}

@Composable
private fun ReaderPageText(
    text: String,
    style: TextStyle
) {
    Text(
        modifier = Modifier.fillMaxSize(),
        text = text,
        style = style,
        overflow = TextOverflow.Clip
    )
}

@Composable
private fun TxtReaderBottomBar(
    modifier: Modifier = Modifier,
    state: TxtReaderState,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onJumpToPage: (Int) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (state.pageCount > 1) {
            Slider(
                value = state.currentPageIndex.toFloat(),
                onValueChange = { value ->
                    onJumpToPage(value.roundToInt())
                },
                valueRange = 0f..state.pages.lastIndex.toFloat()
            )
        } else {
            HorizontalDivider(color = ReaderBorderColor)
            Spacer(modifier = Modifier.height(12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                enabled = state.canGoPrevious,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReaderTextColor,
                    contentColor = ReaderPaperColor,
                    disabledContainerColor = ReaderDisabledColor,
                    disabledContentColor = ReaderSecondaryTextColor
                ),
                onClick = onPreviousPage
            ) {
                Text(text = "上一页")
            }
            Text(
                text = state.toPageIndicatorText(),
                style = MaterialTheme.typography.bodyMedium,
                color = ReaderTextColor,
                fontWeight = FontWeight.Black
            )
            Button(
                enabled = state.canGoNext,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReaderTextColor,
                    contentColor = ReaderPaperColor,
                    disabledContainerColor = ReaderDisabledColor,
                    disabledContentColor = ReaderSecondaryTextColor
                ),
                onClick = onNextPage
            ) {
                Text(text = "下一页")
            }
        }
    }
}

private fun paginateMeasuredText(
    content: String,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int,
    maxHeightPx: Int
): List<TxtReaderPage> {
    if (content.isEmpty()) {
        return emptyList()
    }

    val pages = mutableListOf<TxtReaderPage>()
    var startIndex = 0
    while (startIndex < content.length) {
        val measuredEnd = findMeasuredPageEnd(
            content = content,
            startIndex = startIndex,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            maxWidthPx = maxWidthPx,
            maxHeightPx = maxHeightPx
        )
        val endIndex = refinePageEnd(
            content = content,
            startIndex = startIndex,
            measuredEnd = measuredEnd
        )
        pages += TxtReaderPage(
            text = content.substring(startIndex, endIndex),
            startIndex = startIndex,
            endIndex = endIndex
        )
        startIndex = endIndex
    }
    return pages
}

private fun findMeasuredPageEnd(
    content: String,
    startIndex: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int,
    maxHeightPx: Int
): Int {
    fun fits(endIndex: Int): Boolean {
        val layout = textMeasurer.measure(
            text = AnnotatedString(content.substring(startIndex, endIndex)),
            style = textStyle,
            overflow = TextOverflow.Clip,
            constraints = Constraints(
                maxWidth = maxWidthPx,
                maxHeight = maxHeightPx
            )
        )
        return !layout.hasVisualOverflow && layout.size.height <= maxHeightPx
    }

    var bestEnd = (startIndex + 1).coerceAtMost(content.length)
    var probeEnd = (startIndex + INITIAL_PAGE_PROBE_CHARS).coerceAtMost(content.length)
    while (probeEnd <= content.length && fits(probeEnd)) {
        bestEnd = probeEnd
        if (probeEnd == content.length) {
            return content.length
        }
        val currentSpan = (probeEnd - startIndex).coerceAtLeast(1)
        probeEnd = (startIndex + currentSpan * 2).coerceAtMost(content.length)
    }

    var low = (bestEnd + 1).coerceAtMost(content.length)
    var high = probeEnd
    while (low <= high) {
        val mid = (low + high) / 2
        if (fits(mid)) {
            bestEnd = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return bestEnd
}

private fun refinePageEnd(
    content: String,
    startIndex: Int,
    measuredEnd: Int
): Int {
    if (measuredEnd >= content.length) {
        return content.length
    }
    val pageLength = measuredEnd - startIndex
    if (pageLength < MIN_PAGE_CHARS_FOR_BOUNDARY) {
        return measuredEnd
    }

    val searchStart = startIndex + (pageLength * PAGE_BOUNDARY_SEARCH_RATIO).toInt()
    for (index in measuredEnd - 1 downTo searchStart) {
        if (content[index].isNaturalPageBoundary()) {
            return index + 1
        }
    }
    return measuredEnd
}

private fun Char.isNaturalPageBoundary(): Boolean {
    return this == '\n' ||
        this == '\r' ||
        this == '。' ||
        this == '！' ||
        this == '？' ||
        this == '；' ||
        this == '.' ||
        this == '!' ||
        this == '?' ||
        this == ';'
}

private fun TxtReaderState.toPageIndicatorText(): String {
    return if (pageCount == 0) {
        "- / -"
    } else {
        "$currentPageNumber / $pageCount"
    }
}

private val ReaderBackgroundColor = androidx.compose.ui.graphics.Color(0xFFF4F0E8)
private val ReaderPaperColor = androidx.compose.ui.graphics.Color(0xFFFFFCF5)
private val ReaderTextColor = androidx.compose.ui.graphics.Color(0xFF1F1B16)
private val ReaderSecondaryTextColor = androidx.compose.ui.graphics.Color(0xFF7C7468)
private val ReaderBorderColor = androidx.compose.ui.graphics.Color(0xFFE3D9C9)
private val ReaderDisabledColor = androidx.compose.ui.graphics.Color(0xFFE8E0D5)

private const val INITIAL_PAGE_PROBE_CHARS = 700
private const val MIN_PAGE_CHARS_FOR_BOUNDARY = 120
private const val PAGE_BOUNDARY_SEARCH_RATIO = 0.72f
private val TXT_READER_SWIPE_THRESHOLD_DP = 72.dp
private const val TXT_READER_CONTROLS_AUTO_HIDE_MS = 3_500L
