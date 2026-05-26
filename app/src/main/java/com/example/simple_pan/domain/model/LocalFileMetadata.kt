package com.example.simple_pan.domain.model

// [语法] data class 相当于 Java 的 POJO/Bean，Kotlin 会自动生成 equals/hashCode/toString/copy。
// [设计] 为什么这样写：SAF 只给 App 一个 Uri，上传前需要先把“展示名、大小、MIME、推断类型”整理成稳定的领域模型，后续复制文件和写入 Room 都复用它。
data class LocalFileMetadata(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    // [语法] String? 表示可空字符串，相当于 Java 的 @Nullable String；部分文件来源可能不给 MIME。
    val mimeType: String?,
    val fileType: FileType
)
