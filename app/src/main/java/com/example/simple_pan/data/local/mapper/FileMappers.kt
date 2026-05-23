package com.example.simple_pan.data.local.mapper

import com.example.simple_pan.data.local.entity.FileEntity
import com.example.simple_pan.data.remote.dto.FileDto
import com.example.simple_pan.domain.model.CloudFile
import com.example.simple_pan.domain.model.FileType

// [语法] 这是扩展函数，相当于 Java 里的静态工具方法 FileMappers.toEntity(fileDto)。
// [设计] 为什么这样写：DTO 到 Entity 的转换集中在 mapper，Repository 只表达数据流，不堆字段搬运细节。
fun FileDto.toEntity(): FileEntity {
    return FileEntity(
        fileId = fileId,
        parentId = parentId,
        name = name,
        type = type,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        localPath = localPath,
        originalUri = originalUri,
        createdAt = createdAt,
        updatedAt = updatedAt,
        openedAt = openedAt,
        transferredAt = transferredAt,
        isDeleted = isDeleted,
        isPinned = isPinned,
        source = source
    )
}

// [语法] 这是扩展函数，给 FileEntity 增加 toDomain 能力；Java 里通常会写成 Mapper.toDomain(entity)。
// [设计] 为什么这样写：data 层可以认识 domain 层模型，但 domain 层不认识 Room Entity，从而保持依赖方向向内。
fun FileEntity.toDomain(): CloudFile {
    return CloudFile(
        fileId = fileId,
        parentId = parentId,
        name = name,
        type = FileType.fromStorageValue(type),
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        localPath = localPath,
        createdAt = createdAt,
        updatedAt = updatedAt,
        openedAt = openedAt,
        transferredAt = transferredAt,
        isPinned = isPinned,
        source = source
    )
}
