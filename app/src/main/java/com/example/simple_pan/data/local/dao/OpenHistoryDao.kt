package com.example.simple_pan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.simple_pan.data.local.entity.OpenHistoryEntity
import kotlinx.coroutines.flow.Flow

// [设计] 为什么这样写：浏览历史 DAO 独立存在，首页最近浏览和文件详情历史都从同一张行为表读取，不污染文件表职责。
@Dao
interface OpenHistoryDao {
    // [语法] Flow<List<OpenHistoryEntity>> 类似 Java 的 Observable<List<OpenHistoryEntity>>，数据库变化会自动推送新列表。
    // [设计] 为什么这样写：首页只关心最近几条，limit 由调用方控制，避免 UI 层拿全量历史再裁剪。
    @Query("SELECT * FROM open_history_entity ORDER BY opened_at DESC LIMIT :limit")
    fun observeRecentOpenHistory(limit: Int): Flow<List<OpenHistoryEntity>>

    // [设计] 为什么这样写：按文件查看历史为阅读进度、打开次数等后续能力留接口，不需要改表结构。
    @Query("SELECT * FROM open_history_entity WHERE file_id = :fileId ORDER BY opened_at DESC")
    fun observeHistoryForFile(fileId: String): Flow<List<OpenHistoryEntity>>

    // [语法] suspend fun 是协程挂起函数，调用时不会阻塞线程，类似 Java Future 但更适合顺序写异步流程。
    // [设计] 为什么这样写：打开 TXT/视频时需要快速追加一条历史，返回 rowId 方便测试或日志确认写入成功。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: OpenHistoryEntity): Long

    // [设计] 为什么这样写：批量插入留给 mock 或迁移场景使用，避免后续为了初始化历史再扩 DAO。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<OpenHistoryEntity>)

    // [设计] 为什么这样写：虽然主策略是软删除文件，但历史清理接口能服务未来“彻底清理”或测试重置场景。
    @Query("DELETE FROM open_history_entity WHERE file_id = :fileId")
    suspend fun deleteForFile(fileId: String): Int
}
