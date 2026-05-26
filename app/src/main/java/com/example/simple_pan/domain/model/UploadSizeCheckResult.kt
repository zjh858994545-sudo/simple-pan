package com.example.simple_pan.domain.model

// [语法] sealed interface 表示“受限的接口继承体系”，when 判断它时编译器能检查分支是否覆盖完整。
// [设计] 为什么这样写：大小校验不只是一句 true/false，UI 后面需要区分“允许上传、文件过大、无法读取大小”三种提示。
sealed interface UploadSizeCheckResult {
    // [语法] object 是 Kotlin 单例对象，适合表达没有额外字段的固定结果。
    // [设计] 为什么这样写：允许上传不需要携带数据，用单例能避免反复创建对象。
    object Allowed : UploadSizeCheckResult

    // [设计] 为什么这样写：文件来源没有返回大小时，不能准确判断是否超过 100MB，单独给 UI 一个可提示的状态。
    object UnknownSize : UploadSizeCheckResult

    // [语法] data class 这里用于携带实际大小和最大允许大小，方便 UI 拼出明确提示。
    // [设计] 为什么这样写：过大错误需要知道“当前多大、上限多大”，把数据放在结果里比让 UI 重新计算更清晰。
    data class TooLarge(
        val sizeBytes: Long,
        val maxBytes: Long
    ) : UploadSizeCheckResult
}
