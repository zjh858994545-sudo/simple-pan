package com.example.simple_pan.data.repository

import androidx.room.withTransaction
import com.example.simple_pan.data.local.AppDatabase
import com.example.simple_pan.data.local.dao.FileDao
import com.example.simple_pan.data.local.entity.FileEntity
import com.example.simple_pan.data.local.mapper.toDomain
import com.example.simple_pan.data.local.mapper.toEntity
import com.example.simple_pan.data.remote.FakeRemoteDataSource
import com.example.simple_pan.di.IoDispatcher
import com.example.simple_pan.domain.model.CloudFile
import com.example.simple_pan.domain.repository.FileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// [语法] @Inject constructor 表示 Hilt 可以创建这个类，类似 Java 里用依赖注入框架标记构造函数。
// [设计] 为什么这样写：实现类在 data 层同时连接 FakeRemoteDataSource 和 Room，domain 层只看到 FileRepository 接口。
@Singleton
class FileRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val fileDao: FileDao,
    private val fakeRemoteDataSource: FakeRemoteDataSource,
    // [语法] @param:IoDispatcher 明确注解作用在构造参数上，类似 Java 构造参数注解，避免 Kotlin 未来版本改变默认目标。
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : FileRepository {
    override fun observeFiles(parentId: String?): Flow<List<CloudFile>> {
        // [语法] map 是 Flow 的扩展函数，类似 Java Stream.map，但这里会随着数据库变化持续转换每次发射的数据。
        // [设计] 为什么这样写：DAO 发射 Entity，Repository 转成领域模型，ViewModel 不需要知道 Room 表结构。
        return fileDao.observeFilesInFolder(parentId).map { entities ->
            entities.map { entity -> entity.toDomain() }
        }
    }

    override fun observeFile(fileId: String): Flow<CloudFile?> {
        return fileDao.observeFile(fileId).map { entity ->
            if (entity == null) {
                null
            } else {
                entity.toDomain()
            }
        }
    }

    // [语法] suspend fun 是协程函数；withContext(...) 类似 Java 中把任务提交到指定 Executor 后等待结果。
    // [设计] 为什么这样写：首次 mock 入库涉及 assets 读取和数据库写入，统一切到注入的 IO Dispatcher，避免主线程卡顿。
    override suspend fun initializeFromMockIfNeeded(): Boolean = withContext(ioDispatcher) {
        if (fileDao.countAll() > 0) {
            return@withContext false
        }

        val mockFiles = fakeRemoteDataSource.loadFiles().map { fileDto -> fileDto.toEntity() }

        // [语法] withTransaction 是 Room 的扩展函数，相当于 Java 里 beginTransaction/commit/rollback 的安全封装。
        // [设计] 为什么这样写：再次检查 countAll 可以避免并发初始化重复插入，插入失败时事务会自动回滚。
        database.withTransaction {
            if (fileDao.countAll() > 0) {
                false
            } else {
                fileDao.insertAll(mockFiles)
                true
            }
        }
    }

    override suspend fun findActiveFile(fileId: String): CloudFile? = withContext(ioDispatcher) {
        val entity = fileDao.findActiveFile(fileId)
        if (entity == null) {
            null
        } else {
            entity.toDomain()
        }
    }

    // [语法] suspend fun + withContext 表示协程函数切到指定线程执行，类似 Java 把数据库任务提交到 IO Executor。
    // [设计] 为什么这样写：批量查询用于管理态选中项，Repository 先过滤空列表，避免 Room 的 IN 空参数造成不必要的数据库访问。
    override suspend fun findActiveFiles(fileIds: List<String>): List<CloudFile> = withContext(ioDispatcher) {
        if (fileIds.isEmpty()) {
            return@withContext emptyList()
        }
        fileDao.findActiveFiles(fileIds).map { entity -> entity.toDomain() }
    }

    // [设计] 为什么这样写：同名检查属于数据访问能力，但是否提示错误、是否保留扩展名由上层业务决定，避免 Repository 绑死 UI 规则。
    override suspend fun hasActiveNameInFolder(
        parentId: String?,
        name: String,
        excludeFileId: String?
    ): Boolean = withContext(ioDispatcher) {
        fileDao.countActiveNameInFolder(
            parentId = parentId,
            name = name,
            excludeFileId = excludeFileId
        ) > 0
    }

    // [设计] 为什么这样写：移动弹窗需要按目录层级加载目标文件夹，Repository 返回 CloudFile 让 UI 不依赖 Room Entity。
    override suspend fun findActiveChildFolders(parentId: String?): List<CloudFile> = withContext(ioDispatcher) {
        fileDao.findActiveChildFolders(parentId).map { entity -> entity.toDomain() }
    }

    // [设计] 为什么这样写：把“找出某文件夹所有后代目录”集中在 Repository，后续移动校验只需要判断目标 id 是否在这个集合里。
    override suspend fun findActiveDescendantFolderIds(folderId: String): Set<String> = withContext(ioDispatcher) {
        // [语法] mutableSetOf 创建可变 Set，相当于 Java 的 new LinkedHashSet<String>()。
        // [设计] 为什么这样写：递归收集后代目录时需要逐步追加 id，但对外返回 Set，避免重复目录影响移动校验。
        val descendantFolderIds = mutableSetOf<String>()
        collectActiveDescendantFolderIds(
            parentId = folderId,
            target = descendantFolderIds
        )
        descendantFolderIds
    }

    // [设计] 为什么这样写：Repository 只执行重命名写库动作，空名、重名、扩展名保留会在下一步弹窗逻辑里先校验。
    override suspend fun renameFile(fileId: String, newName: String, updatedAt: Long): Boolean = withContext(ioDispatcher) {
        fileDao.renameFile(fileId, newName, updatedAt) > 0
    }

    // [设计] 为什么这样写：移动本质是批量更新 parent_id；这里先处理空选择，非法目标校验留给下一步移动弹窗。
    override suspend fun moveFiles(fileIds: List<String>, targetParentId: String?, updatedAt: Long): Int = withContext(ioDispatcher) {
        if (fileIds.isEmpty()) {
            return@withContext 0
        }
        fileDao.moveFiles(fileIds, targetParentId, updatedAt)
    }

    // [设计] 为什么这样写：删除文件夹必须把子孙节点一起软删除，Repository 用事务包住递归收集和批量更新，保证列表刷新时数据一致。
    override suspend fun deleteFiles(fileIds: List<String>, deletedAt: Long): Int = withContext(ioDispatcher) {
        if (fileIds.isEmpty()) {
            return@withContext 0
        }

        // [语法] withTransaction 是 Room 的扩展函数，相当于 Java 手写 beginTransaction/commit/rollback。
        // [设计] 为什么这样写：删除文件夹和子文件必须在同一事务里软删除，避免中途失败后只删了父目录或只删了部分子文件。
        database.withTransaction {
            val idsToDelete = mutableSetOf<String>()
            val activeTargets = fileDao.findActiveFiles(fileIds)
            for (target in activeTargets) {
                idsToDelete += target.fileId
                if (target.type == FileEntity.TYPE_FOLDER) {
                    collectActiveDescendantFileIds(
                        parentId = target.fileId,
                        target = idsToDelete
                    )
                }
            }
            if (idsToDelete.isEmpty()) {
                0
            } else {
                fileDao.softDeleteFiles(idsToDelete.toList(), deletedAt)
            }
        }
    }

    // [设计] 为什么这样写：后代目录查询用于移动校验，只关心文件夹 id；递归放在 Repository 里，避免把业务含义塞进复杂 SQL。
    private suspend fun collectActiveDescendantFolderIds(
        parentId: String,
        target: MutableSet<String>
    ) {
        val childFolders = fileDao.findActiveChildFolders(parentId)
        for (childFolder in childFolders) {
            if (target.add(childFolder.fileId)) {
                collectActiveDescendantFolderIds(
                    parentId = childFolder.fileId,
                    target = target
                )
            }
        }
    }

    // [设计] 为什么这样写：软删除文件夹时要包含所有层级的子文件和子文件夹；Repository 递归收集 id 后再批量更新，能保证 Room Flow 只看到一致结果。
    private suspend fun collectActiveDescendantFileIds(
        parentId: String,
        target: MutableSet<String>
    ) {
        val children = fileDao.findActiveChildren(parentId)
        for (child in children) {
            if (target.add(child.fileId) && child.type == FileEntity.TYPE_FOLDER) {
                collectActiveDescendantFileIds(
                    parentId = child.fileId,
                    target = target
                )
            }
        }
    }
}
