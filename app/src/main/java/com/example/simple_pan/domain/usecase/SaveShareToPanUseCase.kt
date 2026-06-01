package com.example.simple_pan.domain.usecase

import com.example.simple_pan.di.IoDispatcher
import com.example.simple_pan.domain.model.SaveShareResult
import com.example.simple_pan.domain.repository.FileRepository
import com.example.simple_pan.domain.repository.ShareRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

// [设计] 为什么这样写：保存分享横跨“读取分享快照”和“写入网盘文件/转存历史”，放在 UseCase 能让 ViewModel 只表达“保存这个 token”。
class SaveShareToPanUseCase @Inject constructor(
    private val shareRepository: ShareRepository,
    private val fileRepository: FileRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    // [语法] suspend operator fun invoke 让调用方可以写 saveShareToPanUseCase(token)，类似 Java 里调用单方法服务对象。
    // [设计] 为什么这样写：保存分享是 IO 业务流程，统一切到注入的 IO Dispatcher，遵守项目协程约束。
    suspend operator fun invoke(
        token: String,
        targetParentId: String? = null
    ): SaveShareResult = withContext(ioDispatcher) {
        try {
            val normalizedToken = token.trim()
            if (normalizedToken.isBlank()) {
                return@withContext SaveShareResult.MissingToken
            }

            val shareBundle = shareRepository.findShareBundle(normalizedToken)
                ?: return@withContext SaveShareResult.ShareNotFound
            if (shareBundle.snapshotFiles.isEmpty()) {
                return@withContext SaveShareResult.EmptySnapshot
            }

            val savedFiles = fileRepository.saveShareSnapshots(
                snapshots = shareBundle.snapshotFiles,
                shareToken = shareBundle.token,
                targetParentId = targetParentId,
                transferredAt = System.currentTimeMillis()
            )
            SaveShareResult.Saved(savedFiles)
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                throw exception
            }
            SaveShareResult.Failed(exception.message)
        }
    }
}
