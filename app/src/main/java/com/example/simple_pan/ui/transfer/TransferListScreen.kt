package com.example.simple_pan.ui.transfer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.simple_pan.ui.component.WukongEmptyState
import com.example.simple_pan.ui.component.WukongPageBackground
import com.example.simple_pan.ui.component.WukongSegmentedTabs

// [设计] 为什么这样写：传输页是参考图里的独立二级页，先展示上传/下载和状态筛选，真实队列后续再接 transfer_history 或任务表。
@Composable
fun TransferListScreen(
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var selectedTopTab by remember { mutableIntStateOf(0) }
    var selectedStatus by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WukongPageBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
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
                TransferTopTabText(
                    text = "上传",
                    selected = selectedTopTab == 0,
                    onClick = {
                        selectedTopTab = 0
                        selectedStatus = 0
                    }
                )
                Spacer(modifier = Modifier.width(28.dp))
                TransferTopTabText(
                    text = "下载",
                    selected = selectedTopTab == 1,
                    onClick = {
                        selectedTopTab = 1
                        selectedStatus = 0
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onSettingsClick) {
                    Text(
                        text = "⚙",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.Black
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            WukongSegmentedTabs(
                items = if (selectedTopTab == 0) {
                    listOf("全部", "已完成 0", "上传中 0", "失败 0")
                } else {
                    listOf("全部", "已完成 0", "下载中 0", "失败 0")
                },
                selectedIndex = selectedStatus,
                onSelected = { index -> selectedStatus = index }
            )
            WukongEmptyState(
                modifier = Modifier.fillMaxSize(),
                text = "暂无更多内容"
            )
        }
    }
}

// [设计] 为什么这样写：顶部上传/下载 Tab 只用字重和颜色区分选中态，贴近参考图的轻量切换方式。
@Composable
private fun TransferTopTabText(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = if (selected) Color.Black else Color(0xFF8F8F8F),
            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal
        )
    }
}
