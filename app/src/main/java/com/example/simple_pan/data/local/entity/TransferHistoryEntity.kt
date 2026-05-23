package com.example.simple_pan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// [设计] 为什么这样写：转存历史独立成表，可以把“上传本地文件”和“保存分享文件”统一展示在首页最近转存里，同时保留来源差异。
@Entity(
    tableName = "transfer_history_entity",
    foreignKeys = [
        ForeignKey(
            entity = FileEntity::class,
            parentColumns = ["file_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_id"]),
        Index(value = ["transferred_at"]),
        Index(value = ["transfer_type"])
    ]
)
// [语法] data class 相当于 Java 的 POJO/Bean，Kotlin 会自动生成常用数据方法，Room 也能直接用主构造函数映射字段。
// [设计] 为什么这样写：这张表描述用户行为，不描述文件本身，避免把文件创建时间误当成转存时间。
data class TransferHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "file_id")
    val fileId: String,
    @ColumnInfo(name = "transfer_type")
    val transferType: String,
    // [语法] String? 是 Kotlin 空安全写法，表示分享 token 可以不存在；上传本地文件时没有分享来源。
    @ColumnInfo(name = "share_token")
    val shareToken: String?,
    @ColumnInfo(name = "transferred_at")
    val transferredAt: Long
) {
    // [语法] companion object 相当于 Java static 常量容器，避免到处 new 工具类。
    // [设计] 为什么这样写：转存来源必须可解释，集中常量后，首页、Repository 和测试都能使用同一套取值。
    companion object {
        const val TYPE_UPLOAD = "upload"
        const val TYPE_SHARE_SAVE = "share_save"
    }
}
