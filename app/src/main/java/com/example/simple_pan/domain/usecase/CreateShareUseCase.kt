package com.example.simple_pan.domain.usecase

import com.example.simple_pan.di.IoDispatcher
import com.example.simple_pan.domain.model.CloudFile
import com.example.simple_pan.domain.model.CreateShareRequest
import com.example.simple_pan.domain.model.CreateShareResult
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.ShareSnapshotFile
import com.example.simple_pan.domain.model.ShareType
import com.example.simple_pan.domain.repository.FileRepository
import com.example.simple_pan.domain.repository.ShareRepository
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

// [设计] 为什么这样写：创建分享横跨文件树读取、快照生成和分享表写入，放在 UseCase 能让 ViewModel 只表达“分享这些文件”，不直接拼业务规则。
class CreateShareUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    private val shareRepository: ShareRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    // [语法] suspend operator fun invoke 让调用方可以写 createShareUseCase(fileIds)，类似 Java 里调用单方法服务对象。
    // [设计] 为什么这样写：快照生成会递归读取文件夹子节点，属于 IO 业务流程，统一切到注入的 IO Dispatcher，遵守项目协程约束。
    suspend operator fun invoke(fileIds: List<String>): CreateShareResult = withContext(ioDispatcher) {
        try {
            val requestedFileIds = fileIds.distinct()
            if (requestedFileIds.isEmpty()) {
                return@withContext CreateShareResult.EmptySelection
            }

            val activeFilesById = fileRepository.findActiveFiles(requestedFileIds)
                .associateBy { file -> file.fileId }
            val selectedFiles = requestedFileIds.mapNotNull { fileId -> activeFilesById[fileId] }
            if (selectedFiles.isEmpty()) {
                return@withContext CreateShareResult.NoActiveFiles
            }

            val snapshotFiles = selectedFiles.toShareSnapshots()
            val shareBundle = shareRepository.createShare(
                CreateShareRequest(
                    title = selectedFiles.toShareTitle(),
                    shareType = selectedFiles.toShareType(),
                    snapshotFiles = snapshotFiles
                )
            )
            CreateShareResult.Created(shareBundle)
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                throw exception
            }
            CreateShareResult.Failed(exception.message)
        }
    }

    // [语法] 这是 List<CloudFile> 的扩展函数，相当于 Java 静态工具方法 ShareSnapshots.toShareSnapshots(files)。
    // [设计] 为什么这样写：快照生成需要按选择顺序展开文件和文件夹，并去重；集中成函数能让主流程保持清楚。
    private suspend fun List<CloudFile>.toShareSnapshots(): List<ShareSnapshotFile> {
        val snapshotsBySourceId = linkedMapOf<String, ShareSnapshotFile>()
        for (file in this) {
            collectSnapshot(
                file = file,
                parentRelativePath = null,
                target = snapshotsBySourceId
            )
        }
        return snapshotsBySourceId.values.toList()
    }

    // [设计] 为什么这样写：文件夹分享要固定分享时刻的整棵子树；递归时给子节点带上父级 relativePath，后续分享页和保存到网盘都能恢复层级。
    private suspend fun collectSnapshot(
        file: CloudFile,
        parentRelativePath: String?,
        target: MutableMap<String, ShareSnapshotFile>
    ) {
        target[file.fileId] = file.toSnapshot(parentRelativePath)
        if (file.type != FileType.Folder) {
            return
        }

        val childParentPath = parentRelativePath.appendPathSegment(file.name)
        val children = fileRepository.findActiveChildren(file.fileId)
            .sortedWith(SHARE_CHILD_COMPARATOR)
        for (child in children) {
            collectSnapshot(
                file = child,
                parentRelativePath = childParentPath,
                target = target
            )
        }
    }

    // [语法] 这是 CloudFile 的扩展函数，相当于 Java 静态工具方法 ShareSnapshots.toSnapshot(file, path)。
    // [设计] 为什么这样写：快照只保存分享展示和后续保存所需字段，避免把原文件完整状态泄漏进分享链路。
    private fun CloudFile.toSnapshot(relativePath: String?): ShareSnapshotFile {
        return ShareSnapshotFile(
            sourceFileId = fileId,
            name = name,
            type = type,
            sizeBytes = sizeBytes,
            relativePath = relativePath,
            localPath = localPath
        )
    }

    // [语法] 这是 String? 的扩展函数，相当于 Java 静态工具方法 SharePath.appendPathSegment(path, segment)。
    // [设计] 为什么这样写：relativePath 只表示父级路径，不包含当前文件名；用统一函数拼接能避免多层文件夹路径格式不一致。
    private fun String?.appendPathSegment(segment: String): String {
        return if (isNullOrBlank()) {
            segment
        } else {
            "$this/$segment"
        }
    }

    // [语法] 这是 List<CloudFile> 的扩展函数，相当于 Java 静态工具方法 ShareTypeResolver.resolve(files)。
    // [设计] 为什么这样写：分享类型由选中项决定，单文件、单文件夹、多文件三种规则集中处理，后续 UI 不需要再判断。
    private fun List<CloudFile>.toShareType(): ShareType {
        return if (size == 1) {
            if (first().type == FileType.Folder) {
                ShareType.Folder
            } else {
                ShareType.SingleFile
            }
        } else {
            ShareType.MultiFile
        }
    }

    // [语法] 这是 List<CloudFile> 的扩展函数，相当于 Java 静态工具方法 ShareTitleResolver.resolve(files)。
    // [设计] 为什么这样写：分享标题在创建时固定下来，后续原文件改名不会影响分享页顶部文案。
    private fun List<CloudFile>.toShareTitle(): String {
        return if (size == 1) {
            first().name
        } else {
            "${first().name} 等 $size 项"
        }
    }

    // [语法] companion object 相当于 Java 的 static 常量区域。
    // [设计] 为什么这样写：子节点排序是快照稳定性的组成部分，集中定义能保证文件夹分享每次生成的列表顺序可预测。
    companion object {
        private val SHARE_CHILD_COMPARATOR = compareByDescending<CloudFile> { file -> file.type == FileType.Folder }
            .thenBy { file -> file.name.lowercase(Locale.ROOT) }
    }
}
