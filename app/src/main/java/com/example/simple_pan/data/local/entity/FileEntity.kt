package com.example.simple_pan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// [设计] 为什么这样写：文件表是本地网盘的单一数据源，文件列表、上传、移动、删除和分享都围绕这张表组织，UI 后续只观察 Room 而不是写死数据。
@Entity(
    tableName = "file_entity",
    indices = [
        Index(value = ["parent_id"]),
        Index(value = ["type"]),
        Index(value = ["is_deleted"]),
        Index(value = ["parent_id", "name"])
    ]
)
// [语法] data class 相当于 Java 的 POJO/Bean，Kotlin 会自动生成 equals、hashCode、toString 和 copy，适合表达一行不可变数据库记录。
// [设计] 为什么这样写：Room Entity 保持为纯数据结构，避免混入业务逻辑；Repository 负责事务和规则，Entity 只负责持久化字段。
data class FileEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_id")
    val fileId: String,
    // [语法] String? 表示这个字段可以为 null，相当于 Java 的 @Nullable String；根目录文件没有父目录，所以这里必须允许为空。
    @ColumnInfo(name = "parent_id")
    val parentId: String?,
    val name: String,
    val type: String,
    // [设计] 为什么这样写：MIME、私有路径和来源 URI 只对部分文件有意义，mock 文件或文件夹不强制伪造这些值。
    @ColumnInfo(name = "mime_type")
    val mimeType: String?,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "local_path")
    val localPath: String?,
    @ColumnInfo(name = "original_uri")
    val originalUri: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "opened_at")
    val openedAt: Long?,
    @ColumnInfo(name = "transferred_at")
    val transferredAt: Long?,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean,
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean,
    val source: String
) {
    // [语法] companion object 相当于 Java 里的 static 常量区域，调用方可以用 FileEntity.TYPE_FOLDER 访问。
    // [设计] 为什么这样写：数据库里存字符串而不是散落魔法值，后续 DTO、Repository、筛选逻辑都复用同一组常量，降低拼写错误风险。
    companion object {
        const val TYPE_FOLDER = "folder"
        const val TYPE_VIDEO = "video"
        const val TYPE_TXT = "txt"
        const val TYPE_IMAGE = "image"
        const val TYPE_AUDIO = "audio"
        const val TYPE_OTHER = "other"

        const val SOURCE_MOCK = "mock"
        const val SOURCE_UPLOAD = "upload"
        const val SOURCE_SHARE_SAVE = "share_save"
    }
}
