package com.example.simple_pan.ui.file

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// [设计] 为什么这样写：第 6 步只建立“文件”这个导航入口，真实 Room 文件列表展示留到第 7 步，保持一步一个可验证目标。
@Composable
fun FileListScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "文件",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "文件列表将在第 7 步从 Room 读取展示",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
