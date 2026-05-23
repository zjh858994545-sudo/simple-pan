package com.example.simple_pan.data.remote.dto

// [语法] data class 相当于 Java 的 POJO/Bean，Kotlin 自动生成 equals/hashCode/toString/copy，适合承载 JSON 解析后的纯数据。
// [设计] 为什么这样写：DTO 表示“远端或 mock 数据长什么样”，不直接复用 Room 的 Entity，避免外部数据格式变化时牵动数据库表结构。
data class FileDto(
    val fileId: String,
    // [语法] String? 表示可以为 null，相当于 Java 里的 @Nullable String；根目录文件没有 parentId。
    val parentId: String?,
    val name: String,
    val type: String,
    // [设计] 为什么这样写：mock 数据里文件夹没有 MIME 和本地路径，字段可空可以真实表达这种缺失，而不是用空字符串伪装。
    val mimeType: String?,
    val sizeBytes: Long,
    val localPath: String?,
    val originalUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val openedAt: Long?,
    val transferredAt: Long?,
    val isDeleted: Boolean,
    val isPinned: Boolean,
    val source: String
)
