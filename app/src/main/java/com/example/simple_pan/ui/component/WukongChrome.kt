package com.example.simple_pan.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// [设计] 为什么这样写：截图里的网盘没有底部 Tab，而是在顶部用“网盘 / 文件”切换；抽成公共组件保证首页和文件页一致。
@Composable
fun WukongTopTabs(
    selectedTab: WukongTopTab,
    onPanClick: () -> Unit,
    onFileClick: () -> Unit,
    onBackClick: () -> Unit,
    onTransferClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBackClick) {
            Text(
                text = "<",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        WukongTopTabText(
            text = "网盘",
            selected = selectedTab == WukongTopTab.Pan,
            onClick = onPanClick
        )
        Spacer(modifier = Modifier.width(12.dp))
        WukongTopTabText(
            text = "文件",
            selected = selectedTab == WukongTopTab.File,
            onClick = onFileClick
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onTransferClick) {
            Text(
                text = "⇅",
                style = MaterialTheme.typography.titleLarge,
                color = Color.Black
            )
        }
        TextButton(onClick = onSearchClick) {
            Text(
                text = "⌕",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black
            )
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
    TextButton(onClick = onClick) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = if (selected) Color.Black else Color(0xFF8A8A8A),
            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal
        )
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
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for ((index, item) in items.withIndex()) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = if (index == selectedIndex) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = if (index == selectedIndex) 1.dp else 0.dp,
                    onClick = { onSelected(index) }
                ) {
                    Text(
                        modifier = Modifier.padding(vertical = 12.dp),
                        text = item,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (index == selectedIndex) Color.Black else Color(0xFF777777),
                        fontWeight = if (index == selectedIndex) FontWeight.Black else FontWeight.Normal
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
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(112.dp),
                color = Color(0xFFE9ECEF),
                contentColor = Color(0xFFB8BEC7),
                shape = RoundedCornerShape(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "SP",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF9A9A9A)
            )
        }
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
                fontWeight = FontWeight.Black,
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
