package com.example.simple_pan.domain.repository

import com.example.simple_pan.domain.model.ShareBundle
import kotlinx.coroutines.flow.Flow

// [设计] 为什么这样写：分享链路通过 token 找本地快照，Repository 把 share_entity 和 snapshot 表聚合成领域对象，UI 不需要知道拆表细节。
interface ShareRepository {
    // [语法] Flow<ShareBundle?> 表示分享可能不存在，类似 Java Observable<Optional<ShareBundle>>。
    // [设计] 为什么这样写：分享页观察 token 对应内容，后续分享过期或删除时 UI 可以自然进入错误/空状态。
    fun observeShareBundle(token: String): Flow<ShareBundle?>

    // [语法] suspend fun 是协程函数，适合保存分享到网盘这种事务流程里做一次性查询。
    // [设计] 为什么这样写：保存分享前通常只需要当前快照，不需要持续观察，所以提供一次性读取入口。
    suspend fun findShareBundle(token: String): ShareBundle?
}
