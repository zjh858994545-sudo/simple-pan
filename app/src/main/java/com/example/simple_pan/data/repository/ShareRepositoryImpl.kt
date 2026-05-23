package com.example.simple_pan.data.repository

import com.example.simple_pan.data.local.dao.ShareDao
import com.example.simple_pan.data.local.dao.ShareFileSnapshotDao
import com.example.simple_pan.data.local.mapper.toDomain
import com.example.simple_pan.data.local.mapper.toDomainFile
import com.example.simple_pan.di.IoDispatcher
import com.example.simple_pan.domain.model.ShareBundle
import com.example.simple_pan.domain.repository.ShareRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// [设计] 为什么这样写：分享实现类负责把 share_entity 和 share_file_snapshot_entity 聚合起来，UI 只拿 ShareBundle，不感知拆表。
@Singleton
class ShareRepositoryImpl @Inject constructor(
    private val shareDao: ShareDao,
    private val shareFileSnapshotDao: ShareFileSnapshotDao,
    // [语法] @param:IoDispatcher 明确注解作用在构造参数上，类似 Java 构造参数注解，避免 Kotlin 未来版本改变默认目标。
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ShareRepository {
    override fun observeShareBundle(token: String): Flow<ShareBundle?> {
        return shareDao.observeShareByToken(token).map { shareEntity ->
            if (shareEntity == null) {
                null
            } else {
                val snapshots = shareFileSnapshotDao.findSnapshotsByShareId(shareEntity.shareId)
                val snapshotFiles = snapshots.map { snapshot -> snapshot.toDomainFile() }
                shareEntity.toDomain(snapshotFiles)
            }
        }
    }

    override suspend fun findShareBundle(token: String): ShareBundle? = withContext(ioDispatcher) {
        val shareEntity = shareDao.findShareByToken(token)
        if (shareEntity == null) {
            null
        } else {
            val snapshots = shareFileSnapshotDao.findSnapshotsByShareId(shareEntity.shareId)
            val snapshotFiles = snapshots.map { snapshot -> snapshot.toDomainFile() }
            shareEntity.toDomain(snapshotFiles)
        }
    }
}
