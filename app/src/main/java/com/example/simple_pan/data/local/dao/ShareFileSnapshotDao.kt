package com.example.simple_pan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.simple_pan.data.local.entity.ShareFileSnapshotEntity
import kotlinx.coroutines.flow.Flow

// [设计] 为什么这样写：快照 DAO 只围绕 shareId 读写文件快照，确保分享页展示的是固定内容，而不是实时读取原始文件表。
@Dao
interface ShareFileSnapshotDao {
    // [语法] Flow<List<ShareFileSnapshotEntity>> 类似 Java Observable<List<...>>，适合分享页响应快照变化。
    // [设计] 为什么这样写：按 relative_path 和 name 排序，可以让文件夹分享在 UI 上保持稳定顺序，录屏演示更可控。
    @Query("SELECT * FROM share_file_snapshot_entity WHERE share_id = :shareId ORDER BY relative_path COLLATE NOCASE ASC, name COLLATE NOCASE ASC")
    fun observeSnapshotsByShareId(shareId: String): Flow<List<ShareFileSnapshotEntity>>

    // [语法] suspend fun 表示这个查询在协程里执行，不阻塞调用线程。
    // [设计] 为什么这样写：保存分享时需要拿到快照的一次性列表，再在同一个事务里生成我的网盘文件。
    @Query("SELECT * FROM share_file_snapshot_entity WHERE share_id = :shareId ORDER BY relative_path COLLATE NOCASE ASC, name COLLATE NOCASE ASC")
    suspend fun findSnapshotsByShareId(shareId: String): List<ShareFileSnapshotEntity>

    // [设计] 为什么这样写：单条插入用于单文件分享，批量插入用于文件夹或多文件分享，接口分开让调用意图明确。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: ShareFileSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<ShareFileSnapshotEntity>)

    // [设计] 为什么这样写：虽然 share_entity 删除会级联快照，但显式删除接口方便测试和未来“重建快照”场景。
    @Query("DELETE FROM share_file_snapshot_entity WHERE share_id = :shareId")
    suspend fun deleteByShareId(shareId: String): Int
}
