package com.example.simple_pan.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.example.simple_pan.data.local.AppDatabase
import com.example.simple_pan.data.local.dao.FileDao
import com.example.simple_pan.data.local.dao.TransferHistoryDao
import com.example.simple_pan.data.local.entity.FileEntity
import com.example.simple_pan.data.local.entity.TransferHistoryEntity
import com.example.simple_pan.data.local.mapper.toDomain
import com.example.simple_pan.data.local.mapper.toEntity
import com.example.simple_pan.data.remote.FakeRemoteDataSource
import com.example.simple_pan.data.storage.LocalFileMetadataReader
import com.example.simple_pan.data.storage.PrivateUploadStorage
import com.example.simple_pan.di.IoDispatcher
import com.example.simple_pan.domain.model.CloudFile
import com.example.simple_pan.domain.model.LocalFileMetadata
import com.example.simple_pan.domain.model.ShareSnapshotFile
import com.example.simple_pan.domain.model.StoredUploadFile
import com.example.simple_pan.domain.model.UploadFileCopyResult
import com.example.simple_pan.domain.model.UploadFileResult
import com.example.simple_pan.domain.model.UploadFileRecord
import com.example.simple_pan.domain.repository.FileRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
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
    private val transferHistoryDao: TransferHistoryDao,
    private val fakeRemoteDataSource: FakeRemoteDataSource,
    private val localFileMetadataReader: LocalFileMetadataReader,
    private val privateUploadStorage: PrivateUploadStorage,
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

    // [设计] 为什么这样写：分享快照需要读取任意文件夹的一层直接子节点，递归规则留给 UseCase，Repository 只暴露领域模型。
    override suspend fun findActiveChildren(parentId: String): List<CloudFile> = withContext(ioDispatcher) {
        fileDao.findActiveChildren(parentId).map { entity -> entity.toDomain() }
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

    // [语法] suspend fun + withContext 表示协程函数切到指定线程执行，类似 Java 把数据库任务提交到 IO Executor。
    // [设计] 为什么这样写：上传成功后的三个数据库动作必须原子完成，避免文件列表出现了文件但首页最近转存没有记录。
    override suspend fun saveUploadedFile(record: UploadFileRecord, transferredAt: Long): CloudFile = withContext(ioDispatcher) {
        database.withTransaction {
            fileDao.insert(record.toEntity())
            transferHistoryDao.insert(
                TransferHistoryEntity(
                    fileId = record.fileId,
                    transferType = TransferHistoryEntity.TYPE_UPLOAD,
                    shareToken = null,
                    transferredAt = transferredAt
                )
            )
            fileDao.markTransferred(
                fileId = record.fileId,
                transferredAt = transferredAt
            )
        }

        val savedFile = fileDao.findActiveFile(record.fileId)
        requireNotNull(savedFile) {
            "上传文件写入后无法读取：${record.fileId}"
        }.toDomain()
    }

    // [语法] suspend fun + withContext 表示协程函数切到指定线程执行，类似 Java 把保存任务提交到 IO Executor。
    // [设计] 为什么这样写：保存分享需要重建文件夹层级、处理同名、写文件表和转存历史，事务包住后首页和文件列表不会出现半成功状态。
    override suspend fun saveShareSnapshots(
        snapshots: List<ShareSnapshotFile>,
        shareToken: String,
        targetParentId: String?,
        transferredAt: Long
    ): List<CloudFile> = withContext(ioDispatcher) {
        require(snapshots.isNotEmpty()) {
            "分享快照不能为空"
        }
        require(isValidUploadTarget(targetParentId)) {
            "目标文件夹不存在或已被删除"
        }

        val savedFileIds = database.withTransaction {
            val savedFolderIdsByOriginalPath = mutableMapOf<String, String>()
            val reservedNamesByParent = mutableMapOf<String?, MutableSet<String>>()
            val savedIds = mutableListOf<String>()

            for (snapshot in snapshots.toShareSaveOrder()) {
                val parentId = snapshot.resolveSavedParentId(
                    targetParentId = targetParentId,
                    savedFolderIdsByOriginalPath = savedFolderIdsByOriginalPath
                )
                val savedName = resolveUniqueShareSaveName(
                    parentId = parentId,
                    requestedName = snapshot.name,
                    preserveExtension = snapshot.type.storageValue != FileEntity.TYPE_FOLDER,
                    reservedNamesByParent = reservedNamesByParent
                )
                val fileId = UUID.randomUUID().toString()
                val entity = snapshot.toShareSavedEntity(
                    fileId = fileId,
                    parentId = parentId,
                    savedName = savedName,
                    transferredAt = transferredAt
                )

                fileDao.insert(entity)
                if (snapshot.isTopLevelShareSnapshot()) {
                    transferHistoryDao.insert(
                        TransferHistoryEntity(
                            fileId = fileId,
                            transferType = TransferHistoryEntity.TYPE_SHARE_SAVE,
                            shareToken = shareToken,
                            transferredAt = transferredAt
                        )
                    )
                }
                if (entity.type == FileEntity.TYPE_FOLDER) {
                    savedFolderIdsByOriginalPath[snapshot.originalFullPath()] = fileId
                }
                savedIds += fileId
            }
            savedIds
        }

        fileDao.findActiveFiles(savedFileIds).map { entity -> entity.toDomain() }
    }

    // [语法] suspend fun + withContext 表示协程函数切到指定线程执行，类似 Java 把上传任务提交到 IO Executor。
    // [设计] 为什么这样写：上传不是单个 DAO 操作，而是“校验目标目录 -> 读取元信息 -> 复制文件 -> 事务入库 -> 失败清理”的完整业务链路，集中在 Repository 能保证状态一致。
    override suspend fun uploadFromUri(
        uriString: String,
        targetParentId: String?
    ): UploadFileResult = withContext(ioDispatcher) {
        if (!isValidUploadTarget(targetParentId)) {
            return@withContext UploadFileResult.TargetFolderUnavailable
        }

        val metadata = tryReadMetadata(uriString)
            ?: return@withContext UploadFileResult.SourceUnavailable

        when (val copyResult = tryCopyToPrivateStorage(metadata)) {
            is UploadFileCopyResult.Copied -> saveCopiedUploadFile(
                storedFile = copyResult.file,
                targetParentId = targetParentId
            )
            is UploadFileCopyResult.RejectedBySize -> UploadFileResult.RejectedBySize(copyResult.reason)
            UploadFileCopyResult.SourceUnavailable -> UploadFileResult.SourceUnavailable
            UploadFileCopyResult.StorageUnavailable -> UploadFileResult.StorageUnavailable
            is UploadFileCopyResult.Failed -> UploadFileResult.Failed(copyResult.message)
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

    // [设计] 为什么这样写：上传目标如果不存在或不是文件夹，就直接拒绝，避免写出一个在文件列表里不可达的孤儿文件。
    private suspend fun isValidUploadTarget(targetParentId: String?): Boolean {
        if (targetParentId == null) {
            return true
        }
        val targetFolder = fileDao.findActiveFile(targetParentId)
        return targetFolder?.type == FileEntity.TYPE_FOLDER
    }

    // [设计] 为什么这样写：读取 SAF 元信息可能因为权限失效、来源文件删除或系统提供者异常而失败，Repository 把这些失败转成上传结果。
    private suspend fun tryReadMetadata(uriString: String): LocalFileMetadata? = try {
        if (uriString.isBlank()) {
            null
        } else {
            localFileMetadataReader.read(Uri.parse(uriString))
        }
    } catch (exception: SecurityException) {
        null
    } catch (exception: IllegalArgumentException) {
        null
    } catch (exception: Exception) {
        if (exception is CancellationException) {
            throw exception
        }
        null
    }

    // [设计] 为什么这样写：复制器已经把大小校验和半成品清理封装好，Repository 只负责把异常边界收敛成稳定结果。
    private suspend fun tryCopyToPrivateStorage(metadata: LocalFileMetadata): UploadFileCopyResult {
        return try {
            privateUploadStorage.copy(metadata)
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                throw exception
            }
            UploadFileCopyResult.Failed(exception.message)
        }
    }

    // [设计] 为什么这样写：复制成功后才生成数据库记录，并在写库失败时删除私有文件，保证 Room 和磁盘不会长期不一致。
    private suspend fun saveCopiedUploadFile(
        storedFile: StoredUploadFile,
        targetParentId: String?
    ): UploadFileResult {
        val now = System.currentTimeMillis()
        val uploadName = resolveUniqueUploadName(
            parentId = targetParentId,
            requestedName = storedFile.displayName
        )
        val record = storedFile.toUploadFileRecord(
            parentId = targetParentId,
            uploadName = uploadName,
            createdAt = now
        )

        return try {
            UploadFileResult.Uploaded(
                saveUploadedFile(
                    record = record,
                    transferredAt = now
                )
            )
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                throw exception
            }
            privateUploadStorage.delete(storedFile.localPath)
            UploadFileResult.Failed(exception.message)
        }
    }

    // [设计] 为什么这样写：上传同名文件时自动追加编号，能保持演示流程不中断，也避免让 UI 在上传阶段再弹一次重命名对话框。
    private suspend fun resolveUniqueUploadName(parentId: String?, requestedName: String): String {
        val cleanedName = requestedName.ifBlank { DEFAULT_UPLOAD_FILE_NAME }
        if (fileDao.countActiveNameInFolder(parentId, cleanedName, excludeFileId = null) == 0) {
            return cleanedName
        }

        val nameParts = cleanedName.toUploadNameParts()
        var index = 1
        while (true) {
            val candidate = "${nameParts.baseName} ($index)${nameParts.extension}"
            if (fileDao.countActiveNameInFolder(parentId, candidate, excludeFileId = null) == 0) {
                return candidate
            }
            index++
        }
    }

    // [语法] 扩展函数是在不继承 StoredUploadFile 的情况下增加转换能力，类似 Java 静态工具方法 UploadMappers.toRecord(file)。
    // [设计] 为什么这样写：复制结果到入库记录的字段转换集中在 Repository 附近，后续调整 file_id 或时间字段时不影响 UI。
    private fun StoredUploadFile.toUploadFileRecord(
        parentId: String?,
        uploadName: String,
        createdAt: Long
    ): UploadFileRecord {
        return UploadFileRecord(
            fileId = UUID.randomUUID().toString(),
            parentId = parentId,
            name = uploadName,
            type = fileType,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            localPath = localPath,
            originalUri = originalUri,
            createdAt = createdAt
        )
    }

    // [语法] 这是 List<ShareSnapshotFile> 的扩展函数，相当于 Java 静态工具方法 ShareSaveOrder.sort(snapshots)。
    // [设计] 为什么这样写：保存分享时必须先插入父文件夹，再插入子文件；同层文件夹也要先于普通文件，才能恢复层级结构。
    private fun List<ShareSnapshotFile>.toShareSaveOrder(): List<ShareSnapshotFile> {
        return sortedWith(
            compareBy<ShareSnapshotFile> { snapshot -> snapshot.relativePath.pathDepth() }
                .thenByDescending { snapshot -> snapshot.type.storageValue == FileEntity.TYPE_FOLDER }
                .thenBy { snapshot -> snapshot.name.lowercase() }
        )
    }

    // [语法] 这是 ShareSnapshotFile 的扩展函数，相当于 Java 静态工具方法 ShareParents.resolveParentId(snapshot, map)。
    // [设计] 为什么这样写：快照里的 relativePath 表示原始父路径，保存时要把它转换成新生成的本地文件夹 id。
    private fun ShareSnapshotFile.resolveSavedParentId(
        targetParentId: String?,
        savedFolderIdsByOriginalPath: Map<String, String>
    ): String? {
        val parentPath = relativePath?.takeIf { path -> path.isNotBlank() }
            ?: return targetParentId
        return savedFolderIdsByOriginalPath[parentPath]
            ?: error("分享目录结构不完整：$parentPath")
    }

    // [语法] 这是 ShareSnapshotFile 的扩展函数，相当于 Java 静态工具方法 ShareSnapshots.isTopLevel(snapshot)。
    // [设计] 为什么这样写：首页最近转存只记录分享根项，文件夹内部子文件仍会保存到文件表，但不会把最近转存列表刷屏。
    private fun ShareSnapshotFile.isTopLevelShareSnapshot(): Boolean {
        return relativePath.isNullOrBlank()
    }

    // [设计] 为什么这样写：保存分享遇到同名文件时自动追加编号，保持演示流程不中断，也避免覆盖用户已有文件。
    private suspend fun resolveUniqueShareSaveName(
        parentId: String?,
        requestedName: String,
        preserveExtension: Boolean,
        reservedNamesByParent: MutableMap<String?, MutableSet<String>>
    ): String {
        val cleanedName = requestedName.ifBlank { DEFAULT_SHARE_SAVE_FILE_NAME }
        val reservedNames = reservedNamesByParent.getOrPut(parentId) { mutableSetOf() }
        if (cleanedName.isAvailableInFolder(parentId, reservedNames)) {
            reservedNames += cleanedName.lowercase()
            return cleanedName
        }

        val nameParts = cleanedName.toShareSaveNameParts(preserveExtension)
        var index = 1
        while (true) {
            val candidate = "${nameParts.baseName} ($index)${nameParts.extension}"
            if (candidate.isAvailableInFolder(parentId, reservedNames)) {
                reservedNames += candidate.lowercase()
                return candidate
            }
            index++
        }
    }

    // [语法] 这是 String 的扩展函数，相当于 Java 静态工具方法 NameChecks.isAvailableInFolder(name, parentId, reserved)。
    // [设计] 为什么这样写：同名判断既要看数据库已有文件，也要看本次事务里刚准备保存的同级文件。
    private suspend fun String.isAvailableInFolder(
        parentId: String?,
        reservedNames: Set<String>
    ): Boolean {
        return lowercase() !in reservedNames &&
            fileDao.countActiveNameInFolder(parentId, this, excludeFileId = null) == 0
    }

    // [语法] 这是 ShareSnapshotFile 的扩展函数，相当于 Java 静态工具方法 ShareSnapshotMappers.toFileEntity(snapshot)。
    // [设计] 为什么这样写：分享快照到文件表的字段转换集中处理，保存事务只关心“按顺序插入哪些记录”。
    private fun ShareSnapshotFile.toShareSavedEntity(
        fileId: String,
        parentId: String?,
        savedName: String,
        transferredAt: Long
    ): FileEntity {
        return FileEntity(
            fileId = fileId,
            parentId = parentId,
            name = savedName,
            type = type.storageValue,
            mimeType = toShareSavedMimeType(),
            sizeBytes = sizeBytes,
            localPath = localPath,
            originalUri = null,
            createdAt = transferredAt,
            updatedAt = transferredAt,
            openedAt = null,
            transferredAt = transferredAt,
            isDeleted = false,
            isPinned = false,
            source = FileEntity.SOURCE_SHARE_SAVE
        )
    }

    // [语法] 这是 ShareSnapshotFile 的扩展函数，相当于 Java 静态工具方法 MimeTypes.fromSnapshot(snapshot)。
    // [设计] 为什么这样写：分享快照没有单独保存 MIME，保存到网盘时根据类型补一个稳定兜底值，方便后续视频/TXT 打开链路复用。
    private fun ShareSnapshotFile.toShareSavedMimeType(): String? {
        return when (type.storageValue) {
            FileEntity.TYPE_TXT -> "text/plain"
            FileEntity.TYPE_VIDEO -> "video/*"
            FileEntity.TYPE_IMAGE -> "image/*"
            FileEntity.TYPE_AUDIO -> "audio/*"
            else -> null
        }
    }

    // [语法] private data class 只在当前类内部使用，相当于 Java 私有静态小对象。
    // [设计] 为什么这样写：同名追加编号时需要拆出基础名和扩展名，文件保留扩展名，文件夹则把完整名称当基础名。
    private data class ShareSaveNameParts(
        val baseName: String,
        val extension: String
    )

    // [语法] 这是 String 的扩展函数，相当于 Java 静态工具方法 FileNames.toShareSaveNameParts(name, preserveExtension)。
    // [设计] 为什么这样写：分享保存的同名规则和上传类似，但文件夹不能把点号后的内容误当扩展名。
    private fun String.toShareSaveNameParts(preserveExtension: Boolean): ShareSaveNameParts {
        if (!preserveExtension) {
            return ShareSaveNameParts(
                baseName = this,
                extension = ""
            )
        }
        val dotIndex = lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == lastIndex) {
            return ShareSaveNameParts(
                baseName = this,
                extension = ""
            )
        }
        return ShareSaveNameParts(
            baseName = substring(0, dotIndex),
            extension = substring(dotIndex)
        )
    }

    // [语法] 这是 ShareSnapshotFile 的扩展函数，相当于 Java 静态工具方法 SharePaths.originalFullPath(snapshot)。
    // [设计] 为什么这样写：子文件通过原始 relativePath 找父目录，保存文件夹后要把“原始完整路径”映射到新 fileId。
    private fun ShareSnapshotFile.originalFullPath(): String {
        return relativePath.appendPathSegment(name)
    }

    // [语法] 这是 String? 的扩展函数，相当于 Java 静态工具方法 SharePaths.append(path, segment)。
    // [设计] 为什么这样写：分享快照里的 relativePath 使用 / 分隔，统一拼接能避免多层文件夹路径格式不一致。
    private fun String?.appendPathSegment(segment: String): String {
        return if (isNullOrBlank()) {
            segment
        } else {
            "$this/$segment"
        }
    }

    // [语法] 这是 String? 的扩展函数，相当于 Java 静态工具方法 SharePaths.depth(path)。
    // [设计] 为什么这样写：保存排序要按父路径深度处理，根目录深度为 0，多层路径按 / 数量计算。
    private fun String?.pathDepth(): Int {
        return if (isNullOrBlank()) {
            0
        } else {
            split("/").size
        }
    }

    // [语法] private data class 只在当前类内部使用，相当于 Java 里的私有静态小对象。
    // [设计] 为什么这样写：把“基础名”和“扩展名”拆出来，上传同名追加编号时才能保留 .txt/.mp4 这类后缀。
    private data class UploadNameParts(
        val baseName: String,
        val extension: String
    )

    // [语法] 扩展函数是在不继承 String 的情况下给 String 增加工具方法，类似 Java StringUtils.splitName(name)。
    // [设计] 为什么这样写：同名编号规则要保留最后一个扩展名，集中成函数可以避免不同分支拼出不同风格的文件名。
    private fun String.toUploadNameParts(): UploadNameParts {
        val dotIndex = lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == lastIndex) {
            return UploadNameParts(
                baseName = this,
                extension = ""
            )
        }
        return UploadNameParts(
            baseName = substring(0, dotIndex),
            extension = substring(dotIndex)
        )
    }

    // [语法] companion object 相当于 Java 的 static 常量区。
    // [设计] 为什么这样写：兜底文件名只服务上传流程，放在 Repository 实现附近比散落在调用方更容易维护。
    companion object {
        private const val DEFAULT_UPLOAD_FILE_NAME = "未命名文件"
        private const val DEFAULT_SHARE_SAVE_FILE_NAME = "未命名分享文件"
    }
}
