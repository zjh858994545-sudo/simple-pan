package com.example.simple_pan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// [设计] 为什么这样写：浏览历史独立成表，才能保留“打开过什么、什么时候打开”的行为记录，而不是只在文件表覆盖一个 opened_at。
@Entity(
    tableName = "open_history_entity",
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
        Index(value = ["opened_at"])
    ]
)
// [语法] data class 相当于 Java 的 POJO/Bean，适合做 Room 的行映射对象，不需要手写 getter、setter、equals 和 hashCode。
// [设计] 为什么这样写：历史记录保持追加式数据，首页最近浏览可以直接观察这张表的 Flow，体现 Room 响应式更新链路。
data class OpenHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "file_id")
    val fileId: String,
    @ColumnInfo(name = "opened_at")
    val openedAt: Long,
    // [语法] Long? 表示可空 Long，相当于 Java 的 Long 而不是 long；没有阅读/播放进度时可以保存 null。
    // [设计] 为什么这样写：进度先作为可空冗余字段保留，阶段 5 打开 TXT/视频时可以直接承接阅读或播放位置。
    val progress: Long?
)
