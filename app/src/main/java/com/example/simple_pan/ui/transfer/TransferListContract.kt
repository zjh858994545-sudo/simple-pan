package com.example.simple_pan.ui.transfer

import com.example.simple_pan.domain.model.TransferDirection
import com.example.simple_pan.domain.model.TransferRecord
import com.example.simple_pan.domain.model.TransferStatus

// [语法] enum class 固定 UI 筛选选项，避免用裸 Int 表示“第几个 Tab”。
// [设计] 为什么这样写：全部/已完成/进行中/失败是传输页自己的展示筛选，不应该直接复用领域层 TransferStatus。
enum class TransferStatusFilter {
    All,
    Completed,
    Running,
    Failed
}

// [语法] data class 用来表达不可变 UI 状态，copy 后发给 Compose 重组。
// [设计] 为什么这样写：传输页的顶部方向、状态筛选、真实记录、加载和错误集中管理，Screen 不自己维护零散 remember 状态。
data class TransferListState(
    val selectedDirection: TransferDirection = TransferDirection.Upload,
    val selectedStatusFilter: TransferStatusFilter = TransferStatusFilter.All,
    val records: List<TransferRecord> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val completedCount: Int
        get() = records.count { record -> record.status == TransferStatus.Completed }

    val runningCount: Int
        get() = records.count { record -> record.status == TransferStatus.Running }

    val failedCount: Int
        get() = records.count { record -> record.status == TransferStatus.Failed }

    val visibleRecords: List<TransferRecord>
        get() = when (selectedStatusFilter) {
            TransferStatusFilter.All -> records
            TransferStatusFilter.Completed -> records.filter { record -> record.status == TransferStatus.Completed }
            TransferStatusFilter.Running -> records.filter { record -> record.status == TransferStatus.Running }
            TransferStatusFilter.Failed -> records.filter { record -> record.status == TransferStatus.Failed }
        }
}

// [语法] sealed interface 表示固定的一组用户意图，when 处理时更安全。
// [设计] 为什么这样写：切换上传/下载、切换状态、重试都交给 ViewModel，Composable 只表达用户行为。
sealed interface TransferListIntent {
    data class ChangeDirection(val direction: TransferDirection) : TransferListIntent

    data class ChangeStatusFilter(val filter: TransferStatusFilter) : TransferListIntent

    data object Retry : TransferListIntent
}
