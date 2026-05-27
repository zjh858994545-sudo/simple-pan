package com.example.simple_pan.domain.model

// [语法] sealed interface 表示“受限的接口继承体系”，类似 Java 里固定子类集合的抽象类型。
// [设计] 为什么这样写：复制文件会遇到大小拦截、源文件不可读、私有目录不可写等情况，用结构化结果比抛异常更适合给 UI 展示明确提示。
sealed interface UploadFileCopyResult {
    // [语法] data class 这里用于携带复制成功后的私有目录文件信息。
    // [设计] 为什么这样写：成功结果必须把 localPath 和实际复制大小交给后续入库流程，不能只返回 true。
    data class Copied(val file: StoredUploadFile) : UploadFileCopyResult

    // [设计] 为什么这样写：复制器会再次执行大小策略，防止调用方漏校验或文件提供者返回的大小与实际流大小不一致。
    data class RejectedBySize(val reason: UploadSizeCheckResult) : UploadFileCopyResult

    // [语法] object 是 Kotlin 单例对象，适合表达没有额外字段的固定失败原因。
    // [设计] 为什么这样写：ContentResolver 打不开输入流时，通常是 Uri 失效或权限不足，后面 UI 可以给出重新选择文件的提示。
    object SourceUnavailable : UploadFileCopyResult

    // [设计] 为什么这样写：私有目录创建失败属于 App 存储环境问题，单独建模能和普通读取失败区分开。
    object StorageUnavailable : UploadFileCopyResult

    // [设计] 为什么这样写：保留兜底失败原因，避免未知 IOException 直接崩溃，同时不把 data 层异常类型泄漏到 UI。
    data class Failed(val message: String?) : UploadFileCopyResult
}
