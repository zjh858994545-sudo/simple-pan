package com.example.simple_pan.domain.usecase

import com.example.simple_pan.domain.model.LocalFileMetadata
import com.example.simple_pan.domain.model.UploadSizeCheckResult
import javax.inject.Inject

// [设计] 为什么这样写：100MB 是上传业务规则，不属于 UI，也不属于 Room；放在领域层后，按钮、UseCase 和测试都能复用同一套判断。
class UploadSizePolicy @Inject constructor() {
    // [设计] 为什么这样写：调用方有时只有 size，有时已经拿到了 LocalFileMetadata；保留 Long 入口可以让规则更独立。
    fun validate(sizeBytes: Long): UploadSizeCheckResult {
        return when {
            sizeBytes < 0L -> UploadSizeCheckResult.UnknownSize
            sizeBytes > MAX_UPLOAD_SIZE_BYTES -> {
                UploadSizeCheckResult.TooLarge(
                    sizeBytes = sizeBytes,
                    maxBytes = MAX_UPLOAD_SIZE_BYTES
                )
            }
            else -> UploadSizeCheckResult.Allowed
        }
    }

    // [设计] 为什么这样写：给已经读取完元信息的调用方一个直观入口，避免在 UI 或 UseCase 里反复取 metadata.sizeBytes。
    fun validate(metadata: LocalFileMetadata): UploadSizeCheckResult {
        return validate(metadata.sizeBytes)
    }

    // [语法] companion object 相当于 Java 的 static 工具区，调用方可以用 UploadSizePolicy.MAX_UPLOAD_SIZE_BYTES。
    // [设计] 为什么这样写：阈值统一放在策略类旁边，后面 UI 展示和测试都不会各自写一份 100MB。
    companion object {
        const val MAX_UPLOAD_SIZE_BYTES: Long = 100L * 1024L * 1024L
    }
}
