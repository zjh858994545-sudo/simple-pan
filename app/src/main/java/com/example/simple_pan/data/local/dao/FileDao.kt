package com.example.simple_pan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.simple_pan.data.local.entity.FileEntity
import kotlinx.coroutines.flow.Flow

// [设计] 为什么这样写：DAO 只负责描述数据库访问，不承载业务流程；上传、移动、删除等事务会放到 Repository，避免 UI 或 ViewModel 直接操作 Room。
@Dao
interface FileDao {
    // [语法] Flow<List<FileEntity>> 类似 Java 的 Observable<List<FileEntity>>，Room 表变化时会重新发射最新列表。
    // [设计] 为什么这样写：文件列表必须从 Room 响应式刷新，上传、移动、删除后 ViewModel 不需要手动 refresh。
    @Query(
        """
        SELECT * FROM file_entity
        WHERE is_deleted = 0
          AND ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
        ORDER BY is_pinned DESC,
                 CASE WHEN type = 'folder' THEN 0 ELSE 1 END,
                 updated_at DESC,
                 name COLLATE NOCASE ASC
        """
    )
    fun observeFilesInFolder(parentId: String?): Flow<List<FileEntity>>

    // [语法] Flow<FileEntity?> 里的 ? 表示可能查不到文件，相当于 Java Observable<Optional<FileEntity>> 的效果。
    // [设计] 为什么这样写：文件详情和打开流程需要感知文件被软删除后的空状态，而不是抛异常导致页面崩溃。
    @Query("SELECT * FROM file_entity WHERE file_id = :fileId AND is_deleted = 0 LIMIT 1")
    fun observeFile(fileId: String): Flow<FileEntity?>

    // [语法] suspend fun 是 Kotlin 协程函数，类似 Java 里返回 Future 的异步方法，但写法像同步代码。
    // [设计] 为什么这样写：一次性查询交给 Repository 在 IO 调度器里调用，避免主线程阻塞，也方便事务中组合多个 DAO 操作。
    @Query("SELECT * FROM file_entity WHERE file_id = :fileId AND is_deleted = 0 LIMIT 1")
    suspend fun findActiveFile(fileId: String): FileEntity?

    // [设计] 为什么这样写：移动文件夹前需要查子文件夹做非法移动校验，DAO 提供最小数据入口，规则本身留给 UseCase/Repository。
    @Query("SELECT * FROM file_entity WHERE parent_id = :parentId AND is_deleted = 0 AND type = 'folder'")
    suspend fun findActiveChildFolders(parentId: String): List<FileEntity>

    // [设计] 为什么这样写：首次启动要判断是否需要把 mock JSON 入库，countAll 比拉全量文件更轻。
    @Query("SELECT COUNT(*) FROM file_entity")
    suspend fun countAll(): Int

    // [设计] 为什么这样写：单条插入服务上传或保存分享，全量插入服务 mock 初始化，两者分开让调用方意图更清楚。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileEntity>)

    @Update
    suspend fun update(file: FileEntity)

    // [设计] 为什么这样写：重命名只更新名称和更新时间，保留路径、来源、历史字段，避免一次操作误改整行数据。
    @Query("UPDATE file_entity SET name = :newName, updated_at = :updatedAt WHERE file_id = :fileId AND is_deleted = 0")
    suspend fun renameFile(fileId: String, newName: String, updatedAt: Long): Int

    // [语法] List<String> 是 Kotlin 泛型集合，相当于 Java 的 List<String>；String? 允许移动到根目录。
    // [设计] 为什么这样写：移动本质是批量更新 parent_id，非法移动和重名检查不放 SQL 里，留给上层写成可测试的业务规则。
    @Query("UPDATE file_entity SET parent_id = :targetParentId, updated_at = :updatedAt WHERE file_id IN (:fileIds) AND is_deleted = 0")
    suspend fun moveFiles(fileIds: List<String>, targetParentId: String?, updatedAt: Long): Int

    // [设计] 为什么这样写：opened_at 是首页排序的冗余字段，真实浏览历史仍写 open_history_entity，二者配合兼顾查询效率和行为追踪。
    @Query("UPDATE file_entity SET opened_at = :openedAt, updated_at = :openedAt WHERE file_id = :fileId AND is_deleted = 0")
    suspend fun markOpened(fileId: String, openedAt: Long): Int

    // [设计] 为什么这样写：transferred_at 是首页最近转存的冗余优化，真实转存来源仍由 transfer_history_entity 保留。
    @Query("UPDATE file_entity SET transferred_at = :transferredAt, updated_at = :transferredAt WHERE file_id = :fileId AND is_deleted = 0")
    suspend fun markTransferred(fileId: String, transferredAt: Long): Int

    // [设计] 为什么这样写：删除先走软删除，既能降低演示误操作风险，也能让历史记录保留解释空间。
    @Query("UPDATE file_entity SET is_deleted = 1, updated_at = :deletedAt WHERE file_id IN (:fileIds) AND is_deleted = 0")
    suspend fun softDeleteFiles(fileIds: List<String>, deletedAt: Long): Int
}
