package com.example.simple_pan.domain.repository

import com.example.simple_pan.domain.model.RecentRecord
import kotlinx.coroutines.flow.Flow

// [设计] 为什么这样写：首页最近浏览和最近转存不应该从 UI 假造，统一通过 RecentRepository 读取真实历史表。
interface RecentRepository {
    // [语法] Flow<List<RecentRecord>> 类似 Java Observable，会随着历史表变化持续发射最新记录。
    // [设计] 为什么这样写：打开 TXT/视频或上传/保存分享后，首页可以自动刷新最近记录。
    fun observeRecentOpen(limit: Int): Flow<List<RecentRecord>>

    fun observeRecentTransfer(limit: Int): Flow<List<RecentRecord>>

    // [语法] suspend fun 表示协程里的异步写入；Java 通常会用 Future 或 callback 表达。
    // [设计] 为什么这样写：记录打开行为需要同时写历史表和更新文件冗余字段，放在 Repository 里保证原子性。
    suspend fun recordOpen(fileId: String, openedAt: Long, progress: Long?)

    // [设计] 为什么这样写：上传和分享保存都算转存，但来源不同；Repository 保留 transferType 和 shareToken 供首页与答辩说明。
    suspend fun recordTransfer(fileId: String, transferType: String, shareToken: String?, transferredAt: Long)
}
