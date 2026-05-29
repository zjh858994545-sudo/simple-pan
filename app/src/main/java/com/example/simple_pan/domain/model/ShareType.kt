package com.example.simple_pan.domain.model

// [语法] enum class 和 Java enum 类似，用固定枚举值表达有限分享类型。
// [设计] 为什么这样写：分享类型会影响快照生成和分享页展示，用领域枚举比在上层散落字符串更安全；存库时再转换成 storageValue。
enum class ShareType(val storageValue: String) {
    SingleFile("single_file"),
    Folder("folder"),
    MultiFile("multi_file");

    // [语法] companion object 相当于 Java 的 static 工具区，调用方可以用 ShareType.fromStorageValue(...)。
    // [设计] 为什么这样写：数据库保存字符串，领域层使用枚举，集中转换能避免未知值导致崩溃。
    companion object {
        fun fromStorageValue(value: String): ShareType {
            for (type in entries) {
                if (type.storageValue == value) {
                    return type
                }
            }
            return MultiFile
        }
    }
}
