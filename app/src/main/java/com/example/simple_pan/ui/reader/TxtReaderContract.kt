package com.example.simple_pan.ui.reader

// [语法] data class 相当于 Java 的 POJO/Bean，适合承载不可变 UI 状态。
// [设计] 为什么这样写：阅读器页用一个 State 同时表达标题、正文、加载和错误，Compose 只根据 State 渲染，不直接读文件。
data class TxtReaderState(
    val fileId: String = "",
    val fileName: String = "",
    val content: String = "",
    val isLoading: Boolean = true,
    // [语法] String? 表示可空字符串，相当于 Java 的 @Nullable String；没有错误时用 null。
    // [设计] 为什么这样写：读取失败需要在正文区域展示错误，成功或加载中则保持 null，避免额外布尔状态互相冲突。
    val errorMessage: String? = null
)

// [语法] sealed interface 表示受限事件类型，类似 Java 里固定子类集合的抽象父类型。
// [设计] 为什么这样写：阅读器用户动作统一收敛成 Intent，后续分页、滑动翻页可以继续添加事件，不把逻辑散在 Composable 中。
sealed interface TxtReaderIntent {
    // [语法] data class 用来携带读取目标参数，相当于 Java 里一个只读事件对象。
    // [设计] 为什么这样写：fileId 用于真正读取文件，fallbackFileName 用于读取期间先展示标题，提升页面进入时的稳定感。
    data class LoadFile(
        val fileId: String,
        val fallbackFileName: String
    ) : TxtReaderIntent
}
