package com.example.simple_pan.domain.model

// [语法] sealed interface 表示“受限的接口继承体系”，类似 Java 里固定子类集合的抽象父类型。
// [设计] 为什么这样写：上传链路横跨 SAF、文件复制和 Room 事务，失败原因需要被 UI 精确区分，不能只用 Boolean 或 Exception 表达。
sealed interface UploadFileResult {
    // [语法] data class 用来携带上传成功后的 CloudFile，相当于 Java 里一个只读结果对象。
    // [设计] 为什么这样写：成功后 UI 不需要手动刷新列表，但仍需要知道本次保存出的文件，方便后续显示提示或定位。
    data class Uploaded(val file: CloudFile) : UploadFileResult

    // [设计] 为什么这样写：过大和未知大小都来自同一套大小策略，保留原始 reason 能让 UI 给出不同提示文案。
    data class RejectedBySize(val reason: UploadSizeCheckResult) : UploadFileResult

    // [语法] object 是 Kotlin 单例对象，适合表达没有额外字段的固定失败原因。
    // [设计] 为什么这样写：目标目录在上传期间可能被删除，单独表达能避免把文件写进失效目录导致列表不可见。
    object TargetFolderUnavailable : UploadFileResult

    // [设计] 为什么这样写：Uri 无法读取通常是权限失效或来源文件不可用，UI 后续可以提示用户重新选择。
    object SourceUnavailable : UploadFileResult

    // [设计] 为什么这样写：App 私有目录不可用属于存储环境问题，和源文件不可读要分开提示。
    object StorageUnavailable : UploadFileResult

    // [设计] 为什么这样写：保留兜底错误，防止未知异常崩溃，同时不把底层异常类型泄漏到 UI。
    data class Failed(val message: String?) : UploadFileResult
}
