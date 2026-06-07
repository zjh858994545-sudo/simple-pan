package com.example.simple_pan.domain.model

// [语法] enum class 和 Java enum 类似，用一组固定常量表达有限选项。
// [设计] 为什么这样写：传输页顶部只有“上传/下载”两个方向，用枚举比传字符串更安全，也方便 ViewModel 切换数据源。
enum class TransferDirection {
    Upload,
    Download
}

// [语法] enum class 让状态筛选只可能取固定值，when 分支能被编译器检查。
// [设计] 为什么这样写：当前 transfer_history 只记录已完成结果，但 UI 已有全部/已完成/进行中/失败四个筛选，先把状态建模出来方便后续接任务表。
enum class TransferStatus {
    Completed,
    Running,
    Failed
}

// [语法] data class 相当于 Java 的只读 Bean，适合从 Repository 传给 UI。
// [设计] 为什么这样写：传输页不应该直接展示 Room 投影对象；领域模型能隐藏 transfer_history/file_entity 的 join 细节。
data class TransferRecord(
    val historyId: Long,
    val fileId: String,
    val fileName: String,
    val fileType: FileType,
    val sizeBytes: Long,
    val transferredAt: Long,
    val transferType: String,
    val direction: TransferDirection,
    val status: TransferStatus
)
