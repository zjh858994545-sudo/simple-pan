package com.example.simple_pan.domain.model

// [语法] sealed interface 表示受限结果类型，类似 Java 里固定子类集合的抽象父类型。
// [设计] 为什么这样写：创建分享有“成功、未选择、文件失效、失败”几类可解释结果，结构化返回比只抛异常更适合后续 ViewModel 转 UI 提示。
sealed interface CreateShareResult {
    // [语法] data class 相当于 Java 的只读结果 Bean，用来携带创建成功后的分享聚合对象。
    // [设计] 为什么这样写：创建成功后后续步骤需要 token、标题和快照列表生成分享文案，所以直接返回 ShareBundle。
    data class Created(val shareBundle: ShareBundle) : CreateShareResult

    // [语法] object 是 Kotlin 单例，适合表达没有额外字段的固定失败原因。
    // [设计] 为什么这样写：管理态没有选中文件时不能创建分享，单独建模能让 UI 显示明确提示。
    object EmptySelection : CreateShareResult

    // [设计] 为什么这样写：选中的文件可能在创建分享前被删除或软删除，单独结果能提示用户重新选择。
    object NoActiveFiles : CreateShareResult

    // [设计] 为什么这样写：数据库写入、token 生成等未知异常保留 message，便于开发阶段定位问题。
    data class Failed(val message: String?) : CreateShareResult
}
