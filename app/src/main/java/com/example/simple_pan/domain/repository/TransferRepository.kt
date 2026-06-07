package com.example.simple_pan.domain.repository

import com.example.simple_pan.domain.model.TransferDirection
import com.example.simple_pan.domain.model.TransferRecord
import kotlinx.coroutines.flow.Flow

// [设计] 为什么这样写：传输页依赖 domain 接口，不直接依赖 Room DAO；后续如果新增真实任务表，只替换 data 实现即可。
interface TransferRepository {
    // [语法] Flow<List<TransferRecord>> 类似 Java Observable，会在历史表或文件表变化后自动推送新列表。
    // [设计] 为什么这样写：上传/保存分享成功后传输页应自动刷新，UI 不需要手动 reload。
    fun observeTransferRecords(direction: TransferDirection): Flow<List<TransferRecord>>
}
