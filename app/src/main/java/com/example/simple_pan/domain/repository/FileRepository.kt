package com.example.simple_pan.domain.repository

import com.example.simple_pan.domain.model.CloudFile
import com.example.simple_pan.domain.model.ShareSnapshotFile
import com.example.simple_pan.domain.model.UploadFileResult
import com.example.simple_pan.domain.model.UploadFileRecord
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

    // [设计] 为什么这样写：管理态通常拿到的是一组选中 id，Repository 提供批量查询可以避免 ViewModel 循环收集 Flow。
    suspend fun findActiveFiles(fileIds: List<String>): List<CloudFile>

    // [语法] Flow<List<CloudFile>> 表示搜索结果会随 Room 数据变化自动刷新，比如上传、重命名、删除后不需要页面手动刷新。
    // [设计] 为什么这样写：搜索页只关心“关键词 -> 结果列表”，具体 SQL、转义规则和 Entity 转换都留在 Repository/Data 层。
    fun observeSearchResults(keyword: String): Flow<List<CloudFile>>

    // [设计] 为什么这样写：重命名弹窗需要先判断同目录是否重名，把查询能力放在 Repository，UI 不直接碰 Room。
    suspend fun hasActiveNameInFolder(parentId: String?, name: String, excludeFileId: String?): Boolean

    // [设计] 为什么这样写：移动弹窗需要展示目标文件夹；Repository 返回领域模型，UI 不需要知道数据库里的 type 字符串。
    suspend fun findActiveChildFolders(parentId: String?): List<CloudFile>

    // [设计] 为什么这样写：文件夹分享需要递归生成快照，UseCase 只需要按 parentId 读取直接子节点，不应该直接访问 DAO。
    suspend fun findActiveChildren(parentId: String): List<CloudFile>

    // [设计] 为什么这样写：移动校验要禁止移动到自身子目录，提前提供后代文件夹 id 查询，后续校验逻辑可以保持清晰。
    suspend fun findActiveDescendantFolderIds(folderId: String): Set<String>

    // [设计] 为什么这样写：重命名、移动、删除先提供 Repository 入口，后续弹窗只负责编排和校验，不直接访问 DAO。
    suspend fun renameFile(fileId: String, newName: String, updatedAt: Long): Boolean

    suspend fun moveFiles(fileIds: List<String>, targetParentId: String?, updatedAt: Long): Int

    // [设计] 为什么这样写：删除文件夹时必须递归软删除子文件，统一放在 Repository 事务中，避免 UI 层遗漏子目录。
    suspend fun deleteFiles(fileIds: List<String>, deletedAt: Long): Int

    // [语法] suspend fun 是协程函数，适合包装 Room 写入这种耗时动作。
    // [设计] 为什么这样写：新建文件夹也是文件表写入能力，放在 Repository 接口里，ViewModel 不直接认识 FileEntity 或 DAO。
    suspend fun createFolder(parentId: String?, name: String, createdAt: Long): CloudFile

    // [语法] suspend fun 是协程函数，类似 Java Future/回调，但调用方可以用顺序代码写异步流程。
    // [设计] 为什么这样写：上传成功后必须同时写文件表和转存历史，Repository 用事务保证首页最近转存和文件列表不会出现半成功状态。
    suspend fun saveUploadedFile(record: UploadFileRecord, transferredAt: Long): CloudFile

    // [设计] 为什么这样写：保存分享需要把快照重建成网盘文件，并写入转存历史；Repository 用事务保证文件列表和首页最近转存一致刷新。
    suspend fun saveShareSnapshots(
        snapshots: List<ShareSnapshotFile>,
        shareToken: String,
        targetParentId: String?,
        transferredAt: Long
    ): List<CloudFile>

    // [语法] suspend fun 是协程函数，适合包装 SAF 读取、文件复制和数据库写入这种耗时流程。
    // [设计] 为什么这样写：上传链路跨越本地文件和 Room 事务，统一放 Repository 能保证 UI 只发起业务动作，不直接拼接多个底层调用。
    suspend fun uploadFromUri(uriString: String, targetParentId: String?): UploadFileResult
}
