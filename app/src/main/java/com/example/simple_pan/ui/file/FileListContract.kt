package com.example.simple_pan.ui.file

import com.example.simple_pan.domain.model.CloudFile

// [语法] data class 相当于 Java 的 POJO/Bean，Kotlin 会自动生成 equals/hashCode/toString/copy。
// [设计] 为什么这样写：文件列表页用一个不可变 State 表达 loading、empty、error 和列表数据，Compose 只根据 State 重组，不在 UI 里手动刷新。
data class FileListState(
    val files: List<CloudFile> = emptyList(),
    val isLoading: Boolean = true,
    // [语法] String? 表示可空字符串，相当于 Java 的 @Nullable String；没有错误时用 null 表示。
    val errorMessage: String? = null,
    val initializedFromMock: Boolean = false
)

// [语法] sealed interface 类似 Java 里“受限的接口/抽象父类”，实现类型只能在编译期确定，when 分支更安全。
// [设计] 为什么这样写：用户行为统一收敛成 Intent，后续加入进入文件夹、筛选、排序时不会把点击逻辑散落到 Composable。
sealed interface FileListIntent {
    // [语法] data object 是 Kotlin 单例对象，类似 Java enum 单例值；这里不需要额外字段。
    // [设计] 为什么这样写：Retry 表示用户要求重新初始化并观察列表，UI 不直接调用 Repository。
    data object Retry : FileListIntent
}
