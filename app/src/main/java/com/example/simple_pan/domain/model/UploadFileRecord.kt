package com.example.simple_pan.domain.model

// [语法] data class 相当于 Java 的 POJO/Bean，Kotlin 会自动生成 equals/hashCode/toString/copy。
// [设计] 为什么这样写：上传流程前几步会从 SAF 和私有目录复制得到这些字段；用领域模型承载“待入库文件”，避免 UI 或 UseCase 直接拼 Room Entity。
data class UploadFileRecord(
    val fileId: String,
    // [语法] String? 表示可空字符串，相当于 Java 的 @Nullable String；上传到根目录时 parentId 为 null。
    val parentId: String?,
    val name: String,
    val type: FileType,
    val mimeType: String?,
    val sizeBytes: Long,
    val localPath: String,
    val originalUri: String?,
    val createdAt: Long
)
