package com.example.simple_pan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// [设计] 为什么这样写：分享快照单独成表，是为了让分享页展示“分享那一刻”的文件信息，不被原文件后续重命名、移动或软删除影响。
@Entity(
    tableName = "share_file_snapshot_entity",
    foreignKeys = [
        ForeignKey(
            entity = ShareEntity::class,
            parentColumns = ["share_id"],
            childColumns = ["share_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["share_id"]),
        Index(value = ["source_file_id"])
    ]
)
// [语法] data class 相当于 Java 的 POJO/Bean，Room 可以直接根据构造函数参数读写列。
// [设计] 为什么这样写：快照记录保存的是展示和转存所需的最小文件信息，不把完整 FileEntity 暴露给分享链路，符合“链接不泄露明文”的设计。
data class ShareFileSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "share_id")
    val shareId: String,
    // [语法] String? 表示这个 ID 可以为空，相当于 Java 的 @Nullable String。
    // [设计] 为什么这样写：这里只保存内部追踪用的原文件 ID，不建立外键；分享页要靠快照稳定展示，不能被原文件重命名或软删除牵着走。
    @ColumnInfo(name = "source_file_id")
    val sourceFileId: String?,
    val name: String,
    val type: String,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "relative_path")
    val relativePath: String?,
    // [设计] 为什么这样写：local_path 只在本机可访问时存在；未来服务端分享可能只有快照元信息，没有本地路径。
    @ColumnInfo(name = "local_path")
    val localPath: String?
)
