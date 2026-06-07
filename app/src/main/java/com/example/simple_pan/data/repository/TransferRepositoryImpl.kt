package com.example.simple_pan.data.repository

import com.example.simple_pan.data.local.dao.TransferHistoryDao
import com.example.simple_pan.data.local.dao.TransferHistoryListItem
import com.example.simple_pan.data.local.entity.TransferHistoryEntity
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.TransferDirection
import com.example.simple_pan.domain.model.TransferRecord
import com.example.simple_pan.domain.model.TransferStatus
import com.example.simple_pan.domain.repository.TransferRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// [语法] @Inject constructor 表示 Hilt 可以创建这个类并注入 TransferHistoryDao。
// [设计] 为什么这样写：传输页真实数据来自 transfer_history 和 file_entity 的 join，集中在 data 层实现，domain/UI 不认识表结构。
@Singleton
class TransferRepositoryImpl @Inject constructor(
    private val transferHistoryDao: TransferHistoryDao
) : TransferRepository {
    override fun observeTransferRecords(direction: TransferDirection): Flow<List<TransferRecord>> {
        val transferTypes = direction.toTransferTypes()
        return transferHistoryDao.observeTransferListItems(transferTypes).map { items ->
            items.map { item -> item.toDomain(direction) }
        }
    }

    // [设计] 为什么这样写：当前 App 没有真实“下载到本机”任务表；先把分享保存归入下载侧展示，因为它也是从分享来源保存进网盘的传输完成记录。
    private fun TransferDirection.toTransferTypes(): List<String> {
        return when (this) {
            TransferDirection.Upload -> listOf(TransferHistoryEntity.TYPE_UPLOAD)
            TransferDirection.Download -> listOf(TransferHistoryEntity.TYPE_SHARE_SAVE)
        }
    }

    // [语法] 这是扩展函数，相当于 Java 静态工具方法 TransferMappers.toDomain(item, direction)。
    // [设计] 为什么这样写：transfer_history 目前只在成功后写入，所以这些记录天然都是 Completed；进行中/失败要等后续接任务表。
    private fun TransferHistoryListItem.toDomain(direction: TransferDirection): TransferRecord {
        return TransferRecord(
            historyId = historyId,
            fileId = fileId,
            fileName = fileName,
            fileType = FileType.fromStorageValue(fileType),
            sizeBytes = sizeBytes,
            transferredAt = transferredAt,
            transferType = transferType,
            direction = direction,
            status = TransferStatus.Completed
        )
    }
}
