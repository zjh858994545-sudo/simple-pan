package com.example.simple_pan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.simple_pan.navigation.AppNavGraph
import com.example.simple_pan.ui.theme.SimplepanTheme
import dagger.hilt.android.AndroidEntryPoint

// [设计] 为什么这样写：MainActivity 只负责宿主和主题，不直接写页面逻辑；真正的页面切换交给 navigation 层，避免入口类变成“大杂烩”。
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // [语法] setContent { ... } 是尾随 lambda 写法，类似 Java 里把一个回调对象传给 setContent。
        // [设计] 为什么这样写：Compose 根节点只挂主题和导航图，后续新增页面时不需要反复改 Activity。
        setContent {
            SimplepanTheme {
                AppNavGraph()
            }
        }
    }
}
