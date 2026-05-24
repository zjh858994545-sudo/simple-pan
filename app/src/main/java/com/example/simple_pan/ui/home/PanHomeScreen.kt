package com.example.simple_pan.ui.home

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

// [设计] 为什么这样写：第 6 步只验证导航和 Tab 切换，首页真实最近浏览/最近转存数据流留到第 8 步，避免提前跨步骤写业务。
@Composable
fun PanHomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "网盘",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "首页骨架将在第 8 步接入最近浏览与最近转存",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
