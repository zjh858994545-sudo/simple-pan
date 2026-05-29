package com.example.simple_pan.ui.share

import com.example.simple_pan.domain.model.ShareSnapshotFile
import com.example.simple_pan.domain.model.ShareType

// [语法] data class 相当于 Java 的 POJO/Bean，适合承载分享预览页的一组不可变 UI 状态。
// [设计] 为什么这样写：分享预览页需要同时表达 token、标题、文件列表、加载和错误；集中到 State 后，Composable 只负责根据状态渲染。
data class SharePreviewState(
    val token: String = "",
    val title: String = "",
    val shareType: ShareType = ShareType.MultiFile,
    val files: List<ShareSnapshotFile> = emptyList(),
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val errorMessage: String? = null
)

// [语法] sealed interface 表示受限的事件类型，类似 Java 里固定子类集合的抽象父类型。
// [设计] 为什么这样写：分享预览页当前只有加载/重试动作，仍然统一走 Intent，后续“保存到网盘”能自然扩展进同一入口。
sealed interface SharePreviewIntent {
    // [语法] data class 用来携带 token 参数，相当于 Java 里的一个只读事件对象。
    // [设计] 为什么这样写：token 来自导航路由或 DeepLink，ViewModel 用它观察对应分享快照，UI 不直接调用 Repository。
    data class Load(val token: String) : SharePreviewIntent

    // [语法] data object 是 Kotlin 单例对象，类似 Java enum 里的一个固定值。
    // [设计] 为什么这样写：重试使用当前 State 里的 token，不需要 UI 再传一遍，避免按钮层重复保存参数。
    data object Retry : SharePreviewIntent
}
