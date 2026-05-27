package com.example.simple_pan.domain.usecase

import com.example.simple_pan.domain.model.UploadFileResult
import com.example.simple_pan.domain.repository.FileRepository
import javax.inject.Inject

// [设计] 为什么这样写：ViewModel 只需要表达“把这个 Uri 上传到当前目录”，具体读取、复制和写库由 Repository 链路完成，UI 层不接触文件系统细节。
class UploadFileUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    // [语法] suspend operator fun invoke 让调用方可以写 uploadFileUseCase(...)，类似 Java 里调用一个单方法服务对象。
    // [设计] 为什么这样写：上传是异步业务动作，但 ViewModel 可以像顺序代码一样调用，状态更新留在下一步 UI 接入时处理。
    suspend operator fun invoke(
        uriString: String,
        targetFolderId: String?
    ): UploadFileResult {
        return fileRepository.uploadFromUri(
            uriString = uriString,
            targetParentId = targetFolderId
        )
    }
}
