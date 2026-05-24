package com.example.simple_pan.navigation

// [语法] data class 相当于 Java 的 POJO/Bean，Kotlin 会自动生成 equals/hashCode/toString/copy。
// [设计] 为什么这样写：底部 Tab 的 route 和 label 是稳定配置，用一个不可变对象承载，AppNavGraph 不需要散落两组平行数组。
data class TopLevelDestination(
    val route: String,
    val label: String
)

// [设计] 为什么这样写：阶段 1 只需要网盘/文件两个顶层入口，后续分享页、阅读器页是二级页面，不放到底部 Tab。
val topLevelDestinations = listOf(
    TopLevelDestination(route = Routes.HOME, label = "网盘"),
    TopLevelDestination(route = Routes.FILES, label = "文件")
)
