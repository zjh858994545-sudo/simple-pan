package com.example.simple_pan.domain.model

// [语法] data class 相当于 Java 的 POJO/Bean，自动提供 copy 等方法，适合表达分享页的一组快照数据。
// [设计] 为什么这样写：分享页需要分享元信息和文件快照一起展示，ShareBundle 把它们聚合起来，但仍不暴露数据库 Entity。
data class ShareBundle(
    val shareId: String,
    val token: String,
    val title: String,
    val shareType: ShareType,
    val snapshotFiles: List<ShareSnapshotFile>,
    val createdAt: Long,
    val expiredAt: Long?,
    val ownerMask: String?
)
