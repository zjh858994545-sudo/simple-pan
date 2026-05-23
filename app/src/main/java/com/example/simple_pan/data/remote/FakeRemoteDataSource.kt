package com.example.simple_pan.data.remote

import android.content.Context
import com.example.simple_pan.data.remote.dto.FileDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

// [设计] 为什么这样写：FakeRemoteDataSource 模拟“远端返回 JSON”的边界，让阶段 1 也遵守 mock JSON -> Repository -> Room -> UI 的数据流。
class FakeRemoteDataSource @Inject constructor(
    // [语法] @param: 指定注解作用在构造参数上，类似 Java 里把注解明确写在构造函数参数前，避免 Kotlin 未来版本改变默认目标。
    // [设计] 为什么这样写：读取 assets 需要 Application 级 Context；用 @ApplicationContext 避免持有 Activity，降低生命周期泄漏风险。
    @param:ApplicationContext private val context: Context
) {
    // [语法] suspend fun 是 Kotlin 协程函数，类似 Java 的 Future/回调异步方法，但在协程里能像同步代码一样顺序调用。
    // [设计] 为什么这样写：读取 assets 和解析 JSON 属于 IO/CPU 工作，函数声明为 suspend；真正切到 IO 线程由后续 Repository 注入 Dispatcher 统一负责。
    suspend fun loadFiles(): List<FileDto> {
        // [语法] use { reader -> ... } 是 Kotlin 的扩展函数加尾随 lambda，类似 Java try-with-resources，会自动关闭 reader。
        // [设计] 为什么这样写：assets 文件流必须及时关闭，否则后续初始化和测试反复读取时容易泄漏文件句柄。
        val jsonText = context.assets.open(MOCK_FILE_NAME)
            .bufferedReader()
            .use { reader -> reader.readText() }

        val root = JSONObject(jsonText)
        return root.getJSONArray(FILES_KEY).toFileDtos()
    }

    // [语法] 这是扩展函数，给 JSONArray “补充” toFileDtos 方法；Java 里通常会写成 JsonParsers.toFileDtos(array) 这样的静态工具方法。
    // [设计] 为什么这样写：把数组遍历逻辑收在数据源内部，Repository 只拿到 List<FileDto>，不需要知道 JSON 细节。
    private fun JSONArray.toFileDtos(): List<FileDto> {
        val files = mutableListOf<FileDto>()
        for (index in 0 until length()) {
            files += getJSONObject(index).toFileDto()
        }
        return files
    }

    // [语法] 这是 JSONObject 的扩展函数，相当于 Java 静态方法 parseFileDto(JSONObject json)。
    // [设计] 为什么这样写：单个文件的字段解析集中在这里，后续 mock JSON 增减字段时只改这一处。
    private fun JSONObject.toFileDto(): FileDto {
        return FileDto(
            fileId = getString("fileId"),
            parentId = optNullableString("parentId"),
            name = getString("name"),
            type = getString("type"),
            mimeType = optNullableString("mimeType"),
            sizeBytes = getLong("sizeBytes"),
            localPath = optNullableString("localPath"),
            originalUri = optNullableString("originalUri"),
            createdAt = getLong("createdAt"),
            updatedAt = getLong("updatedAt"),
            openedAt = optNullableLong("openedAt"),
            transferredAt = optNullableLong("transferredAt"),
            isDeleted = getBoolean("isDeleted"),
            isPinned = getBoolean("isPinned"),
            source = getString("source")
        )
    }

    // [语法] String? 是 Kotlin 空安全类型；返回 null 时调用方必须显式处理，类似 Java 里返回 @Nullable String。
    // [设计] 为什么这样写：JSON 里的 null 和缺失字段都代表“没有这个信息”，统一转成 Kotlin null，避免把 "null" 或空串写进数据库。
    private fun JSONObject.optNullableString(name: String): String? {
        return if (!has(name) || isNull(name)) {
            null
        } else {
            getString(name)
        }
    }

    // [语法] Long? 是可空 Long，相当于 Java 的 Long；非可空 Long 更接近 Java 的 long。
    // [设计] 为什么这样写：openedAt/transferredAt 在初始 mock 数据里通常不存在，保留 null 能让首页历史由真实操作驱动。
    private fun JSONObject.optNullableLong(name: String): Long? {
        return if (!has(name) || isNull(name)) {
            null
        } else {
            getLong(name)
        }
    }

    // [语法] companion object 相当于 Java 的 static 常量区域。
    // [设计] 为什么这样写：文件名和 JSON key 集中定义，后续替换 mock 文件或调整根字段时不会在解析逻辑里到处找字符串。
    companion object {
        private const val MOCK_FILE_NAME = "mock_files.json"
        private const val FILES_KEY = "files"
    }
}
