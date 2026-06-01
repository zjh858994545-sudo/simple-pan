package com.example.simple_pan.domain.model

// [语法] sealed interface 表示受限结果类型，类似 Java 里固定子类集合的抽象父类型。
// [设计] 为什么这样写：保存分享有成功、缺 token、分享不存在、快照为空、失败几类结果，结构化返回能让 ViewModel 显示准确状态。
sealed interface SaveShareResult {
    // [语法] data class 相当于 Java 的只读结果 Bean，用来携带保存成功后新生成的文件列表。
    // [设计] 为什么这样写：保存成功后 UI 需要知道保存了多少项，首页最近转存则由 Room 历史表自动刷新。
    data class Saved(val files: List<CloudFile>) : SaveShareResult

    // [语法] object 是 Kotlin 单例，适合表达没有额外字段的固定失败原因。
    // [设计] 为什么这样写：DeepLink 或路由缺 token 时不能查询分享，单独建模便于 UI 显示明确提示。
    object MissingToken : SaveShareResult

    // [设计] 为什么这样写：用户可能粘贴过期或不存在的分享链接，保存前需要重新确认分享仍存在。
    object ShareNotFound : SaveShareResult

    // [设计] 为什么这样写：分享记录存在但快照为空属于数据异常，单独结果比泛化失败更容易定位。
    object EmptySnapshot : SaveShareResult

    // [设计] 为什么这样写：数据库事务、层级重建等未知异常保留 message，便于开发阶段定位问题。
    data class Failed(val message: String?) : SaveShareResult
}
