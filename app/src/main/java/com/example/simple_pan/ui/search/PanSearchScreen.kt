package com.example.simple_pan.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.simple_pan.ui.component.WukongPageBackground
import kotlinx.coroutines.delay

// [设计] 为什么这样写：搜索页先还原参考图的输入结构，真实搜索结果后续再接 Repository 查询，避免本次 UI 还原牵动数据层。
@Composable
fun PanSearchScreen(onBackClick: () -> Unit) {
    var keyword by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // [语法] LaunchedEffect 会在页面进入组合后执行一次；这里用于自动聚焦输入框并弹出键盘。
    // [设计] 为什么这样写：参考图进入搜索页时键盘已弹出，延迟一小段时间可以等 TextField 完成布局，避免部分设备过早 requestFocus。
    LaunchedEffect(Unit) {
        delay(120L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

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
                    onValueChange = { input -> keyword = input },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "搜索网盘文件",
                            color = Color(0xFF999999)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF0F0F0),
                        unfocusedContainerColor = Color(0xFFF0F0F0),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
                TextButton(onClick = { }) {
                    Text(
                        text = "搜索",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (keyword.isBlank()) {
                        "输入关键词搜索网盘文件"
                    } else {
                        "暂无搜索结果"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF9A9A9A)
                )
            }
        }
    }
}
