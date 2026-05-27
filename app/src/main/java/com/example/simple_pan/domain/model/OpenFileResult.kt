package com.example.simple_pan.domain.model

// [语法] sealed interface 表示“受限的接口继承体系”，类似 Java 里固定子类集合的抽象父类型。
// [设计] 为什么这样写：打开文件会分成“进入 TXT 阅读器”“拉起视频播放器”和多种失败原因，结构化结果比 Boolean/异常更适合给 ViewModel 显示明确提示。
sealed interface OpenFileResult {
    // [语法] data class 用来携带 TXT 阅读器需要的最小信息，相当于 Java 里只读的结果对象。
    // [设计] 为什么这样写：TXT 阅读器后续按 fileId 进入，但提前校验 localPath 能把“文件丢失”挡在跳转前，避免进入空白阅读页。
    data class ReadyForTxtReader(
        val fileId: String,
        val fileName: String,
        val localPath: String
    ) : OpenFileResult

    // [设计] 为什么这样写：视频播放器后续需要本地路径生成 content Uri，也需要 MIME 类型构造系统 Intent，所以这里把两者都准备好。
    data class ReadyForVideoPlayer(
        val fileId: String,
        val fileName: String,
        val localPath: String,
        val mimeType: String
    ) : OpenFileResult

    // [语法] object 是 Kotlin 单例对象，适合表达没有额外字段的固定失败原因。
    // [设计] 为什么这样写：文件可能被软删除或不存在，单独返回能让 UI 提示“文件不存在或已被删除”。
    object FileNotFound : OpenFileResult

    // [设计] 为什么这样写：mock 文件或目录可能没有私有目录路径，打开前必须拦截，否则后续 FileProvider 或阅读器会拿不到真实文件。
    object LocalPathMissing : OpenFileResult

    // [设计] 为什么这样写：Room 里有记录不代表磁盘文件还存在，单独建模方便提示用户重新上传。
    object LocalFileMissing : OpenFileResult

    // [设计] 为什么这样写：当前阶段只支持 TXT 和视频，其它类型保持可解释失败，不把打开逻辑散落在 UI 分支里。
    data class UnsupportedType(val fileType: FileType) : OpenFileResult
}
