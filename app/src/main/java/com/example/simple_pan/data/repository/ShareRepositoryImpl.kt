package com.example.simple_pan.data.repository

import androidx.room.withTransaction
import com.example.simple_pan.data.local.AppDatabase
import com.example.simple_pan.data.local.dao.ShareDao
import com.example.simple_pan.data.local.dao.ShareFileSnapshotDao
import com.example.simple_pan.data.local.entity.ShareEntity
import com.example.simple_pan.data.local.mapper.toDomain
import com.example.simple_pan.data.local.mapper.toDomainSnapshot
import com.example.simple_pan.data.local.mapper.toEntity
import com.example.simple_pan.di.IoDispatcher
import com.example.simple_pan.domain.model.CreateShareRequest
import com.example.simple_pan.domain.model.ShareBundle
import com.example.simple_pan.domain.repository.ShareRepository
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// [设计] 为什么这样写：分享实现类负责把 share_entity 和 share_file_snapshot_entity 聚合起来，UI 只拿 ShareBundle，不感知拆表。
@Singleton
class ShareRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val shareDao: ShareDao,
    private val shareFileSnapshotDao: ShareFileSnapshotDao,
    // [语法] @param:IoDispatcher 明确注解作用在构造参数上，类似 Java 构造参数注解，避免 Kotlin 未来版本改变默认目标。
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ShareRepository {
    // [语法] suspend fun 是协程挂起函数，类似 Java Future/回调；withContext 会把事务切到注入的 IO 调度器执行。
    // [设计] 为什么这样写：创建分享必须先生成不含文件明文的 token，再把分享元信息和快照放进同一个事务，避免只写一半导致分享页查到残缺数据。
    override suspend fun createShare(request: CreateShareRequest): ShareBundle = withContext(ioDispatcher) {
        require(request.snapshotFiles.isNotEmpty()) {
            "分享内容不能为空"
        }

        val shareId = UUID.randomUUID().toString()
        val token = generateUniqueToken()
        val createdAt = System.currentTimeMillis()
        val normalizedTitle = request.title.ifBlank { DEFAULT_SHARE_TITLE }
        val shareEntity = ShareEntity(
            shareId = shareId,
            token = token,
            title = normalizedTitle,
            shareType = request.shareType.storageValue,
            createdAt = createdAt,
            expiredAt = request.expiredAt,
            ownerMask = request.ownerMask
        )

        database.withTransaction {
            shareDao.insert(shareEntity)
            shareFileSnapshotDao.insertAll(
                request.snapshotFiles.map { snapshot -> snapshot.toEntity(shareId) }
            )
        }

        val snapshots = shareFileSnapshotDao.findSnapshotsByShareId(shareId)
            .map { snapshot -> snapshot.toDomainSnapshot() }
        shareEntity.toDomain(snapshots)
    }

    // [设计] 为什么这样写：分享页打开后需要观察 token 对应快照，Room Flow 能在分享被删除或快照变化时自动刷新 UI。
    override fun observeShareBundle(token: String): Flow<ShareBundle?> {
        return shareDao.observeShareByToken(token).map { shareEntity ->
            if (shareEntity == null) {
                null
            } else {
                val snapshots = shareFileSnapshotDao.findSnapshotsByShareId(shareEntity.shareId)
                val snapshotFiles = snapshots.map { snapshot -> snapshot.toDomainSnapshot() }
                shareEntity.toDomain(snapshotFiles)
            }
        }
    }

    // [设计] 为什么这样写：保存分享时只需要一次性读取当前快照，用 suspend 查询比长期观察更轻，也更适合放进保存流程。
    override suspend fun findShareBundle(token: String): ShareBundle? = withContext(ioDispatcher) {
        val shareEntity = shareDao.findShareByToken(token)
        if (shareEntity == null) {
            null
        } else {
            val snapshots = shareFileSnapshotDao.findSnapshotsByShareId(shareEntity.shareId)
            val snapshotFiles = snapshots.map { snapshot -> snapshot.toDomainSnapshot() }
            shareEntity.toDomain(snapshotFiles)
        }
    }

    // [设计] 为什么这样写：分享链接只需要一个不可猜测 token，不把 file_id/path/name 放进链接；生成前检查冲突，极小概率冲突时重新生成。
    private suspend fun generateUniqueToken(): String {
        repeat(MAX_TOKEN_GENERATE_ATTEMPTS) {
            val token = generateToken()
            if (shareDao.countByToken(token) == 0) {
                return token
            }
        }
        error("生成分享 token 失败，请重试")
    }

    // [设计] 为什么这样写：token 使用 URL 安全字符，后续可以直接放到 https://simplepan.local/s/{token} 中，不需要额外编码。
    private fun generateToken(): String {
        return buildString(SHARE_TOKEN_LENGTH) {
            repeat(SHARE_TOKEN_LENGTH) {
                append(TOKEN_CHARS[tokenRandom.nextInt(TOKEN_CHARS.length)])
            }
        }
    }

    // [语法] companion object 相当于 Java 的 static 常量区域。
    // [设计] 为什么这样写：token 长度、字符表和重试次数属于分享安全策略参数，集中命名方便后续答辩说明。
    companion object {
        private const val DEFAULT_SHARE_TITLE = "我的分享"
        private const val SHARE_TOKEN_LENGTH = 22
        private const val MAX_TOKEN_GENERATE_ATTEMPTS = 5
        private const val TOKEN_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"
        private val tokenRandom = SecureRandom()
    }
}
