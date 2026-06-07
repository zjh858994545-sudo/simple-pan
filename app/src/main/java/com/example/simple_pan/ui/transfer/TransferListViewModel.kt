package com.example.simple_pan.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simple_pan.domain.model.TransferDirection
import com.example.simple_pan.domain.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// [语法] @HiltViewModel 表示这个 ViewModel 由 Hilt 创建，构造参数自动注入。
// [设计] 为什么这样写：传输页不直接查 DAO，而是通过 domain 层 TransferRepository 观察真实历史数据。
@HiltViewModel
class TransferListViewModel @Inject constructor(
    private val transferRepository: TransferRepository
) : ViewModel() {
    private val _state = MutableStateFlow(TransferListState())
    val state: StateFlow<TransferListState> = _state.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeTransfers(direction = TransferDirection.Upload)
    }

    fun onIntent(intent: TransferListIntent) {
        when (intent) {
            is TransferListIntent.ChangeDirection -> {
                changeDirection(intent.direction)
            }
            is TransferListIntent.ChangeStatusFilter -> {
                _state.update { currentState ->
                    currentState.copy(selectedStatusFilter = intent.filter)
                }
            }
            TransferListIntent.Retry -> {
                observeTransfers(direction = _state.value.selectedDirection)
            }
        }
    }

    private fun changeDirection(direction: TransferDirection) {
        if (_state.value.selectedDirection == direction) {
            return
        }
        observeTransfers(direction = direction)
    }

    private fun observeTransfers(direction: TransferDirection) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(
                    selectedDirection = direction,
                    selectedStatusFilter = TransferStatusFilter.All,
                    records = emptyList(),
                    isLoading = true,
                    errorMessage = null
                )
            }

            try {
                // [语法] collect 会持续收集 Flow；历史表或文件表变化后这里会再次收到最新记录。
                // [设计] 为什么这样写：传输页展示真实历史，上传成功或保存分享成功后，Room 会驱动页面自动刷新。
                transferRepository.observeTransferRecords(direction).collect { records ->
                    _state.update { currentState ->
                        currentState.copy(
                            records = records,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
            } catch (throwable: Throwable) {
                _state.update { currentState ->
                    currentState.copy(
                        records = emptyList(),
                        isLoading = false,
                        errorMessage = throwable.toTransferMessage()
                    )
                }
            }
        }
    }

    // [语法] 这是 Throwable 的扩展函数，相当于 Java 静态工具方法 TransferErrors.toMessage(throwable)。
    // [设计] 为什么这样写：传输页失败时统一给用户可读文案，避免直接展示数据库异常细节。
    private fun Throwable.toTransferMessage(): String {
        val detail = message
        return if (detail.isNullOrBlank()) {
            "传输记录加载失败，请重试"
        } else {
            "传输记录加载失败：$detail"
        }
    }
}
