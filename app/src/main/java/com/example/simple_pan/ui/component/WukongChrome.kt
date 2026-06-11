package com.example.simple_pan.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val WukongTopBarHeight = 72.dp
internal val WukongTopBarContentTopPadding = 18.dp
internal val WukongTopTitleFontSize = 20.sp
internal val WukongTopTitleLineHeight = 25.sp

// [设计] 为什么这样写：截图里的网盘没有底部 Tab，而是在顶部用“网盘 / 文件”切换；抽成公共组件保证首页和文件页一致。
@Composable
fun WukongTopTabs(
    selectedTab: WukongTopTab,
    onPanClick: () -> Unit,
    onFileClick: () -> Unit,
    onBackClick: () -> Unit,
    onTransferClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(WukongTopBarHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = WukongTopBarContentTopPadding)
        ) {
            WukongTopIconButton(
                modifier = Modifier.align(Alignment.CenterStart),
                text = "<",
                onClick = onBackClick
            )
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WukongTopTabText(
                    text = "网盘",
                    selected = selectedTab == WukongTopTab.Pan,
                    onClick = onPanClick
                )
                Spacer(modifier = Modifier.width(10.dp))
                WukongTopTabText(
                    text = "文件",
                    selected = selectedTab == WukongTopTab.File,
                    onClick = onFileClick
                )
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WukongTopIconButton(
                    text = "⇅",
                    onClick = onTransferClick
                )
                WukongTopIconButton(
                    text = "⌕",
                    onClick = onSearchClick
                )
            }
        }
    }
}

// [设计] 为什么这样写：顶部选中态只通过字重和颜色表达，贴近截图里的“选中黑色粗体、未选灰色”。
@Composable
private fun WukongTopTabText(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        modifier = Modifier.height(42.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        onClick = onClick
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = WukongTopTitleFontSize,
                lineHeight = WukongTopTitleLineHeight
            ),
            color = if (selected) Color.Black else Color(0xFF8A8A8A),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// [设计] 为什么这样写：顶部图标按钮统一 48dp 点击区，能让标题真正居中，也更接近截图中的大图标触控范围。
@Composable
fun WukongTopIconButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        modifier = modifier.size(42.dp),
        onClick = onClick
    ) {
        when (text) {
            "<" -> WukongBackGlyph()
            "⇅" -> WukongTransferGlyph()
            "⌕" -> WukongSearchGlyph()
            else -> Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WukongBackGlyph() {
    Canvas(
        modifier = Modifier
            .size(26.dp)
    ) {
        val stroke = size.width * 0.12f
        drawLine(
            color = Color.Black,
            start = Offset(size.width * 0.64f, size.height * 0.18f),
            end = Offset(size.width * 0.34f, size.height * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.Black,
            start = Offset(size.width * 0.34f, size.height * 0.50f),
            end = Offset(size.width * 0.64f, size.height * 0.82f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun WukongSearchGlyph() {
    Canvas(
        modifier = Modifier
            .size(28.dp)
    ) {
        val stroke = size.width * 0.11f
        drawCircle(
            color = Color.Black,
            radius = size.width * 0.30f,
            center = Offset(size.width * 0.43f, size.height * 0.42f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        drawLine(
            color = Color.Black,
            start = Offset(size.width * 0.66f, size.height * 0.66f),
            end = Offset(size.width * 0.88f, size.height * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun WukongTransferGlyph() {
    Canvas(
        modifier = Modifier
            .size(28.dp)
    ) {
        val stroke = size.width * 0.09f
        drawCircle(
            color = Color.Black,
            radius = size.width * 0.39f,
            center = Offset(size.width * 0.50f, size.height * 0.50f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        drawLine(
            color = Color.Black,
            start = Offset(size.width * 0.39f, size.height * 0.30f),
            end = Offset(size.width * 0.39f, size.height * 0.70f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.Black,
            start = Offset(size.width * 0.61f, size.height * 0.30f),
            end = Offset(size.width * 0.61f, size.height * 0.70f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        val upArrow = Path().apply {
            moveTo(size.width * 0.39f, size.height * 0.24f)
            lineTo(size.width * 0.30f, size.height * 0.36f)
            lineTo(size.width * 0.48f, size.height * 0.36f)
            close()
        }
        val downArrow = Path().apply {
            moveTo(size.width * 0.61f, size.height * 0.76f)
            lineTo(size.width * 0.52f, size.height * 0.64f)
            lineTo(size.width * 0.70f, size.height * 0.64f)
            close()
        }
        drawPath(upArrow, Color.Black)
        drawPath(downArrow, Color.Black)
    }
}

// [设计] 为什么这样写：二级页面使用“返回 + 居中标题 + 右侧工具”的固定结构，避免每个页面手写后出现标题偏移。
@Composable
fun WukongTitleTopBar(
    title: String,
    onBackClick: () -> Unit,
    onTransferClick: () -> Unit,
    onSearchClick: () -> Unit,
    showTransferButton: Boolean = true,
    showSearchButton: Boolean = true
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
            WukongTopIconButton(
                modifier = Modifier.align(Alignment.CenterStart),
                text = "<",
                onClick = onBackClick
            )
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = WukongTopTitleFontSize,
                    lineHeight = WukongTopTitleLineHeight
                ),
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showTransferButton) {
                    WukongTopIconButton(
                        text = "⇅",
                        onClick = onTransferClick
                    )
                }
                if (showSearchButton) {
                    WukongTopIconButton(
                        text = "⌕",
                        onClick = onSearchClick
                    )
                }
            }
        }
    }
}

// [设计] 为什么这样写：截图中的分类筛选是大圆角灰底分段控件，当前项目选项较少，用这一版公共组件即可承载。
@Composable
fun WukongSegmentedTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFF0F0F0),
        shape = RoundedCornerShape(26.dp)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for ((index, item) in items.withIndex()) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = if (index == selectedIndex) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(23.dp),
                    shadowElevation = if (index == selectedIndex) 1.dp else 0.dp,
                    onClick = { onSelected(index) }
                ) {
                    Text(
                        modifier = Modifier.padding(vertical = 8.dp),
                        text = item,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            lineHeight = 21.sp
                        ),
                        color = if (index == selectedIndex) Color.Black else Color(0xFF777777),
                        fontWeight = if (index == selectedIndex) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// [设计] 为什么这样写：截图中的空状态是浅灰插画 + 文案；不复制悟空素材，用 SimplePan 占位图表达同一种状态。
@Composable
fun WukongEmptyState(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WukongEmptyIllustration()
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF9A9A9A)
            )
        }
    }
}

// [设计] 为什么这样写：参考图的空状态是浅灰卡通插画；这里用 Compose 基础形状拼出低对比度插画，避免引入新图片依赖。
@Composable
private fun WukongEmptyIllustration() {
    Box(
        modifier = Modifier.size(width = 128.dp, height = 116.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .size(width = 58.dp, height = 76.dp)
                .offset(x = 8.dp, y = 6.dp),
            color = Color(0xFFE1E5EA),
            shape = RoundedCornerShape(18.dp)
        ) {
            Box {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .size(width = 34.dp, height = 18.dp),
                    color = Color.White.copy(alpha = 0.86f),
                    shape = RoundedCornerShape(12.dp)
                ) {}
            }
        }
        Surface(
            modifier = Modifier
                .size(width = 42.dp, height = 34.dp)
                .align(Alignment.BottomStart)
                .offset(x = 24.dp, y = (-8).dp),
            color = Color(0xFFD7DCE3),
            shape = RoundedCornerShape(6.dp)
        ) {}
        Surface(
            modifier = Modifier
                .size(50.dp)
                .align(Alignment.TopCenter)
                .offset(x = 10.dp, y = 8.dp),
            color = Color(0xFFD9DEE5),
            shape = CircleShape
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .align(Alignment.TopStart)
                .offset(x = 28.dp, y = 26.dp)
                .background(Color(0xFFD9DEE5), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .align(Alignment.TopStart)
                .offset(x = 18.dp, y = 38.dp)
                .background(Color(0xFFE3E7EC), CircleShape)
        )
    }
}

// [设计] 为什么这样写：右下角上传按钮按截图做成白色圆形悬浮按钮，比 Material 默认 FAB 更接近参考图。
@Composable
fun WukongPlusButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .size(64.dp)
            .shadow(16.dp, CircleShape),
        color = Color.White,
        shape = CircleShape,
        border = BorderStroke(1.dp, Color(0xFFEFEFEF)),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}

// [设计] 为什么这样写：网盘和文件是顶部栏的两个固定入口，用枚举表达能让调用方更清楚当前选中态。
enum class WukongTopTab {
    Pan,
    File
}

// [设计] 为什么这样写：参考图整体背景接近 #F7F7F7 的浅灰，用公共常量避免多处硬编码颜色。
val WukongPageBackground = Color(0xFFF7F7F7)
