package com.example.simple_pan.data.repository

import androidx.room.withTransaction
import com.example.simple_pan.data.local.AppDatabase
import com.example.simple_pan.data.local.dao.FileDao
import com.example.simple_pan.data.local.dao.OpenHistoryDao
import com.example.simple_pan.data.local.dao.TransferHistoryDao
import com.example.simple_pan.data.local.entity.OpenHistoryEntity
import com.example.simple_pan.data.local.entity.TransferHistoryEntity
import com.example.simple_pan.data.local.mapper.toDomain
import com.example.simple_pan.data.local.mapper.toRecentRecord
import com.example.simple_pan.di.IoDispatcher
import com.example.simple_pan.domain.model.RecentRecord
import com.example.simple_pan.domain.repository.RecentRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// [设计] 为什么这样写：最近记录实现类聚合历史表和文件表，既保留真实行为，又能给首页提供文件名、类型等展示字段。
@Singleton
class RecentRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val fileDao: FileDao,
    private val openHistoryDao: OpenHistoryDao,
    private val transferHistoryDao: TransferHistoryDao,
    // [语法] @param:IoDispatcher 明确注解作用在构造参数上，类似 Java 构造参数注解，避免 Kotlin 未来版本改变默认目标。
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : RecentRepository {
    override fun observeRecentOpen(limit: Int): Flow<List<RecentRecord>> {
        // [语法] Flow.map 的 lambda 可以调用 suspend DAO 方法；这和 Java Stream.map 不同，它能在协程上下文里挂起等待。
        // [设计] 为什么这样写：历史表只存 fileId，展示时再查当前有效文件，已软删除文件会自然从最近列表隐藏。
        return openHistoryDao.observeRecentOpenHistory(limit).map { histories ->
            val records = mutableListOf<RecentRecord>()
            for (history in histories) {
                val fileEntity = fileDao.findActiveFile(history.fileId)
                if (fileEntity != null) {
                    records += history.toRecentRecord(fileEntity.toDomain())
                }
            }
            records
        }
    }

    override fun observeRecentTransfer(limit: Int): Flow<List<RecentRecord>> {
        // [设计] 为什么这样写：最近转存直接走历史表和文件表的 join Flow；上传成功写入两张表后，Room 会触发首页自动收到最新记录。
        return transferHistoryDao.observeRecentTransferWithFiles(limit).map { histories ->
            histories.map { history -> history.toRecentRecord() }
        }
    }

    override suspend fun recordOpen(fileId: String, openedAt: Long, progress: Long?) {
        // [设计] 为什么这样写：写浏览历史和更新文件冗余 opened_at 必须一起成功，否则首页和文件表状态会不一致。
        withContext(ioDispatcher) {
            database.withTransaction {
                openHistoryDao.insert(
                    OpenHistoryEntity(
                        fileId = fileId,
                        openedAt = openedAt,
                        progress = progress
                    )
                )
                fileDao.markOpened(fileId, openedAt)
            }
        }
    }

    override suspend fun recordTransfer(fileId: String, transferType: String, shareToken: String?, transferredAt: Long) {
        // [设计] 为什么这样写：转存历史和 file_entity.transferred_at 一起写，首页既能走历史表，也能用冗余字段快速排序。
        withContext(ioDispatcher) {
            database.withTransaction {
                transferHistoryDao.insert(
                    TransferHistoryEntity(
                        fileId = fileId,
                        transferType = transferType,
                        shareToken = shareToken,
                        transferredAt = transferredAt
                    )
                )
                fileDao.markTransferred(fileId, transferredAt)
            }
        }
    }
}
