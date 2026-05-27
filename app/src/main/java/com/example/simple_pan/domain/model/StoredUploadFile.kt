package com.example.simple_pan.domain.model

// [语法] data class 相当于 Java 的 POJO/Bean，Kotlin 会自动生成 equals/hashCode/toString/copy。
// [设计] 为什么这样写：文件复制到 App 私有目录后，需要把“原始展示信息”和“本地真实路径”一起交给后续上传编排，避免 UseCase 再去依赖 Android File API。
data class StoredUploadFile(
    val displayName: String,
    val fileType: FileType,
    // [语法] String? 表示可空字符串，相当于 Java 的 @Nullable String；部分文件来源可能不给 MIME。
    val mimeType: String?,
    val sizeBytes: Long,
    val localPath: String,
    val originalUri: String
)
