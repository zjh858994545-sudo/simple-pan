package com.example.simple_pan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.simple_pan.data.local.entity.ShareEntity
import kotlinx.coroutines.flow.Flow

// [设计] 为什么这样写：分享元信息和快照分两个 DAO，能清楚表达 token 查询分享、再按 shareId 查询文件快照的链路。
@Dao
interface ShareDao {
    // [语法] Flow<ShareEntity?> 类似 Java 的 Observable<Optional<ShareEntity>>，? 表示 token 可能查不到分享。
    // [设计] 为什么这样写：分享页打开后观察 token 对应记录，分享被删除或过期处理时 UI 可以自动进入空/错误状态。
    @Query("SELECT * FROM share_entity WHERE token = :token LIMIT 1")
    fun observeShareByToken(token: String): Flow<ShareEntity?>

    // [语法] suspend fun 是协程挂起函数，适合在 Repository 事务中像同步代码一样查询。
    // [设计] 为什么这样写：保存分享到我的网盘时需要一次性读取分享元信息，找不到就由 Repository 转成业务错误。
    @Query("SELECT * FROM share_entity WHERE token = :token LIMIT 1")
    suspend fun findShareByToken(token: String): ShareEntity?

    // [设计] 为什么这样写：shareId 是内部主键，token 是外部入口；两个查询分开能避免把外部链接概念泄漏到内部逻辑。
    @Query("SELECT * FROM share_entity WHERE share_id = :shareId LIMIT 1")
    suspend fun findShareById(shareId: String): ShareEntity?

    // [设计] 为什么这样写：token 需要唯一，插入前可以先查冲突并重新生成，便于把“随机 token 冲突”讲清楚。
    @Query("SELECT COUNT(*) FROM share_entity WHERE token = :token")
    suspend fun countByToken(token: String): Int

    // [设计] 为什么这样写：分享 token 冲突不能静默覆盖旧分享，所以用 ABORT 暴露错误，让 Repository 重新生成 token。
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(share: ShareEntity)

    // [设计] 为什么这样写：删除分享时让外键级联清理快照，保证 share_entity 和 share_file_snapshot_entity 不出现孤儿数据。
    @Query("DELETE FROM share_entity WHERE share_id = :shareId")
    suspend fun deleteById(shareId: String): Int
}
