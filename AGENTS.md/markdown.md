markdown

```markdown
# Codex 协作约束（强制）

本工程是一个三周的 Android 教学项目,所有开发节奏服从评分需求,而非工程效率。
你（Codex）必须严格遵守以下规则,任何违反都会被回滚。

## 一、节奏约束

1. **一次只做一个小步骤**。即使你能预见后面 5 步,也只做当前这一步。
2. **每一步完成后必须停下**,等待开发者说"通过,下一步"才继续。
3. **不要主动跨模块改文件**。如果某一步需要的修改超出当前模块,先提示开发者
   "需要额外修改 X 文件,是否继续"。
4. **不要主动添加未在 docs/DEVELOPER_GUIDE.md 中列出的依赖**。需要新依赖时
   先提示开发者并说明理由。

## 二、报告格式

每完成一步,必须用以下格式向开发者汇报:
```

【本步骤完成】

- 创建文件: [文件路径列表]
- 修改文件: [文件路径列表]
- 关键决策: [1-3 条,说明为什么这样写]
- 验证方法: [开发者应该运行什么命令、看到什么]
- 下一步建议: [一句话]



```
## 三、代码质量

1. **关键设计决策必须加中文注释**——这些注释是开发者答辩素材的一部分。
   注释要说"为什么这样写",不要重复代码本身在做什么。
2. **遵守 docs/DEVELOPER_GUIDE.md 的分层结构**。所有新文件必须落在正确的
   目录(ui/domain/data/di/navigation/deeplink/util)下。
3. **不要写硬编码假数据到 Composable**。数据必须从 Room 经 ViewModel 到 UI。
   只有 Preview 函数允许写假数据。
4. **依赖注入统一用 Hilt**,不要用手动 new 或 Service Locator。
5. **协程切线程统一用注入的 Dispatcher**(@IoDispatcher 等),不要直接写
   `Dispatchers.IO`。

## 四、Commit 约束

1. 每一步完成后,**不要自动 git add/commit/push**。开发者会人工 commit。
2. commit message 规范见 docs/DEVELOPER_GUIDE.md §6.4。

## 五、知识来源优先级

1. docs/DEVELOPER_GUIDE.md 是唯一权威。
2. 如果 DEVELOPER_GUIDE 没明确,问开发者,不要凭训练数据猜。
3. 涉及 Android/Kotlin/Compose 官方最佳实践且 DEVELOPER_GUIDE 未提及的,
   按官方文档写并在汇报中说明。

## 六、禁止行为

- 禁止删除或修改 docs/ 目录下任何文件(除非开发者明确要求)
- 禁止修改 .gitignore、.git/ 相关任何东西
- 禁止跑 git push
- 禁止在没有开发者确认的情况下执行任何破坏性命令(rm -rf、git reset --hard 等)


从现在开始，所有代码必须加详细中文注释，要求：

1. 【语法注释】我只会 Java，不熟悉 Kotlin。遇到以下 Kotlin 语法时必须注释说明：
   - data class（对比 Java 的 POJO/Bean）
   - sealed interface / sealed class（对比 Java 的枚举或抽象类）
   - suspend fun（对比 Java 的回调或 Future）
   - Flow<T>（对比 Java 的 Observable 或 Stream）
   - by lazy / by inject
   - 扩展函数
   - 作用域函数（let、apply、also、run、with）
   - 尾随 lambda、it 关键字
   - ?. 和 ?: 安全调用
   - object 单例
   - companion object（对比 Java 的 static）

2. 【设计意图注释】每个类/函数/关键代码块前写一段"为什么这样写"，不要写"做了什么"（代码本身已经表达了做什么）。

3. 【注释格式】
   // [语法] 这是 Kotlin 的 xxx，相当于 Java 的 xxx
   // [设计] 为什么这样写：xxx

示例：
​```kotlin
// [语法] data class 相当于 Java 的 POJO，自动生成 equals/hashCode/toString/copy
// [设计] 用不可变 data class 表示 UI 状态，保证 Compose 能正确检测状态变化触发重组
data class FileListState(
    val files: List<CloudFileUiModel> = emptyList(),
    // [语法] ?: 是 Kotlin 空安全语法，String? 表示可以为 null，相当于 Java 的 @Nullable String
    val errorMessage: String? = null
)
​```
```