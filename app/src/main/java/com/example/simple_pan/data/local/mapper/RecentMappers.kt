package com.example.simple_pan.data.local.mapper

import com.example.simple_pan.data.local.entity.OpenHistoryEntity
import com.example.simple_pan.data.local.entity.TransferHistoryEntity
import com.example.simple_pan.data.local.dao.TransferHistoryWithFile
import com.example.simple_pan.domain.model.CloudFile
import com.example.simple_pan.domain.model.FileType
import com.example.simple_pan.domain.model.RecentRecord

// [语法] 这是扩展函数，相当于 Java 静态方法 RecentMappers.toRecentRecord(history, file)。
// [设计] 为什么这样写：历史表只保存行为，文件名和类型来自文件表；mapper 把两者合成首页真正需要的模型。
fun OpenHistoryEntity.toRecentRecord(file: CloudFile): RecentRecord {
    return RecentRecord(
        fileId = file.fileId,
        fileName = file.name,
        fileType = file.type,
        timestamp = openedAt,
        recordType = RecentRecord.RecordType.Open,
        progress = progress,
        transferType = null
    )
}

// [语法] 这是扩展函数，让 TransferHistoryEntity 可以直接转换成领域层 RecentRecord。
// [设计] 为什么这样写：上传和分享保存都用同一个最近转存 UI，但 transferType 仍保留来源信息，答辩时能说明两类行为没有混淆。
fun TransferHistoryEntity.toRecentRecord(file: CloudFile): RecentRecord {
    return RecentRecord(
        fileId = file.fileId,
        fileName = file.name,
        fileType = file.type,
        timestamp = transferredAt,
        recordType = RecentRecord.RecordType.Transfer,
        progress = null,
        transferType = transferType
    )
}

// [语法] 这是扩展函数，相当于 Java 静态方法 RecentMappers.toRecentRecord(projection)。
// [设计] 为什么这样写：join 查询已经拿到首页展示所需字段，直接映射成 RecentRecord，可以减少 Repository 的 N+1 次文件查询。
fun TransferHistoryWithFile.toRecentRecord(): RecentRecord {
    return RecentRecord(
        fileId = fileId,
        fileName = fileName,
        fileType = FileType.fromStorageValue(fileType),
        timestamp = transferredAt,
        recordType = RecentRecord.RecordType.Transfer,
        progress = null,
        transferType = transferType
    )
}
