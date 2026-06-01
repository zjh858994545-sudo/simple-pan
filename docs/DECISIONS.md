# 项目决策日志

本文件记录开发过程中的关键技术决策。每条决策包含背景、备选方案、最终选择和验证方法。
目标：开发结束时累计 15-25 条决策。

什么时候新增一条：
1. 放弃一个看似可行的方案时
2. 引入一个新库或新架构时
3. 解决一个异常场景时
4. 从 v1 优化到 v2 时
5. 为了评分权重调整优先级时

---

# D-001 选择 Android + Kotlin + Compose 而非 iOS 或 KMP

- **日期：** 2026-5-23（开工前填写）
- **背景：** 项目要求 Android 或 iOS 任选其一，拓展项写明 "KMP + MVI 架构"。我需要在三周内交付一个能体现客户端核心能力的项目。
- **备选方案：**
  1. **Android + Kotlin + Compose**：符合项目"必须使用 Compose"的硬要求，Kotlin 生态对协程、Flow、Room 支持完整，社区资源丰富。
  2. **iOS + SwiftUI**：声明式 UI 同样合规，但我对 iOS 工具链（Xcode、CocoaPods、签名机制）不熟悉，学习成本高。
  3. **KMP + Compose Multiplatform**：作为拓展项可拿 5% 加分，但 KMP 的 expect/actual 机制、平台资源处理、依赖图都需要额外学习时间，三周内风险过高。
- **决策：** 选择方案 1（Android + Kotlin + Compose）。
- **代价：**
  - 放弃拓展项 1 中的 KMP 部分；保留 MVI 作为架构亮点拓展项。
  - 不能展示跨平台能力，但这不在评分核心权重内。
- **验证：** 阶段 1-6 主线在三周内能稳定完成，即视为决策正确。
- **关联：** v3 开发指南 §2.1、§1.3

---

# D-002 选择 MVI 架构而非纯 MVVM

- **日期：** 2026-5-23（开工前填写）
- **背景：** 文件列表页是项目里状态最复杂的页面。它至少要承载两种模式（浏览模式、管理模式），管理模式下还需要联动维护多个状态：`isManaging` 标记是否处于管理态、`selectedIds` 记录勾选了哪些文件、底部操作栏是否可见、是否全选、当前筛选类型、当前排序方式等。如果用 MVVM 在 ViewModel 里散落维护多个独立的 StateFlow，状态之间很容易不一致——例如退出管理态时忘记清空 selectedIds，导致下次进入管理态时残留旧选中状态。我需要一种能让状态变化"显式化、可重放、可解释"的架构。考虑到老师明确强调评分会重点看架构思考和"为什么这么设计"，架构选择本身就是答辩素材的一部分。
- **备选方案：**
  1. **MVVM + 多个独立 StateFlow**：
     - 优势：写起来直接，每个状态字段一个 StateFlow，不需要额外定义 Intent 类。我之前后端开发的命令式思维也更习惯这种方式。
     - 担心的问题：状态字段之间隐含联动关系（例如"退出管理态必须清空 selectedIds"）只能靠开发者记住，没有结构约束；一旦页面状态超过 5 个字段就容易出现"忘了同步更新"的 bug；答辩时讲"管理态怎么实现"很难讲清楚整体状态机。
  2. **MVI 单一 State + Intent + Effect**：
     - 优势：所有 UI 状态收敛到一个不可变 `FileListState` data class 里，`state.update { it.copy(isManaging = false, selectedIds = emptySet()) }` 这种写法让"退出管理态要清空选中"在代码里显式存在；用户行为统一通过 `onIntent(intent)` 入口，方便追踪和单元测试；一次性副作用（Toast、导航、打开播放器）通过 Effect 分离，避免重组时重复触发。
     - 担心的代价：样板代码确实多（每个用户操作都要定义一个 Intent 子类）；初期开发节奏会比 MVVM 慢；对我这个客户端新手来说，第一次写 MVI 会有学习成本。
- **决策：** 选择方案 2（MVI）。
- **代价：**
  - 阶段 1-2 开发速度会比 MVVM 慢一些，因为要先搭 State / Intent / Effect 的脚手架。
  - Codex 生成代码时需要更明确的约束，否则可能生成出混合了 MVVM 写法的代码。
  - 简单的页面（例如设置页、关于页）用 MVI 显得过度设计，所以约定：只有文件列表、分享预览这种状态复杂的页面用完整 MVI，简单页面允许退化为轻量 ViewModel。
- **验证：**
  - 实现管理态进入/退出时，`selectedIds` 状态在代码层面就能看出"必然被清空"，不依赖开发者记忆。
  - 删除、移动、上传文件后，文件列表自动刷新（因为 State 来自 Room Flow），不需要任何手动 refresh。
  - 答辩时能用 `FileListState` 这一个 data class，把页面所有可能的状态组合一次讲完。
- **关联：** v3 开发指南 §2.2、§3.4
- **关联 commit：** `feat(file): 实现 FileListState/Intent/Effect 骨架 #D-002`（阶段 1-7 步完成后补编号）

---

# D-003 选择 Room + Flow 实现响应式数据更新

- **日期：** 2026-5-23
- **背景：** 项目明确要求 "json → 数据库 → 数据结构" 的数据流，所有 UI 数据必须从数据库读取而不是硬编码。这意味着上传、删除、移动、重命名、保存分享等几乎所有用户操作都会修改数据库，相应的 UI（文件列表、首页最近浏览、首页最近转存）必须随之刷新。我之前做后端时一直是命令式思维——增删改查后手动通知 UI 刷新，没有用过响应式编程框架。但客户端这种"用户行为密集 + UI 状态多处依赖同一份数据"的场景，手动通知很容易出现遗漏：例如分享保存写入 transfer_history 后，开发者要记得通知"文件列表"和"首页最近转存"两处刷新，漏一个就是 bug。我需要一种"数据变化 → UI 自动重渲染"的机制，把"通知刷新"这件事从开发者的记忆里移到框架的责任里。
- **备选方案：**
  1. **手动 refresh()**：
     - 实现方式：Repository 提供一次性查询方法，每个修改数据的地方调用完后手动调 `viewModel.refresh()` 重新拉数据。
     - 优势：和我后端开发经验一致，符合命令式直觉；不需要学新概念。
     - 担心的问题：每个修改入口（上传、删除、移动、重命名、分享保存）都要记得通知所有相关 UI；上传文件后要同时刷新"文件列表"和"首页最近转存"，漏一个就是 bug；这种"开发者记忆驱动"的代码不可靠，三周项目里很可能出现演示时某个页面没刷新的尴尬。
  2. **Room DAO 返回 Flow + UI 用 `collectAsStateWithLifecycle` 订阅**：
     - 实现方式：DAO 定义 `fun observeFiles(parentId: String): Flow<List<FileEntity>>`，Room 内部的 `InvalidationTracker` 监听表变化，事务提交后自动让 Flow 重新发射；UI 通过 `collectAsStateWithLifecycle` 订阅，状态变化自动重组。
     - 优势：开发者只管"把数据写对"，"通知 UI"是 Room + Flow 的责任；同一个 Flow 可以被多处订阅（文件列表订阅 file_entity，首页订阅 transfer_history），互不干扰；和我选的 MVI 架构天然匹配——State 来自 Flow，Flow 变了 State 就变了。
     - 担心的代价：要理解协程冷热流、`stateIn`、`flowOn(Dispatchers.IO)` 这些概念；调试时数据流路径比命令式长（数据库 → Flow → ViewModel → State → UI），出问题不容易定位。
  3. **LiveData**：
     - 实现方式：DAO 返回 `LiveData<List<FileEntity>>`，UI 用 `observeAsState()` 订阅。
     - 为什么不选：LiveData 是 Android 早期方案，绑定 Android Lifecycle 类，纯 Kotlin 模块（例如 Repository 单元测试）使用不便；不能像 Flow 那样做 `combine`、`map`、`filter` 等流式变换，而我项目里"文件列表 = 数据库 flow combine 当前筛选 combine 当前排序"是典型场景；Google 官方近年也在推 Flow 替代 LiveData。
- **决策：** 选择方案 2（Room + Flow）。
- **代价：**
  - 我需要花时间理解协程和 Flow 的基础概念，这是学习成本但也是项目想要的"成长"。
  - 调试时如果 UI 没刷新，要从"数据库写没写对"→"Flow 有没有发射"→"ViewModel 收到没"→"UI 重组没"依次排查，比命令式 debug 步骤多。
  - 单元测试 ViewModel 时要用 `runTest` + `TestDispatcher`，setup 比同步测试稍微复杂。
- **验证：**
  - 上传文件后，不调用任何 refresh 方法，首页最近转存和文件列表都自动出现新文件。
  - 分享保存后，首页最近转存和目标文件夹列表都自动刷新。
  - 软删除后，列表自动隐藏被删文件，最近浏览也自动隐藏（因为查询带了 `is_deleted = 0` 过滤）。
  - 计划在 §3.5 的单元测试计划里至少覆盖一个"Flow 自动刷新"的测试。
- **关联：** v3 开发指南 §4.3、§4.4
- **关联 commit：** `feat(db): DAO 返回 Flow 实现响应式数据流 #D-003`（阶段 1-4 步完成后补编号）

---

# D-004 修复剪贴板分享链接冷启动不识别问题

- **日期：** 2026-06-01
- **背景：** 阶段 6 做分享与 DeepLink 时，我实现了“分享文案复制到剪贴板”和“App 回到前台时识别剪贴板分享链接”。设计目标是：用户复制 `simplepan://share?token=...` 后，退出 App 再重新打开，App 能自动识别剪贴板里的分享链接并进入分享预览页。这个功能涉及 Android 剪贴板、生命周期、Compose 导航和 DeepLink 解析，是一个很典型的客户端端到端场景。
- **问题现象：**
  1. 在文件页进入管理模式，选中文件后点击“分享”，分享文案能正常复制到剪贴板。
  2. 分享文案中确实包含 `simplepan://share?token=...` 链接。
  3. 退出 App 后重新打开，页面没有自动跳转到分享预览页，也没有任何错误提示。
  4. 由于没有可见反馈，最初无法判断问题发生在“没读到剪贴板”“解析失败”还是“解析成功但导航失败”。
- **最初实现：**
  - 在 `AppNavGraph` 中注册 `LifecycleEventObserver`。
  - 只在 `Lifecycle.Event.ON_RESUME` 事件触发时读取剪贴板。
  - 调用 `DeepLinkParser.parse(clipboardText)` 解析 token。
  - 如果解析出 `DeepLinkParseResult.Share(token)`，则执行 `navController.navigate(Routes.sharePreview(token))`。
- **根因分析：**
  1. **冷启动时可能错过 `ON_RESUME`：** Compose 的 `ClipboardShareLinkHandler` 是在 `setContent { AppNavGraph() }` 之后进入组合才注册的。如果 Activity 的 `ON_RESUME` 已经发生，observer 注册得太晚，就不会收到这次事件。
  2. **刚回前台时剪贴板读取可能过早：** Android 对剪贴板读取有前台限制。App 刚进入前台的一瞬间，生命周期已经到 `RESUMED`，但窗口/前台权限可能还没完全稳定，立即读取剪贴板可能读不到内容。
  3. **缺少可观测反馈：** 之前失败时静默忽略普通文本和异常情况，导致用户看不到“没读到”“读到了但不是分享链接”“链接格式错误”这些状态，调试效率很低。
- **备选方案：**
  1. **继续只监听 `ON_RESUME`，手动重试：**
     - 优势：代码最少。
     - 问题：冷启动仍可能错过事件；用户体验不稳定；答辩时很难解释为什么有时能跳、有时不能跳。
  2. **注册后立刻检测一次剪贴板：**
     - 优势：能解决“observer 注册晚于 `ON_RESUME`”的问题。
     - 问题：如果检测太早，仍可能遇到 Android 剪贴板前台权限尚未稳定的问题；第一次修复后实测仍不稳定。
  3. **在 `RESUMED` 状态下延迟检测，并加入可见反馈：**
     - 优势：确保 App 已经真正处于前台；延迟一小段时间等窗口和剪贴板权限稳定；成功/失败都有 Snackbar 提示，便于定位。
     - 代价：引入一个很短的 300ms 延迟；导航层需要持有全局 `SnackbarHostState`。
- **决策：** 选择方案 3。最终实现改为：
  - 使用 `repeatOnLifecycle(Lifecycle.State.RESUMED)`，只在 App 真正处于前台时检测。
  - 在检测前 `delay(300L)`，避开冷启动时剪贴板权限不稳定的窗口。
  - 把检测结果建模为 `ClipboardShareDetectionResult`：
    - `Share(token)`：识别成功，跳转分享预览页。
    - `InvalidShareLink`：剪贴板里有 SimplePan 链接但 token 缺失或非法，显示错误提示。
    - `NoShareLink`：普通剪贴板文本，静默忽略。
  - 在 App 顶层 `Scaffold` 增加 `SnackbarHost`，识别成功显示“已识别剪贴板分享链接”，链接无效显示“剪贴板中的分享链接无效”。
- **最终代码路径：**
  - `AppNavGraph.kt`
    - `ClipboardShareLinkHandler(...)`
    - `Context.detectClipboardShareLink()`
    - `Context.readClipboardTextOrNull()`
  - `DeepLinkParser.kt`
    - `DeepLinkParser.parse(input)`
    - `DeepLinkParseResult.Share(token)`
  - `Routes.kt`
    - `Routes.sharePreview(token)`
- **关键代码思路：**
  ```kotlin
  lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      delay(300L)
      when (val result = context.detectClipboardShareLink()) {
          is ClipboardShareDetectionResult.Share -> {
              navController.navigate(Routes.sharePreview(result.token)) {
                  launchSingleTop = true
              }
              snackbarHostState.showSnackbar("已识别剪贴板分享链接")
          }
          ClipboardShareDetectionResult.InvalidShareLink -> {
              snackbarHostState.showSnackbar("剪贴板中的分享链接无效")
          }
          ClipboardShareDetectionResult.NoShareLink -> Unit
      }
  }
  ```
- **踩过的坑：**
  1. **把生命周期事件等同于业务可执行时机。** `ON_RESUME` 表示生命周期进入前台，但并不保证 Compose observer 已经注册，也不保证剪贴板读取马上稳定。
  2. **静默失败导致定位困难。** 最初普通文本、解析失败、读取失败都没有反馈，用户只能说“不跳”，开发者无法快速判断哪一段失败。
  3. **并行跑 Gradle 任务会干扰 Kotlin 增量编译。** 一次把 `assembleDebug` 和 `testDebugUnitTest` 并行执行，Windows 上 Kotlin daemon 出现临时备份文件冲突。后来改为顺序执行，构建正常。这也说明验证命令本身也要稳定。
- **学到的技术点：**
  1. **Android 剪贴板读取受前台状态影响。** 不是任何时候都能稳定读取剪贴板，尤其是冷启动和刚回前台时要注意时机。
  2. **Compose 中监听生命周期应优先使用 `repeatOnLifecycle`。** 它比手写 `LifecycleEventObserver` 更适合“在某个生命周期状态内运行协程”的场景。
  3. **DeepLink 解析和导航应该分层。** `DeepLinkParser` 只负责从文本中解析 token，不负责导航；`AppNavGraph` 持有 `NavController`，负责把 token 转成页面跳转。
  4. **可观测性是调试的一部分。** Snackbar 不只是用户提示，也能在演示和调试时证明系统已经读到、解析到或拒绝了剪贴板内容。
- **验证：**
  1. 管理模式选中文件，点击“分享”，剪贴板生成包含 `simplepan://share?token=...` 的分享文案。
  2. 退出 App 或切到后台，再重新打开 App。
  3. App 延迟短暂时间后自动进入分享预览页。
  4. 页面出现“已识别剪贴板分享链接”提示。
  5. 如果剪贴板中是非法 SimplePan 链接，则显示“剪贴板中的分享链接无效”。
  6. 执行 `.\gradlew.bat :app:assembleDebug --console=plain` 通过。
  7. 执行 `.\gradlew.bat :app:testDebugUnitTest --console=plain` 通过。
- **答辩表达：**
  - 这次问题不是简单的字符串解析错误，而是移动端生命周期、系统权限时机和 Compose 注册时机共同造成的真实问题。
  - 修复过程从“怀疑解析错误”逐步定位到“生命周期触发时机”和“剪贴板读取时机”，最后通过 `repeatOnLifecycle + delay + 可见反馈` 解决。
  - 这说明我理解了客户端开发中“逻辑正确”和“触发时机正确”是两件事，也理解了为什么复杂交互需要可观测反馈来辅助调试。
- **关联 commit：** `fix(share): 延迟检测前台剪贴板分享链接 #D-004`

---

<!--
后续决策按以下模板继续：

# D-00X 决策标题（动词开头：选择 / 改用 / 放弃 / 拆分 / 调整）

- **日期：** YYYY-MM-DD
- **背景：** 1-3 句话，说明这个决策是为了解决什么问题
- **备选方案：**
  1. 方案 A：说明实现方式、优势、问题
  2. 方案 B：说明实现方式、优势、问题
- **决策：** 选了哪个
- **代价：** 这个选择放弃了什么、需要承担什么后果
- **验证：** 通过什么方式确认这个决策是对的（手测、单测、性能对比、用户反馈）
- **关联 commit：** refactor(reader): v2 改用测量分页 #D-001
-->
