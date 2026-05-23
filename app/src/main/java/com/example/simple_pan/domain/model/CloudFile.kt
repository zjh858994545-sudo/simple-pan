package com.example.simple_pan.domain.model

// [语法] data class 相当于 Java 的 POJO/Bean，自动生成 equals/hashCode/toString/copy，适合表达不可变领域数据。
// [设计] 为什么这样写：UI 和 UseCase 依赖 CloudFile，而不是 Room 的 FileEntity，保证数据库字段变化不会直接冲击上层。
data class CloudFile(
    val fileId: String,
    // [语法] String? 表示可空字符串，相当于 Java 的 @Nullable String；根目录文件没有 parentId。
    val parentId: String?,
    val name: String,
    val type: FileType,
    val mimeType: String?,
    val sizeBytes: Long,
    val localPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val openedAt: Long?,
    val transferredAt: Long?,
    val isPinned: Boolean,
    val source: String
)
