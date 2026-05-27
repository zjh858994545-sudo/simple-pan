package com.example.simple_pan.domain.usecase

import com.example.simple_pan.domain.repository.RecentRepository
import javax.inject.Inject

// [设计] 为什么这样写：记录“最近浏览”是 TXT 阅读器和视频播放器都会用到的业务动作，抽成 UseCase 能避免两个 ViewModel 分别拼 openedAt/progress 规则。
class RecordOpenUseCase @Inject constructor(
    private val recentRepository: RecentRepository
) {
    // [语法] suspend operator fun invoke 让调用方可以写 recordOpenUseCase(fileId)，类似 Java 里调用一个单方法服务对象。
    // [设计] 为什么这样写：浏览历史写入由 RecentRepository 负责事务和 IO 线程，UseCase 只统一业务入口和当前时间策略。
    suspend operator fun invoke(fileId: String, progress: Long? = null) {
        recentRepository.recordOpen(
            fileId = fileId,
            openedAt = System.currentTimeMillis(),
            progress = progress
        )
    }
}
