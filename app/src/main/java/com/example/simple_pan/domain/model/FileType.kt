package com.example.simple_pan.domain.model

// [语法] enum class 和 Java enum 类似，用一组固定常量表达有限取值。
// [设计] 为什么这样写：文件类型会被列表筛选、打开方式和图标展示共同使用，集中成领域枚举比到处比较字符串更安全。
enum class FileType(val storageValue: String) {
    Folder("folder"),
    Video("video"),
    Txt("txt"),
    Image("image"),
    Audio("audio"),
    Other("other");

    // [语法] companion object 相当于 Java 的 static 工具区，调用方可以用 FileType.fromStorageValue(...)。
    // [设计] 为什么这样写：数据库和 JSON 用字符串存储，领域层用枚举表达，转换入口集中后能防止未知类型导致崩溃。
    companion object {
        fun fromStorageValue(value: String): FileType {
            for (type in entries) {
                if (type.storageValue == value) {
                    return type
                }
            }
            return Other
        }
    }
}
