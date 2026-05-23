package com.example.simple_pan.data.repository

import androidx.room.withTransaction
import com.example.simple_pan.data.local.AppDatabase
import com.example.simple_pan.data.local.dao.FileDao
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

    override suspend fun renameFile(fileId: String, newName: String, updatedAt: Long): Boolean = withContext(ioDispatcher) {
        fileDao.renameFile(fileId, newName, updatedAt) > 0
    }

    override suspend fun moveFiles(fileIds: List<String>, targetParentId: String?, updatedAt: Long): Int = withContext(ioDispatcher) {
        fileDao.moveFiles(fileIds, targetParentId, updatedAt)
    }

    override suspend fun deleteFiles(fileIds: List<String>, deletedAt: Long): Int = withContext(ioDispatcher) {
        fileDao.softDeleteFiles(fileIds, deletedAt)
    }
}
