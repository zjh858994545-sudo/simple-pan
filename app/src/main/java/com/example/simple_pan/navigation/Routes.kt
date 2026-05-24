package com.example.simple_pan.navigation

// [语法] object 是 Kotlin 单例，相当于 Java 里 private 构造函数 + static INSTANCE。
// [设计] 为什么这样写：路由字符串集中管理，避免 Navigation 和底部 Tab 各写一份字符串导致跳转拼错。
object Routes {
    const val HOME = "home"
    const val FILES = "files"
}
