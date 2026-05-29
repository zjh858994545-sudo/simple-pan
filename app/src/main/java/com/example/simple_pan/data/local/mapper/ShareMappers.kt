package com.example.simple_pan.data.local.mapper

import com.example.simple_pan.data.local.entity.ShareEntity
import com.example.simple_pan.data.local.entity.ShareFileSnapshotEntity
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.ShareSnapshotFile
import com.example.simple_pan.domain.model.ShareBundle
import com.example.simple_pan.domain.model.ShareType

// [语法] 这是扩展函数，相当于 Java 静态方法 ShareMappers.toDomain(share, snapshots)。
// [设计] 为什么这样写：分享页需要一次拿到元信息和快照列表，mapper 负责聚合，Repository 保持业务流程清晰。
fun ShareEntity.toDomain(snapshotFiles: List<ShareSnapshotFile>): ShareBundle {
    return ShareBundle(
        shareId = shareId,
        token = token,
        title = title,
        shareType = ShareType.fromStorageValue(shareType),
        snapshotFiles = snapshotFiles,
        createdAt = createdAt,
        expiredAt = expiredAt,
        ownerMask = ownerMask
    )
}

// [语法] 这是扩展函数，把快照表的一行转换成领域层 ShareSnapshotFile。
// [设计] 为什么这样写：分享快照有 relativePath/localPath 等分享专属字段，单独领域模型比复用 CloudFile 更清楚，也避免把快照误当成真实网盘文件。
fun ShareFileSnapshotEntity.toDomainSnapshot(): ShareSnapshotFile {
    return ShareSnapshotFile(
        snapshotId = id,
        sourceFileId = sourceFileId,
        name = name,
        type = FileType.fromStorageValue(type),
        sizeBytes = sizeBytes,
        relativePath = relativePath,
        localPath = localPath
    )
}

// [语法] 这是 ShareSnapshotFile 的扩展函数，相当于 Java 静态方法 ShareMappers.toEntity(snapshot, shareId)。
// [设计] 为什么这样写：创建分享时上层只给领域快照，mapper 统一决定如何落到快照表，避免 Repository 手写字段映射。
fun ShareSnapshotFile.toEntity(shareId: String): ShareFileSnapshotEntity {
    return ShareFileSnapshotEntity(
        shareId = shareId,
        sourceFileId = sourceFileId,
        name = name,
        type = type.storageValue,
        sizeBytes = sizeBytes,
        relativePath = relativePath,
        localPath = localPath
    )
}
