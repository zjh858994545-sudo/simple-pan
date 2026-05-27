package com.example.simple_pan.domain.model

// [语法] sealed interface 表示受限结果类型，类似 Java 里固定子类集合的抽象父类型。
// [设计] 为什么这样写：TXT 读取有多种可解释失败原因，用结构化结果比直接抛异常更适合 ViewModel 转成明确 UI 文案。
sealed interface ReadTxtFileResult {
    // [语法] data class 相当于 Java 的只读结果 Bean，自动生成 equals/hashCode/toString/copy。
    // [设计] 为什么这样写：读取成功后同时返回最新文件名和文本内容，阅读器标题能跟随数据库里的真实名称，而不是只依赖路由参数。
    data class Loaded(
        val fileId: String,
        val fileName: String,
        val content: String
    ) : ReadTxtFileResult

    // [语法] object 是 Kotlin 单例，适合表达没有额外字段的固定结果。
    // [设计] 为什么这样写：文件可能在进入阅读器前后被删除，单独建模能显示“文件不存在或已被删除”。
    object FileNotFound : ReadTxtFileResult

    // [设计] 为什么这样写：mock 数据或异常记录可能没有本地路径，阅读器必须提前拦截，避免进入空白页面。
    object LocalPathMissing : ReadTxtFileResult

    // [设计] 为什么这样写：Room 记录存在不代表磁盘文件还在，单独提示用户重新上传更容易理解。
    object LocalFileMissing : ReadTxtFileResult

    // [设计] 为什么这样写：阅读器只处理 TXT，其他类型通过明确结果返回，避免把类型判断散落在 UI 中。
    data class UnsupportedType(val fileType: FileType) : ReadTxtFileResult

    // [设计] 为什么这样写：编码失败属于用户可感知异常，单独返回能提示用户换成受支持编码，而不是显示乱码。
    object DecodeFailed : ReadTxtFileResult

    // [设计] 为什么这样写：磁盘读取可能出现权限或 IO 异常，保留底层 message 便于开发阶段定位问题。
    data class Failed(val message: String?) : ReadTxtFileResult
}
