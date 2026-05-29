package com.example.simple_pan.domain.model

// [语法] data class 相当于 Java 的 POJO/Bean，适合表达一条不可变的分享文件快照。
// [设计] 为什么这样写：分享页必须展示“分享那一刻”的文件信息，而不是实时读取原文件；快照模型能保留名称、类型、大小和层级路径。
data class ShareSnapshotFile(
    val snapshotId: Long = 0L,
    // [语法] String? 表示可空字符串，相当于 Java 的 @Nullable String。
    // [设计] 为什么这样写：sourceFileId 只用于本机追踪和后续保存时尽量复用本地文件，分享链接本身永远不暴露它。
    val sourceFileId: String?,
    val name: String,
    val type: FileType,
    val sizeBytes: Long,
    val relativePath: String?,
    val localPath: String?
)
