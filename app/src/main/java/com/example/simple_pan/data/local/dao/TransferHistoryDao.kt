package com.example.simple_pan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.simple_pan.data.local.entity.TransferHistoryEntity
import kotlinx.coroutines.flow.Flow

// [设计] 为什么这样写：转存历史 DAO 单独管理上传和分享保存两类行为，让首页最近转存不是从文件创建时间硬推出来的假数据。
@Dao
interface TransferHistoryDao {
    // [语法] Flow<List<TransferHistoryEntity>> 类似 Java Observable，Room 会在 transfer_history_entity 变化后重新发射。
    // [设计] 为什么这样写：首页最近转存依赖真实行为记录，上传或保存分享成功后可以自动刷新。
    @Query("SELECT * FROM transfer_history_entity ORDER BY transferred_at DESC LIMIT :limit")
    fun observeRecentTransferHistory(limit: Int): Flow<List<TransferHistoryEntity>>

    // [设计] 为什么这样写：按文件追踪转存来源，后续答辩能解释上传和分享保存为什么不混在 file_entity.created_at 里。
    @Query("SELECT * FROM transfer_history_entity WHERE file_id = :fileId ORDER BY transferred_at DESC")
    fun observeHistoryForFile(fileId: String): Flow<List<TransferHistoryEntity>>

    // [语法] suspend fun 是协程函数，相当于 Java 异步 Future 风格，但可以在协程里按顺序调用。
    // [设计] 为什么这样写：转存写入通常和文件插入放在同一个 Repository 事务中，DAO 保持单一写入职责。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: TransferHistoryEntity): Long

    // [设计] 为什么这样写：保存文件夹分享时可能一次生成多条转存记录，批量接口可以减少重复调用。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<TransferHistoryEntity>)

    // [设计] 为什么这样写：为彻底清理和测试重置保留入口；正常删除仍优先软删除文件，不主动抹掉行为记录。
    @Query("DELETE FROM transfer_history_entity WHERE file_id = :fileId")
    suspend fun deleteForFile(fileId: String): Int
}
