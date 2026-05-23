package com.example.simple_pan.domain.model

// [语法] data class 相当于 Java 的 POJO/Bean，适合把首页最近记录作为一个不可变值传给 UI。
// [设计] 为什么这样写：最近浏览和最近转存都服务首页，但来源不同；统一模型能让 PanHomeState 后续用一套列表组件展示。
data class RecentRecord(
    val fileId: String,
    val fileName: String,
    val fileType: FileType,
    val timestamp: Long,
    val recordType: RecordType,
    val progress: Long?,
    val transferType: String?
) {
    // [语法] enum class 和 Java enum 类似，适合表达固定的记录类型。
    // [设计] 为什么这样写：用枚举区分浏览和转存，比字符串更适合给 UI 做分支展示，也方便答辩说明两个历史表的差异。
    enum class RecordType {
        Open,
        Transfer
    }
}
