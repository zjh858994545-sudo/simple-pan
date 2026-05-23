package com.example.simple_pan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// [设计] 为什么这样写：分享链接只暴露 token，分享标题、类型、过期时间等元信息留在本地数据库，避免链接里出现文件明文信息。
@Entity(
    tableName = "share_entity",
    indices = [
        Index(value = ["token"], unique = true),
        Index(value = ["created_at"])
    ]
)
// [语法] data class 相当于 Java 的 POJO/Bean，天然适合表示“分享表的一行记录”。
// [设计] 为什么这样写：分享元信息和文件快照拆开存，一个分享可以对应一个或多个快照文件，后续扩展文件夹分享和多文件分享更自然。
data class ShareEntity(
    @PrimaryKey
    @ColumnInfo(name = "share_id")
    val shareId: String,
    val token: String,
    val title: String,
    @ColumnInfo(name = "share_type")
    val shareType: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    // [语法] Long? 和 String? 都表示可空字段，相当于 Java 里可能为 null 的包装类型或引用类型。
    // [设计] 为什么这样写：过期时间和脱敏用户信息不是阶段 1 必需，但提前建成可空字段，后续服务端拓展时不用破坏表语义。
    @ColumnInfo(name = "expired_at")
    val expiredAt: Long?,
    @ColumnInfo(name = "owner_mask")
    val ownerMask: String?
) {
    // [语法] companion object 相当于 Java static 常量区域。
    // [设计] 为什么这样写：分享类型会影响快照生成和 UI 文案，集中定义能防止业务层散落字符串。
    companion object {
        const val TYPE_SINGLE_FILE = "single_file"
        const val TYPE_FOLDER = "folder"
        const val TYPE_MULTI_FILE = "multi_file"
    }
}
