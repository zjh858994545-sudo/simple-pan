package com.example.simple_pan.ui.home

import com.example.simple_pan.domain.model.RecentRecord

// [语法] data class 相当于 Java 的 POJO/Bean，Kotlin 会自动生成 equals/hashCode/toString/copy。
// [设计] 为什么这样写：首页用单一 State 收敛个人信息、空间信息和最近记录，Compose 根据 State 重组，不在 UI 里散落数据来源。
data class PanHomeState(
    val userName: String = "SimplePan 用户",
    val usedBytes: Long = 0L,
    val fileCount: Int = 0,
    val recentOpen: List<RecentRecord> = emptyList(),
    val recentTransfer: List<RecentRecord> = emptyList(),
    val isLoading: Boolean = true,
    // [语法] String? 表示可空字符串，相当于 Java 的 @Nullable String；没有错误时用 null。
    val errorMessage: String? = null
)

// [语法] sealed interface 类似 Java 里受限的接口/抽象父类，所有实现类型都在编译期可知。
// [设计] 为什么这样写：即使第 8 步只有重试动作，也先保持 MVI 入口，后续刷新、查看全部等行为可以自然加进来。
sealed interface PanHomeIntent {
    // [语法] data object 是 Kotlin 单例对象，类似 Java enum 的单个值，不需要额外字段。
    // [设计] 为什么这样写：Retry 由 UI 发送给 ViewModel，UI 不直接调用 Repository，保持单向数据流。
    data object Retry : PanHomeIntent
}
