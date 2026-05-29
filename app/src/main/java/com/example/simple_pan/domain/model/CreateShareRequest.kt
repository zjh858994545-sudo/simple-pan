package com.example.simple_pan.domain.model

// [语法] data class 相当于 Java 的请求 Bean，自动提供 copy/toString 等方法。
// [设计] 为什么这样写：创建分享需要标题、类型和快照列表，集中成请求对象后 Repository 接口不会随着参数增加而变长。
data class CreateShareRequest(
    val title: String,
    val shareType: ShareType,
    val snapshotFiles: List<ShareSnapshotFile>,
    val expiredAt: Long? = null,
    val ownerMask: String? = null
)
