package com.example.simple_pan.domain.repository

import com.example.simple_pan.domain.model.CloudFile
import kotlinx.coroutines.flow.Flow

// [设计] 为什么这样写：Repository 接口放在 domain 层，让 ViewModel 和 UseCase 依赖抽象，不直接依赖 Room、JSON 或文件系统。
interface FileRepository {
    // [语法] Flow<List<CloudFile>> 类似 Java Observable<List<CloudFile>>，数据库变化后会持续推送新列表。
    // [设计] 为什么这样写：文件列表要由 Room 响应式驱动，上传、移动、删除后 UI 不需要手动刷新。
    fun observeFiles(parentId: String?): Flow<List<CloudFile>>

    // [语法] Flow<CloudFile?> 中的 ? 表示可能没有文件，相当于 Java Observable<Optional<CloudFile>>。
    // [设计] 为什么这样写：文件被软删除或不存在时，上层能进入空状态，而不是靠异常控制流程。
    fun observeFile(fileId: String): Flow<CloudFile?>

    // [语法] suspend fun 是 Kotlin 协程函数，类似 Java Future/回调，但在协程里能按顺序写。
    // [设计] 为什么这样写：首次 mock 入库是一次性初始化动作，需要在 Repository 里用事务保证不会重复插入。
    suspend fun initializeFromMockIfNeeded(): Boolean

    // [设计] 为什么这样写：一次性查询给打开文件、移动校验等业务使用，避免上层为了拿当前值去收集 Flow。
    suspend fun findActiveFile(fileId: String): CloudFile?

    // [设计] 为什么这样写：重命名、移动、删除先提供 Repository 入口，后续 UseCase 只负责编排和校验，不直接访问 DAO。
    suspend fun renameFile(fileId: String, newName: String, updatedAt: Long): Boolean

    suspend fun moveFiles(fileIds: List<String>, targetParentId: String?, updatedAt: Long): Int

    suspend fun deleteFiles(fileIds: List<String>, deletedAt: Long): Int
}
