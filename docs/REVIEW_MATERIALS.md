# 复盘素材库

本文不是最终要提交的飞书文档，而是写飞书文档前的素材仓库。

老师强调“不建议通过 AI 输出”，核心意思不是不能整理资料，而是最终文档要体现自己的真实理解、选择过程和踩坑经历。所以这里的内容建议这样使用：

1. 先按专题读一遍，找出你真的讲得清楚的功能。
2. 对每个专题补上“我当时怎么想”“我怎么验证”“我后来怎么理解”。
3. 最终飞书文档不要照抄本文，把这里的素材改成自己的叙述。
4. 答辩前挑 3-5 个专题重点讲深，不要每个功能都平均用力。

---

## 0. 飞书文档建议结构

可以把最终文档拆成这几块：

1. 项目目标与阶段推进
2. 我重点理解的技术方案
3. 实现过程中踩过的坑
4. 几个关键代码链路解读
5. 后续迭代计划

不要写成“功能清单”。老师更想看的是：我为什么这么做、我一开始哪里不懂、我怎么定位问题、我最后学到了什么。

---

## 1. 项目阶段素材

| 阶段 | 做了什么 | 可以写进文档的学习点 |
| --- | --- | --- |
| 阶段 1：工程骨架 | Hilt、Room、Repository、mock 数据、Navigation、首页骨架 | 为什么先搭分层架构；为什么 mock 数据要先入库再展示 |
| 阶段 2：文件树浏览 | 进入文件夹、返回上一级、类型图标、筛选、排序 | 文件系统本质是树结构；当前目录用 parentId 表示 |
| 阶段 3：管理模式 | 长按进入管理、勾选、全选、底部操作栏、重命名、删除、移动 | MVI 管理复杂 UI 状态；软删除；移动时禁止移动到自身子目录 |
| 阶段 4：上传与最近转存 | SAF 选择文件、100MB 校验、复制到私有目录、写 Room、写 transfer_history | Android 文件访问权限；为什么不能只保存外部 Uri；响应式刷新 |
| 阶段 5：文件打开 | 视频系统播放器、TXT 自研阅读器、最近浏览、异常处理、固定字数分页 v1 | FileProvider；content Uri；分页方案从简单到复杂迭代 |
| 阶段 6：分享与 DeepLink | token 分享、分享快照、复制文案、剪贴板识别、分享预览、保存到网盘 | 分享链接安全设计；DeepLink 解析；生命周期和剪贴板时机 |

---

## 2. 重点专题一：为什么用 MVI 管理文件页状态

### 这个专题适合表达什么

适合表达你从“后端命令式思维”转向“客户端状态驱动 UI”的过程。

文件页不是一个简单列表，它同时包含：

- 当前目录
- 是否处于管理模式
- 当前选中了哪些文件
- 当前筛选类型
- 当前排序方式
- 上传中、分享中、删除中、移动中等临时状态
- Toast、Snackbar、打开文件、跳转页面这类一次性事件

如果这些状态散落在很多变量里，很容易出现联动遗漏。例如退出管理模式时，如果忘记清空 selectedIds，用户下次进入管理模式会看到旧的选中状态。

### 技术选择

最终选择 MVI 的原因：

- `State` 统一描述页面当前长什么样。
- `Intent` 统一描述用户做了什么。
- `Effect` 处理一次性事件，比如 Toast、跳转、打开播放器。
- 状态变化通过 `copy()` 显式写出来，方便排查和答辩讲解。

### 代码阅读路线

1. `app/src/main/java/com/example/simple_pan/ui/file/FileListContract.kt`
   - 看 `FileListState`
   - 看 `FileListIntent`
   - 看 `FileListEffect`
2. `app/src/main/java/com/example/simple_pan/ui/file/FileListViewModel.kt`
   - 看 `onIntent(...)`
   - 看进入管理、退出管理、选择文件、全选、筛选、排序的处理
3. `app/src/main/java/com/example/simple_pan/ui/file/FileListScreen.kt`
   - 看 UI 如何根据 state 自动变化
   - 看 effect 如何触发 Toast、文件打开、分享复制

### 可以用自己的话写

我一开始更习惯“用户点了按钮，我就手动改某个变量”的写法。但文件列表页状态越来越多后，我发现单独维护变量会让状态之间的关系变得隐蔽，比如退出管理模式必须同时清空选中列表。后来我用 MVI 把页面状态集中到一个 State 里，把用户行为定义成 Intent，这样每次状态变化都能在 ViewModel 里追踪到。

### 老师可能会问

- 为什么不用普通 MVVM？
- MVI 会不会太复杂？
- State 和 Effect 的区别是什么？
- 为什么 Toast、导航不直接放进 State？

---

## 3. 重点专题二：Room + Flow 为什么能让列表自动刷新

### 这个专题适合表达什么

适合表达你理解了“数据驱动 UI”的链路。

项目里很多功能都会修改数据库：

- 上传文件写入 `file_entity`
- 删除文件更新 `is_deleted`
- 重命名更新 `name`
- 分享保存写入新文件
- 打开文件写入 `open_history`
- 上传/分享保存写入 `transfer_history`

如果每次修改后都手动刷新 UI，很容易漏。例如上传成功后，既要刷新文件列表，又要刷新首页最近转存。

### 技术选择

最终选择 DAO 返回 `Flow`：

- Room 监听表变化。
- 数据变化后，Flow 自动重新发射。
- ViewModel 收到新数据后更新 State。
- Compose 根据 State 重新绘制 UI。

完整链路可以这样理解：

```text
用户操作
  -> Repository / UseCase 写数据库
  -> Room 感知表变化
  -> DAO 的 Flow 重新发射
  -> ViewModel 更新 State
  -> Compose 自动重组页面
```

### 代码阅读路线

1. `app/src/main/java/com/example/simple_pan/data/local/dao/FileDao.kt`
   - 看 `observe...` 开头的方法
2. `app/src/main/java/com/example/simple_pan/data/repository/FileRepositoryImpl.kt`
   - 看 Repository 怎么把 DAO 暴露成领域层数据
3. `app/src/main/java/com/example/simple_pan/ui/file/FileListViewModel.kt`
   - 看 ViewModel 怎么收集 Flow
4. `app/src/main/java/com/example/simple_pan/ui/file/FileListScreen.kt`
   - 看 Compose 怎么展示 state

### 可以用自己的话写

这次项目里我印象比较深的是上传和分享保存后的自动刷新。最开始我以为要在每个操作结束后手动 refresh，但这样很容易漏掉某个页面。后来用 Room + Flow 后，只要数据库写对，订阅这个表的页面就会自动收到新数据。这个设计让我理解了客户端里“单一数据源”和“响应式刷新”的意义。

### 老师可能会问

- Flow 和普通 suspend 查询有什么区别？
- 为什么 Room 数据变了 UI 会刷新？
- 最近转存为什么能自动出现新记录？
- 如果 UI 没刷新，你会怎么排查？

---

## 4. 重点专题三：文件上传链路

### 这个专题适合表达什么

适合表达你理解 Android 本地文件访问和 App 私有存储。

上传不是“拿到一个路径就完事”。Android 上通过 SAF 选中文件后，App 拿到的是一个 `Uri`，而不是传统文件路径。这个 Uri 代表系统授权 App 读取这个文件。

项目里的上传链路是：

```text
点击 +
  -> 系统文件选择器返回 Uri
  -> 读取文件名、大小、MIME
  -> 校验是否超过 100MB
  -> 复制到 App 私有目录
  -> 写入 file_entity
  -> 写入 transfer_history
  -> 文件页和首页自动刷新
```

### 为什么要复制到 App 私有目录

如果只保存 SAF 返回的 Uri，会有几个问题：

- 用户可能移动或删除原文件。
- App 下次打开时不一定还能稳定读取。
- 项目后续要打开 TXT、视频，需要 App 能稳定拿到自己的文件。

复制到私有目录后，App 对文件拥有稳定控制权。

### 代码阅读路线

1. `app/src/main/java/com/example/simple_pan/data/storage/LocalFileMetadataReader.kt`
   - 读取文件名、大小、MIME
2. `app/src/main/java/com/example/simple_pan/domain/usecase/UploadSizePolicy.kt`
   - 100MB 校验规则
3. `app/src/main/java/com/example/simple_pan/data/storage/PrivateUploadStorage.kt`
   - 把 SAF 文件复制到 App 私有目录
4. `app/src/main/java/com/example/simple_pan/domain/usecase/UploadFileUseCase.kt`
   - 串起读取、校验、复制、入库
5. `app/src/main/java/com/example/simple_pan/data/repository/FileRepositoryImpl.kt`
   - 写 `file_entity` 和 `transfer_history`

### 可以用自己的话写

上传功能让我意识到 Android 和 Web/后端不太一样。用户选择文件后，我拿到的不是一个可以随便访问的绝对路径，而是系统授权的 Uri。为了让后续 TXT 阅读器和视频播放稳定工作，我把文件复制到 App 私有目录，再把数据库记录指向这份 App 自己管理的文件。

### 老师可能会问

- SAF 是什么？
- 为什么不能直接保存原始文件路径？
- 100MB 校验放在哪里更合理？
- 上传成功后首页为什么会自动出现最近转存？

---

## 5. 重点专题四：TXT 阅读器固定字数分页 v1

### 这个专题适合表达什么

适合表达你知道“先做简单可验证版本，再逐步迭代”的工程思维。

TXT 阅读器最终想做的是接近真实阅读器的分页效果，但真实分页其实很复杂，因为它和很多因素有关：

- 屏幕宽高
- 字号
- 行高
- 字体
- 中英文宽度差异
- 段落和换行
- 横竖屏变化

所以阶段 5 先做了固定字数分页 v1。它不是最终最优方案，而是一个故意保留的迭代点。

### v1 方案

固定每页切一定数量的字符，例如每 500 个字符一页。

优点：

- 实现快。
- 逻辑简单。
- 容易验证翻页、页码、手势。
- 能先把阅读器主流程跑通。

缺点：

- 不知道真实屏幕能放多少字。
- 可能一页内容太多或太少。
- 中英文混排时效果不稳定。
- 换字号或横竖屏后分页不准确。

### 后续 v2 方向

v2 可以做“测量版分页”：

- 根据屏幕宽高计算可用区域。
- 根据字体大小和行高测量文本。
- 按真实可展示高度切页。
- 横竖屏或字号变化时重新分页。

### 代码阅读路线

1. `app/src/main/java/com/example/simple_pan/domain/usecase/ReadTxtFileUseCase.kt`
   - 看 TXT 文件读取和编码处理
2. `app/src/main/java/com/example/simple_pan/ui/reader/TxtReaderViewModel.kt`
   - 看分页数据如何进入状态
3. `app/src/main/java/com/example/simple_pan/ui/reader/TxtReaderScreen.kt`
   - 看上一页、下一页、左右滑动、页码展示

### 可以用自己的话写

TXT 阅读器这里我没有一开始就做最复杂的测量分页，而是先做固定字数分页 v1。这个选择不是因为不知道真实分页更好，而是因为阶段目标要先验证“能打开 TXT、能分页、能左右滑动、能记录最近浏览”。v1 暴露出来的问题，比如中英文混排和屏幕适配不准，正好可以作为后续 v2 优化的对比点。

### 老师可能会问

- 固定字数分页有什么问题？
- 真正阅读器的分页为什么复杂？
- 你后面准备怎么优化？
- 为什么不一步到位？

---

## 6. 重点专题五：视频打开与 FileProvider

### 这个专题适合表达什么

适合表达你理解 Android 文件共享安全模型。

App 私有目录里的文件，其他 App 不能直接访问。系统播放器是另一个 App，如果直接把私有文件路径传给它，它通常打不开。

所以需要 FileProvider：

```text
App 私有文件
  -> FileProvider 生成 content:// Uri
  -> Intent.ACTION_VIEW
  -> 授予临时读取权限
  -> 系统播放器打开视频
```

### 为什么不用 file://

Android 新版本限制直接暴露 `file://` 路径。原因是安全：不能把 App 私有路径直接泄露给其他 App，也不能让其他 App 越权访问。

`content://` Uri 更安全，因为它可以通过 FileProvider 控制访问范围和有效时间。

### 代码阅读路线

1. `app/src/main/java/com/example/simple_pan/data/storage/FileUriProvider.kt`
   - 看本地文件如何转成 content Uri
2. `app/src/main/java/com/example/simple_pan/domain/usecase/OpenFileUseCase.kt`
   - 看不同文件类型的打开决策
3. `app/src/main/java/com/example/simple_pan/ui/file/FileListViewModel.kt`
   - 看点击文件后如何触发打开 Effect
4. `app/src/main/AndroidManifest.xml`
   - 看 FileProvider 注册

### 可以用自己的话写

视频打开这里我学到，App 自己能读的文件不代表系统播放器也能读。因为视频被复制到了 App 私有目录，所以要通过 FileProvider 生成一个临时授权的 content Uri，再交给系统播放器。这比直接暴露文件路径更符合 Android 的安全模型。

---

## 7. 重点专题六：分享功能为什么用 token + 快照

### 这个专题适合表达什么

适合表达你对“分享链接安全性”和“数据快照”的理解。

分享功能不能把真实的 `file_id`、`path` 放进链接里。否则用户复制出来的链接会暴露内部数据库结构，也可能让别人猜测文件路径。

最终分享链接类似：

```text
simplepan://share?token=随机字符串
```

token 是外部看到的唯一标识。真正的文件列表保存在本地数据库的分享快照表里。

### 为什么要快照

如果分享的是文件夹，里面可能有多层子文件。创建分享时需要把当时的文件结构记录下来：

- 文件名
- 文件类型
- 大小
- 相对路径
- 是否文件夹
- 排序信息

这样分享预览页可以根据 token 还原出当时分享的内容。

### 代码阅读路线

1. `app/src/main/java/com/example/simple_pan/domain/usecase/CreateShareUseCase.kt`
   - 看如何把选中的文件/文件夹转成分享快照
2. `app/src/main/java/com/example/simple_pan/data/repository/ShareRepositoryImpl.kt`
   - 看 token、share、snapshot 如何写入数据库
3. `app/src/main/java/com/example/simple_pan/deeplink/ShareLinkBuilder.kt`
   - 看分享文案和链接如何生成
4. `app/src/main/java/com/example/simple_pan/deeplink/DeepLinkParser.kt`
   - 看如何从剪贴板文字里解析 token
5. `app/src/main/java/com/example/simple_pan/ui/share/SharePreviewViewModel.kt`
   - 看分享预览页如何根据 token 加载快照
6. `app/src/main/java/com/example/simple_pan/domain/usecase/SaveShareToPanUseCase.kt`
   - 看保存到网盘如何把快照重新写成文件记录

### 可以用自己的话写

分享功能我没有把 file_id 或 path 直接拼到链接里，而是生成一个 token。链接只暴露 token，真正的文件信息存在本地分享快照里。这样做一方面避免暴露内部数据库字段，另一方面也能支持文件夹和多文件分享，因为分享时已经把当时的文件结构保存成快照。

### 老师可能会问

- 为什么链接里不能有 file_id？
- token 和 file_id 有什么区别？
- 文件夹分享怎么保存子文件？
- 分享后如果原文件改名了，预览页应该展示旧名字还是新名字？

---

## 8. 重点专题七：剪贴板识别分享链接的踩坑

### 这个专题适合表达什么

这是目前最适合写进“踩坑点”的经历，因为它不是单纯语法错误，而是真实客户端问题：代码逻辑看起来对，但触发时机不对。

### 问题现象

流程是：

```text
管理模式选中文件
  -> 点击分享
  -> 分享文案复制到剪贴板
  -> 退出 App
  -> 重新打开 App
  -> 预期自动跳到分享预览页
  -> 实际没有跳转
```

### 一开始的判断

最开始可能会怀疑：

- 是不是分享文案没有复制成功？
- 是不是 DeepLinkParser 解析错了？
- 是不是 navController.navigate 没执行？
- 是不是分享预览页路由错了？

后来逐步排查后发现，核心问题不是 token 或解析逻辑，而是 App 回到前台时读取剪贴板的时机。

### 原因拆解

一开始使用 `LifecycleEventObserver` 监听 `ON_RESUME`。

问题在于：

- Compose 里的监听器是在页面组合后才注册的。
- 冷启动时 Activity 的 `ON_RESUME` 可能已经发生。
- 如果监听器注册晚了，就错过这次事件。
- 即使注册后立刻读取，Android 刚回前台的一瞬间剪贴板读取也可能还不稳定。

所以这次 bug 的本质是：

```text
业务逻辑基本正确
但执行时机不稳定
```

### 最终修复

最终改成：

- 使用 `repeatOnLifecycle(Lifecycle.State.RESUMED)`。
- 确保只在 App 真正处于前台时检测。
- 检测前 `delay(300L)`。
- 识别成功或链接无效时用 Snackbar 给出反馈。

### 代码阅读路线

1. `app/src/main/java/com/example/simple_pan/navigation/AppNavGraph.kt`
   - 看 `ClipboardShareLinkHandler`
   - 看 `repeatOnLifecycle`
   - 看 `delay(300L)`
   - 看识别成功后如何 navigate
2. `app/src/main/java/com/example/simple_pan/deeplink/DeepLinkParser.kt`
   - 看剪贴板文本如何解析成 token
3. `app/src/main/java/com/example/simple_pan/navigation/Routes.kt`
   - 看 token 如何变成分享预览页路由
4. `docs/DECISIONS.md`
   - 看 D-004 对这次问题的正式复盘

### 可以用自己的话写

这次剪贴板问题让我意识到，移动端开发里“代码逻辑正确”和“触发时机正确”是两回事。最开始我只监听 ON_RESUME，但冷启动时 Compose 监听器可能注册得太晚，导致错过事件。后来又发现刚回前台时马上读剪贴板也不一定稳定。最终通过 repeatOnLifecycle 保证 App 处于 RESUMED 状态，再延迟 300ms 读取，并加 Snackbar 反馈，问题才稳定解决。

### 老师可能会问

- 为什么 ON_RESUME 还不够？
- 为什么要 delay？
- delay 是不是拍脑袋？
- 失败时为什么要给 Snackbar？
- 这个问题和 DeepLinkParser 有关系吗？

### 自己补充区

你可以在这里补自己的真实过程：

- 我第一次看到“不跳转”时，以为问题出在：
- 我后来怎么验证剪贴板里确实有链接：
- 我怎么判断不是解析问题：
- 修复成功后，我对生命周期的理解变化是：

---

## 9. 重点专题八：管理模式的几个细节

### 为什么管理模式值得写

管理模式看起来是 UI 功能，但里面有很多状态一致性问题：

- 长按进入管理模式
- 点击勾选/取消勾选
- 全选/取消全选
- 底部操作栏根据选中数量变化
- 删除后要退出或刷新选中状态
- 移动后不能残留旧选择
- 退出管理模式必须清空选中状态

### 重命名校验

重命名不是简单改字符串，需要考虑：

- 空名不允许
- 同目录重名不允许
- 文件要保留扩展名

例如 `课程录屏.mp4` 重命名时，用户改的是主文件名，`.mp4` 扩展名应该保留。

### 移动限制

移动文件夹时，不能移动到自己的子目录里。

原因是树结构会产生循环：

```text
学习资料
  -> 子文件夹

如果把“学习资料”移动到“子文件夹”里：

学习资料
  -> 子文件夹
      -> 学习资料
          -> 子文件夹
```

这样文件树就不再是一棵正常的树，后续递归遍历可能死循环。

### 软删除

项目里删除采用软删除：

- 不是真的从数据库物理删除。
- 而是把 `is_deleted` 标记改为 true。
- 查询列表时过滤掉已删除文件。

好处：

- 更接近真实网盘。
- 后续可以扩展回收站。
- 删除操作更安全。

### 代码阅读路线

1. `app/src/main/java/com/example/simple_pan/ui/file/FileListContract.kt`
   - 看管理模式状态和 Intent
2. `app/src/main/java/com/example/simple_pan/ui/file/FileListViewModel.kt`
   - 看选中、全选、删除、移动、重命名逻辑
3. `app/src/main/java/com/example/simple_pan/ui/file/FileListScreen.kt`
   - 看底部操作栏和弹窗
4. `app/src/main/java/com/example/simple_pan/data/repository/FileRepositoryImpl.kt`
   - 看软删除、重命名、移动最终怎么写数据库

### 可以用自己的话写

管理模式让我体会到，UI 上一个简单的“勾选”背后其实是状态管理问题。比如退出管理模式时必须清空选中，否则用户下次进入会看到错误状态。移动文件夹时还要判断不能移动到自己的子目录，因为这会破坏文件树结构。

---

## 10. 重点专题九：首页最近浏览 / 最近转存

### 为什么这个功能值得写

首页最近记录是检验数据链路是否打通的好例子。

它不是用户直接在首页手动添加的，而是由其他行为自动产生：

- 打开 TXT 或视频后，写入最近浏览。
- 上传文件后，写入最近转存。
- 保存分享后，写入最近转存。

首页只是订阅这些历史表，所以能自动刷新。

### 代码阅读路线

1. `app/src/main/java/com/example/simple_pan/data/local/entity/OpenHistoryEntity.kt`
2. `app/src/main/java/com/example/simple_pan/data/local/entity/TransferHistoryEntity.kt`
3. `app/src/main/java/com/example/simple_pan/data/local/dao/OpenHistoryDao.kt`
4. `app/src/main/java/com/example/simple_pan/data/local/dao/TransferHistoryDao.kt`
5. `app/src/main/java/com/example/simple_pan/data/repository/RecentRepositoryImpl.kt`
6. `app/src/main/java/com/example/simple_pan/ui/home/PanHomeViewModel.kt`
7. `app/src/main/java/com/example/simple_pan/ui/home/PanHomeScreen.kt`

### 可以用自己的话写

首页最近记录这里，我理解到它不应该由首页主动去“猜”发生了什么，而应该由具体行为写历史表。首页只订阅历史数据。这样上传、打开、分享保存这些功能各自负责记录自己的行为，首页负责展示结果，职责比较清晰。

---

## 11. 可以手搓并讲清楚的代码片段

老师说建议大家手搓部分功能代码。建议你选几段不太大、但能体现理解的代码重新手写或至少能口述出来。

### 建议 1：DeepLinkParser

为什么适合手搓：

- 输入输出明确。
- 不依赖 UI。
- 可以写单元测试。
- 能讲安全性：只接受 SimplePan 分享链接，只提取 token。

你要能讲清楚：

- 输入是一整段剪贴板文本，不一定只有链接。
- 需要从文本中找出 `simplepan://share?token=...`。
- token 缺失或格式错误时不能跳转。

### 建议 2：固定字数分页

为什么适合手搓：

- 算法简单。
- 很适合解释 v1 和 v2 的差异。
- 能展示“先做可用版本，再迭代”的思维。

你要能讲清楚：

- 为什么每页切固定字符数。
- 为什么这个方案不适合最终阅读器。
- 后续测量分页要解决什么问题。

### 建议 3：重命名校验

为什么适合手搓：

- 贴近用户体验。
- 逻辑边界清楚。
- 能体现不是只做 UI，而是考虑业务规则。

你要能讲清楚：

- 空名为什么不允许。
- 同目录重名为什么不允许。
- 为什么文件扩展名要保留。

### 建议 4：禁止移动到自身子目录

为什么适合手搓：

- 能体现你理解树结构。
- 是一个真实 bug 防护点。
- 答辩时容易画图说明。

你要能讲清楚：

- 文件夹树为什么不能出现环。
- 怎么沿着 parentId 向上查祖先。
- 如果目标目录的祖先包含当前文件夹，就不能移动。

---

## 12. 答辩时可以画的链路图

### 上传链路

```text
SAF Uri
  -> 读取元信息
  -> 100MB 校验
  -> 复制到私有目录
  -> 写 file_entity
  -> 写 transfer_history
  -> Room Flow 自动刷新
  -> 文件页 / 首页变化
```

### 分享链路

```text
选中文件
  -> CreateShareUseCase
  -> 生成 token
  -> 生成快照
  -> 写 share / snapshot 表
  -> 生成 simplepan://share?token=...
  -> 复制到剪贴板
```

### 剪贴板识别链路

```text
App 回到前台
  -> repeatOnLifecycle(RESUMED)
  -> delay(300ms)
  -> 读取剪贴板
  -> DeepLinkParser 解析 token
  -> navigate 到分享预览页
  -> 根据 token 加载快照
```

### TXT 阅读器链路

```text
点击 TXT 文件
  -> OpenFileUseCase 判断类型
  -> 导航到 TXT 阅读器
  -> ReadTxtFileUseCase 读取文本
  -> 固定字数分页
  -> 左右滑动切页
  -> 写入最近浏览
```

---

## 13. 可继续补充的真实经历

这些地方建议你自己补充，越真实越好：

### 我最开始不懂的点

- 

### 我第一次跑通时最有成就感的功能

- 

### 我最容易混淆的概念

- 

### 我踩过但后来解决的问题

- 

### 我准备在 6 月 11 日前重点打磨的地方

- UI 交互继续对齐悟空浏览器。
- TXT 阅读器可以保留 v1 截图，后续如果做 v2 测量分页，用来对比。
- 分享保存、上传、最近记录这些链路要准备演示路径。

---

## 14. 最终飞书文档不要照抄的提醒

最终提交时，可以保留本文的技术结构，但表达要换成自己的：

- 多写“我当时以为……后来发现……”
- 多写“我为什么没有选另一个方案”
- 多写“我怎么验证这个功能真的生效”
- 多写“这个问题让我理解了什么”

少写：

- “本项目实现了……”
- “该功能用于……”
- “通过某某技术完成……”

因为这些像功能说明书，不像实现过程复盘。
