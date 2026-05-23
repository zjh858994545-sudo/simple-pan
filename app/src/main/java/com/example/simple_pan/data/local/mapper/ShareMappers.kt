package com.example.simple_pan.data.local.mapper

import com.example.simple_pan.data.local.entity.ShareEntity
import com.example.simple_pan.data.local.entity.ShareFileSnapshotEntity
import com.example.simple_pan.domain.model.CloudFile
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.ShareBundle

// [语法] 这是扩展函数，相当于 Java 静态方法 ShareMappers.toDomain(share, snapshots)。
// [设计] 为什么这样写：分享页需要一次拿到元信息和快照列表，mapper 负责聚合，Repository 保持业务流程清晰。
fun ShareEntity.toDomain(snapshotFiles: List<CloudFile>): ShareBundle {
    return ShareBundle(
        shareId = shareId,
        token = token,
        title = title,
        shareType = shareType,
        snapshotFiles = snapshotFiles,
        createdAt = createdAt,
        expiredAt = expiredAt,
        ownerMask = ownerMask
    )
}

// [语法] 这是扩展函数，把快照表的一行转换成领域层 CloudFile。
// [设计] 为什么这样写：分享快照不是当前网盘文件，但 UI 展示需要文件名、类型和大小，所以复用 CloudFile 的展示字段。
fun ShareFileSnapshotEntity.toDomainFile(): CloudFile {
    val snapshotFileId = if (sourceFileId == null) {
        "snapshot-$id"
    } else {
        sourceFileId
    }

    return CloudFile(
        fileId = snapshotFileId,
        parentId = null,
        name = name,
        type = FileType.fromStorageValue(type),
        mimeType = null,
        sizeBytes = sizeBytes,
        localPath = localPath,
        createdAt = 0L,
        updatedAt = 0L,
        openedAt = null,
        transferredAt = null,
        isPinned = false,
        source = "share_snapshot"
    )
}
