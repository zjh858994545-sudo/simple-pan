# SimplePan 代码学习指南

这份文档的目标不是替你写复盘，而是帮你把项目代码真正读懂。你可以把它当成“读代码地图”：

1. 先知道每个目录负责什么。
2. 再按功能链路从 UI 一直追到数据库。
3. 最后通过小练习把关键代码自己写一遍。

> 重要提醒：这份文档适合你自己学习、整理笔记、准备答辩。最终提交到飞书的复盘文档，建议改成你自己的第一人称表达，不要原样照搬。

---

## 0. 先建立整体认识

这个项目本质上是一个本地模拟网盘 App。它看起来像“网盘”，但真正核心不是云服务器，而是 Android 客户端能力：

- 页面展示：Jetpack Compose。
- 状态管理：轻量 MVI。
- 本地数据：Room。
- 异步数据流：Coroutines + Flow。
- 依赖注入：Hilt。
- 文件选择：SAF / Activity Result API。
- 私有文件保存：App 私有目录。
- 视频打开：FileProvider + 系统播放器。
- TXT 阅读：自研阅读器。
- 分享链接：token + 快照 + 剪贴板识别。

你读代码时，不要一上来纠结每一行语法。先抓住一条主线：

```text
用户操作
-> Composable 发出 Intent
-> ViewModel 处理 Intent
-> UseCase / Repository 执行业务
-> DAO 写入或读取 Room
-> Flow 发射新数据
-> ViewModel 更新 State
-> Compose 自动重组 UI
```

这就是整个项目最核心的“数据流动方式”。

---

## 1. 目录结构怎么读

代码主目录：

```text
app/src/main/java/com/example/simple_pan
```

核心目录含义：

| 目录 | 作用 | 你要重点理解什么 |
|---|---|---|
| `data` | 数据层，负责 Room、mock 数据、文件存储、Repository 实现 | 数据从哪里来，怎么存，怎么转成领域模型 |
| `domain` | 领域层，定义模型、Repository 接口、UseCase | 业务规则放在哪里，UI 为什么不直接操作数据库 |
| `di` | Hilt 依赖注入配置 | DAO、Repository、Dispatcher 是怎么被自动传进去的 |
| `navigation` | 页面路由和全局能力 | 页面之间怎么跳，剪贴板分享链接在哪里识别 |
| `ui` | Compose 页面、ViewModel、MVI Contract | 用户看到的界面，以及页面状态如何管理 |
| `deeplink` | 分享链接构建和解析 | 为什么分享链接只有 token，不暴露文件 ID |
| `util` | 工具类 | 一些通用辅助能力 |

最推荐的读代码顺序：

1. `domain/model`
2. `data/local/entity`
3. `data/local/dao`
4. `domain/repository`
5. `data/repository`
6. `domain/usecase`
7. `ui/*/*Contract.kt`
8. `ui/*/*ViewModel.kt`
9. `ui/*/*Screen.kt`
10. `navigation/AppNavGraph.kt`

这个顺序是从“数据长什么样”读到“页面怎么显示”，比直接从 UI 开始读更稳。

---

## 2. 先读懂三类模型

项目里有三类看起来很像的数据类，别混淆。

### 2.1 Entity：数据库表结构

位置：

```text
data/local/entity
```

例如：

- `FileEntity`
- `TransferHistoryEntity`
- `OpenHistoryEntity`
- `ShareEntity`
- `ShareFileSnapshotEntity`

Entity 的作用是告诉 Room：数据库表有哪些字段。

你读 Entity 时重点看：

- `@Entity(tableName = "...")`：表名。
- `@PrimaryKey`：主键。
- `parentId`：文件树靠它表示父子关系。
- `isDeleted`：软删除标记。
- `createdAt / updatedAt / openedAt / transferredAt`：排序、最近记录依赖时间。

不要把 Entity 直接拿给 UI 用。Entity 是“数据库视角”的数据。

### 2.2 Domain Model：业务模型

位置：

```text
domain/model
```

例如：

- `CloudFile`
- `RecentRecord`
- `TransferRecord`
- `ShareBundle`
- `LocalFileMetadata`
- `UploadFileResult`

Domain Model 是 UI 和 UseCase 更愿意使用的数据结构。它不关心 Room 注解，也不暴露数据库细节。

例如 `CloudFile` 可以理解成：

```text
一个网盘文件在业务层里的样子
```

它包含：

- 文件 ID
- 父目录 ID
- 文件名
- 文件类型
- MIME 类型
- 大小
- 本地路径
- 创建/更新时间
- 是否置顶
- 来源

### 2.3 DTO：mock/远程数据格式

位置：

```text
data/remote/dto
```

例如：

- `FileDto`

DTO 是 mock JSON 对应的数据格式。它代表“外部数据长什么样”，不一定和数据库表完全一致。

项目初始化时大概是：

```text
mock_files.json
-> FileDto
-> FileEntity
-> Room
-> CloudFile
-> UI
```

你要理解这个转换链路，因为老师很可能问：“为什么不直接在 UI 里读 JSON？”

回答思路：

> JSON 是初始数据来源，但 App 的真实状态应该由数据库维护。上传、删除、移动、重命名都会改变数据，如果 UI 直接读 JSON，就无法表达这些后续变化。

---

## 3. Room 数据库怎么读

相关文件：

```text
data/local/AppDatabase.kt
data/local/dao/FileDao.kt
data/local/dao/TransferHistoryDao.kt
data/local/dao/OpenHistoryDao.kt
data/local/dao/ShareDao.kt
data/local/dao/ShareFileSnapshotDao.kt
```

### 3.1 AppDatabase 是数据库入口

`AppDatabase` 里通常能看到：

- 注册了哪些 Entity。
- 数据库版本。
- 暴露哪些 DAO。

你可以把它理解为：

```text
Room 数据库总开关
```

### 3.2 DAO 是数据库操作接口

DAO 负责定义 SQL：

- 查询文件夹下文件。
- 插入文件。
- 更新文件名。
- 软删除文件。
- 查询最近浏览。
- 查询最近转存。
- 查询分享快照。

你读 DAO 时要重点看两类函数：

1. 返回 `Flow<...>` 的查询函数。
2. `suspend` 的写入/更新/删除函数。

为什么查询用 Flow？

```text
数据库表变化
-> Room 让 Flow 重新发射
-> ViewModel 收到新数据
-> State 更新
-> Compose UI 自动刷新
```

这就是首页最近记录自动刷新的根本原因。

### 3.3 软删除怎么理解

软删除不是把数据库记录删掉，而是把 `isDeleted` 改成 true。

好处：

- 后续可以做回收站。
- 历史记录更完整。
- 删除文件夹时可以递归标记子文件。

风险：

- 所有查询都要记得过滤 `isDeleted = 0`。
- 如果漏过滤，已删除文件可能又出现在列表里。

你可以在 DAO 里找类似：

```sql
WHERE is_deleted = 0
```

这种条件。

---

## 4. Repository 为什么存在

相关文件：

```text
domain/repository/FileRepository.kt
data/repository/FileRepositoryImpl.kt
domain/repository/RecentRepository.kt
data/repository/RecentRepositoryImpl.kt
domain/repository/ShareRepository.kt
data/repository/ShareRepositoryImpl.kt
domain/repository/TransferRepository.kt
data/repository/TransferRepositoryImpl.kt
```

Repository 的作用是隔离“业务层”和“数据来源”。

UI 和 UseCase 不应该知道：

- 数据来自 Room 还是网络。
- SQL 怎么写。
- Entity 怎么转 Domain Model。
- 文件是不是从 mock JSON 初始化来的。

所以项目采用：

```text
UI / UseCase
-> domain/repository 接口
-> data/repository 实现
-> DAO / Storage / FakeRemoteDataSource
```

读 Repository 时，你重点看：

- 接口定义了哪些业务能力。
- Impl 里调用了哪些 DAO。
- 有没有用事务保证多个表一起写。
- 有没有做 Entity 和 Domain Model 的转换。

例如上传成功后，不只是插入文件，还要写转存历史：

```text
insert file
insert transfer_history
```

如果这两个动作中间失败，就可能出现“文件有了，但最近转存没有记录”的不一致。所以这类地方最好用事务。

---

## 5. UseCase 是什么

相关文件：

```text
domain/usecase
```

重点文件：

- `UploadFileUseCase.kt`
- `OpenFileUseCase.kt`
- `ReadTxtFileUseCase.kt`
- `RecordOpenUseCase.kt`
- `CreateShareUseCase.kt`
- `SaveShareToPanUseCase.kt`
- `UploadSizePolicy.kt`

UseCase 可以理解为：

```text
一次完整业务动作的编排器
```

它比 Repository 更接近“用户操作”。

### 5.1 上传 UseCase

上传不是一个简单 insert，它包含很多步骤：

```text
用户选择 SAF 文件 Uri
-> 读取文件名、大小、MIME
-> 判断是否超过 100MB
-> 复制到 App 私有目录
-> 插入 file 表
-> 插入 transfer_history 表
-> UI 显示成功
```

所以这条链路适合放在 `UploadFileUseCase` 里，而不是全塞进 ViewModel。

你读的时候重点看：

- 成功结果是什么。
- 失败结果有哪些。
- 100MB 阈值在哪里。
- 文件复制失败怎么办。
- 数据库写入失败怎么办。

### 5.2 打开文件 UseCase

打开文件要区分类型：

```text
TXT -> 自研阅读器
Video -> 系统播放器
Other -> 暂不支持或提示
```

ViewModel 不应该自己判断所有细节，所以有 `OpenFileUseCase`。

读它时重点看：

- 文件不存在返回什么。
- TXT 返回什么结果。
- 视频返回什么结果。
- 何时记录最近浏览。

### 5.3 分享 UseCase

分享不是把文件路径发出去，而是：

```text
选择文件/文件夹/多文件
-> 生成 token
-> 保存 ShareEntity
-> 保存 ShareFileSnapshotEntity
-> 构建分享文案
-> 复制到剪贴板
```

这里最重要的设计点：

> 链接里只放 token，不放 file_id、path、local_path。

这样更安全，也更像真实网盘产品。

---

## 6. Hilt 依赖注入怎么理解

相关文件：

```text
SimplePanApp.kt
di/DatabaseModule.kt
di/RepositoryModule.kt
di/DispatcherModule.kt
```

### 6.1 Hilt 解决什么问题

不用 Hilt 的话，ViewModel 里可能会手动 new：

```kotlin
val database = AppDatabase(...)
val dao = database.fileDao()
val repository = FileRepositoryImpl(dao)
```

这样会很乱，而且测试困难。

Hilt 的思路是：

```text
我声明“我需要 FileRepository”
Hilt 根据 Module 里的配置自动把 FileRepositoryImpl 传进来
```

### 6.2 重点注解

你需要认识这些：

- `@HiltAndroidApp`：标记 Application，启动 Hilt。
- `@Module`：告诉 Hilt 这里有依赖配置。
- `@InstallIn(SingletonComponent::class)`：依赖生命周期。
- `@Provides`：手动提供某个对象。
- `@Binds`：把接口绑定到实现类。
- `@Inject`：构造函数注入。
- `@HiltViewModel`：ViewModel 交给 Hilt 创建。

答辩时可以这样讲：

> Hilt 的价值不是少写几行代码，而是让依赖关系清晰、可替换、可测试。比如 UI 依赖的是 Repository 接口，不是具体 Room 实现。

---

## 7. MVI 页面应该怎么读

每个复杂页面通常有三类文件：

```text
xxxContract.kt
xxxViewModel.kt
xxxScreen.kt
```

例如文件页：

```text
ui/file/FileListContract.kt
ui/file/FileListViewModel.kt
ui/file/FileListScreen.kt
```

### 7.1 Contract 文件

Contract 里通常放：

- `State`
- `Intent`
- `Effect`
- 弹窗状态类
- UI 枚举

你先读 Contract，是因为它告诉你这个页面所有可能状态。

例如文件页的 State 会包含类似：

- 当前文件列表
- 当前目录
- 文件夹栈
- 是否管理模式
- 已选文件 ID
- 筛选类型
- 排序类型
- 上传中
- 重命名弹窗
- 删除弹窗
- 移动弹窗

读懂 State，就读懂了页面“可能长什么样”。

### 7.2 Intent 是用户行为

Intent 不是 Android 系统 Intent，这里是 MVI 的用户意图。

例如：

```text
EnterFolder
BackToParent
ChangeFilter
EnterManageMode
ToggleFileSelection
ConfirmRename
ConfirmDelete
UploadPickedFile
CreateShareFromSelection
```

你可以把它理解为：

```text
用户做了什么
```

### 7.3 Effect 是一次性动作

Effect 用来表示不能放进 State 的动作：

- Toast / Snackbar
- 页面跳转
- 打开系统播放器
- 复制分享链接后的提示

为什么不放 State？

因为 State 会被 Compose 重组重复读取。如果 Toast 放 State 里，可能会重复弹。

### 7.4 ViewModel

ViewModel 是页面逻辑核心。

读 ViewModel 时按这个顺序：

1. 构造函数注入了哪些 UseCase / Repository。
2. `state` 是怎么暴露的。
3. `effect` 是怎么发出的。
4. `onIntent()` 怎么分发用户行为。
5. 每个 `handleXxx()` 做了什么。

### 7.5 Screen

Screen 负责 UI 展示。

读 Screen 时不要被 Compose 嵌套吓到。抓住这几件事：

- 从 ViewModel 收集 State。
- 收集 Effect。
- 把 State 传给纯 UI 函数。
- 用户点击时调用 `viewModel.onIntent(...)`。
- Android 平台能力放在 Screen 层，例如打开系统播放器、Activity Result API。

---

## 8. 功能链路一：App 启动和导航

入口文件：

```text
MainActivity.kt
SimplePanApp.kt
navigation/AppNavGraph.kt
navigation/Routes.kt
```

启动流程：

```text
系统启动 Activity
-> MainActivity setContent
-> SimplePan Theme
-> AppNavGraph
-> NavHost 显示首页
```

`AppNavGraph` 还负责一个重要全局功能：

```text
App 回到前台
-> 检查剪贴板
-> 解析分享链接
-> 如果是合法 token，跳转分享预览页
```

你读 `AppNavGraph` 时重点看：

- `NavHost` 有哪些路由。
- 首页怎么跳文件页。
- 文件页怎么跳 TXT 阅读器。
- 分享预览页的 token 参数怎么传。
- 剪贴板检测为什么放在全局导航层。

---

## 9. 功能链路二：文件列表怎么显示

相关文件：

```text
ui/file/FileListContract.kt
ui/file/FileListViewModel.kt
ui/file/FileListScreen.kt
domain/repository/FileRepository.kt
data/repository/FileRepositoryImpl.kt
data/local/dao/FileDao.kt
```

流程：

```text
FileListScreen 进入组合
-> FileListViewModel 观察当前目录文件
-> FileRepository.observeFiles(...)
-> FileDao 返回 Flow<List<FileEntity>>
-> Mapper 转 CloudFile
-> ViewModel 更新 FileListState.files
-> FileListScreen 显示列表
```

你要理解：

- 当前目录靠 `parentId` 查。
- 根目录通常是 `parentId == null`。
- 文件夹点击后，当前目录 ID 变成文件夹 ID。
- 返回上一级靠 folder stack。

### 9.1 文件夹进入

点击文件夹：

```text
FileRow onTap
-> FileListIntent.EnterFolder(folderId, folderName)
-> ViewModel 更新 currentFolderId 和 folderStack
-> 重新观察该 folderId 下的文件
-> UI 列表刷新
```

### 9.2 返回上一级

返回：

```text
BackToParent
-> ViewModel 从 folderStack 弹出上一层
-> currentFolderId 恢复
-> Flow 查询上一层文件
```

你可以在 ViewModel 里找 `folderStack` 相关逻辑。

---

## 10. 功能链路三：筛选和排序

相关文件：

```text
FileListContract.kt
FileListViewModel.kt
FileListScreen.kt
```

筛选类型通常是：

```text
All
Image
Video
Document
```

关键思想：

```text
数据库给出当前目录全部文件
-> ViewModel 根据 filter 过滤
-> 再根据 sortType 排序
-> 最终 files 给 UI
```

为什么筛选不一定要写 SQL？

因为当前目录文件数量不大，放在 ViewModel 里处理简单清晰；后续如果真实大数据量，再考虑 SQL 查询优化。

你可以尝试一个练习：

> 新增“音频”筛选类型，看看需要改 Contract、UI 筛选栏、ViewModel 过滤逻辑哪些地方。

---

## 11. 功能链路四：管理模式

管理模式包括：

- 进入管理模式。
- 勾选/取消勾选。
- 全选/取消全选。
- 底部操作栏。
- 重命名。
- 删除。
- 移动。
- 分享。

核心状态：

```text
isManageMode
selectedFileIds
```

### 11.1 为什么退出管理模式要清空选中

如果不清空，会出现：

```text
用户退出管理模式
-> 再次进入
-> 上次选中的文件还被选中
```

这是很差的体验，也容易导致误删除。

所以退出管理模式时必须：

```text
isManageMode = false
selectedFileIds = emptySet()
```

这正是 MVI 单一 State 的好处：你能在一个地方看到状态同步变化。

### 11.2 重命名

重命名流程：

```text
选择一个文件
-> 打开重命名弹窗
-> 输入新名称
-> 校验空名
-> 校验重名
-> 保留扩展名
-> Repository 更新数据库
-> Room Flow 触发列表刷新
```

重点理解：

- 为什么只能单选重命名。
- 为什么要保留扩展名。
- 为什么重名校验要看同目录下文件。

### 11.3 删除

删除流程：

```text
选择文件
-> 删除确认弹窗
-> Repository 软删除
-> 列表查询过滤 isDeleted
-> UI 自动消失
```

重点理解：

- 删除不是从 UI list 里 remove。
- 删除是写数据库。
- UI 消失是 Flow 自动刷新结果。

### 11.4 移动

移动流程：

```text
选择文件
-> 打开目标文件夹弹窗
-> 选择目标文件夹
-> 校验不能移动到自身/自身子目录
-> 更新 parentId
-> 当前目录和目标目录列表自动刷新
```

移动本质上就是改文件的 `parentId`。

老师可能问：

> 为什么不能移动到自身子目录？

回答：

> 因为文件树会变成环，比如 A 移动到 A/B 下面，A 的父子关系就不再是树，后续遍历可能死循环。

---

## 12. 功能链路五：上传

相关文件：

```text
ui/file/FileListScreen.kt
ui/file/FileListViewModel.kt
domain/usecase/UploadFileUseCase.kt
domain/usecase/UploadSizePolicy.kt
data/storage/LocalFileMetadataReader.kt
data/storage/PrivateUploadStorage.kt
data/repository/FileRepositoryImpl.kt
data/repository/TransferRepositoryImpl.kt
```

### 12.1 为什么用 SAF

Android 新版本不鼓励 App 直接扫用户文件路径。SAF 通过系统文件选择器让用户主动授权。

用户选择文件后，App 拿到的是：

```text
Uri
```

不是：

```text
C:\xxx 或 /sdcard/xxx
```

### 12.2 上传完整流程

```text
用户点 +
-> WukongUploadSheet
-> 点击照片/视频/文档等
-> ActivityResultContracts.OpenDocument
-> 返回 Uri
-> FileListIntent.UploadPickedFile(uri)
-> UploadFileUseCase
-> LocalFileMetadataReader 读取名称/大小/MIME
-> UploadSizePolicy 判断是否超过 100MB
-> PrivateUploadStorage 复制到 App 私有目录
-> FileRepository 插入 file
-> TransferRepository 插入 transfer_history
-> 首页最近转存 Flow 自动刷新
```

### 12.3 为什么要复制到私有目录

SAF 授权不是永远可靠的真实路径访问。把文件复制到 App 私有目录后：

- 后续打开更稳定。
- App 自己有读写权限。
- 删除 App 时文件也会随 App 数据一起清理。

### 12.4 过大文件提示在哪里

`UploadSizePolicy` 负责大小策略。

你要记住这个阈值：

```text
100MB
```

答辩可以说：

> 大小校验放在 UseCase 层，是因为它是业务规则，不是 UI 规则。UI 只负责展示“文件过大”的结果。

---

## 13. 功能链路六：首页最近转存和最近浏览

相关文件：

```text
ui/home/PanHomeContract.kt
ui/home/PanHomeViewModel.kt
ui/home/PanHomeScreen.kt
domain/repository/RecentRepository.kt
data/repository/RecentRepositoryImpl.kt
data/local/dao/OpenHistoryDao.kt
data/local/dao/TransferHistoryDao.kt
```

### 13.1 最近转存

来源：

- 上传文件。
- 保存分享文件。

数据表：

```text
transfer_history
```

流程：

```text
上传/保存分享成功
-> 写 transfer_history
-> PanHomeViewModel 观察最近转存 Flow
-> 首页自动显示
```

### 13.2 最近浏览

来源：

- 打开 TXT。
- 成功拉起视频播放器。

数据表：

```text
open_history
```

流程：

```text
打开文件成功
-> RecordOpenUseCase
-> 写 open_history
-> 首页最近浏览 Flow 自动刷新
```

注意：

> 视频只有系统播放器真正启动成功后才记录最近浏览，否则“没有播放器”也写历史就不合理。

---

## 14. 功能链路七：TXT 阅读器

相关文件：

```text
ui/reader/TxtReaderContract.kt
ui/reader/TxtReaderViewModel.kt
ui/reader/TxtReaderScreen.kt
domain/usecase/ReadTxtFileUseCase.kt
```

### 14.1 阅读器做了什么

- 读取 TXT 内容。
- 分页。
- 显示当前页。
- 左右滑动翻页。
- 点击屏幕显示/隐藏底部 UI。
- 显示页码。
- 处理文件不存在或读取异常。

### 14.2 v1 固定字数分页的问题

固定字数分页看起来简单：

```text
每 500 个字符切一页
```

但它不理解真实排版：

- 中文和英文宽度不同。
- 屏幕宽度不同。
- 字体大小不同。
- 换行会改变高度。

所以 v1 会出现：

- 有的页很满。
- 有的页很空。
- 中英文混排不自然。

你要把 v1 当成“刻意迭代点”来讲：

> 我先做固定字数版，是为了快速跑通阅读链路；之后再根据真实效果发现问题，继续优化阅读体验。

### 14.3 最终版重点

最终版阅读器更强调用户体验：

- 默认沉浸阅读。
- 点击屏幕显示/隐藏底部 UI。
- 左右滑动翻页。
- 边界页不能继续翻。

答辩时可以说：

> 阅读器不是只把 TXT 打开，而是要让用户能舒服地读，所以交互状态也很重要。

---

## 15. 功能链路八：视频打开

相关文件：

```text
ui/openfile/VideoFileOpener.kt
data/storage/FileUriProvider.kt
domain/usecase/OpenFileUseCase.kt
AndroidManifest.xml
res/xml/file_paths.xml
```

### 15.1 为什么不能直接暴露文件路径

Android 7.0 之后，App 不能随便把 `file://` URI 暴露给其他 App，否则可能触发异常。

正确方式是：

```text
App 私有文件
-> FileProvider
-> content:// URI
-> FLAG_GRANT_READ_URI_PERMISSION
-> 系统播放器
```

### 15.2 FileProvider 的意义

FileProvider 像一个安全代理：

- 它不暴露真实路径。
- 它生成 `content://`。
- 它给外部播放器临时读权限。

答辩可以这么讲：

> FileProvider 体现的是 Android 的 URI 授权模型。外部 App 不是直接读取我的文件路径，而是通过我授权的 content URI 读取。

---

## 16. 功能链路九：分享和剪贴板识别

相关文件：

```text
domain/usecase/CreateShareUseCase.kt
domain/usecase/SaveShareToPanUseCase.kt
deeplink/ShareLinkBuilder.kt
deeplink/DeepLinkParser.kt
navigation/AppNavGraph.kt
ui/share/SharePreviewViewModel.kt
ui/share/SharePreviewScreen.kt
```

### 16.1 分享为什么用 token

错误做法：

```text
https://xxx/share?file_id=abc&path=/学习资料/项目说明.txt
```

问题：

- 暴露内部文件 ID。
- 暴露路径。
- 暴露文件名。
- 后续改数据库结构会影响链接。

当前做法：

```text
https://simplepan.example/s/{token}
```

token 对应本地分享快照。

### 16.2 分享快照是什么

分享时保存一份文件快照：

- 分享标题。
- 文件列表。
- 文件类型。
- 文件大小。

这样即使原文件后来改名，分享页也能展示分享时的内容。

### 16.3 剪贴板识别踩坑

问题现象：

```text
复制分享链接
-> 退出 App
-> 重新打开 App
-> 没有识别弹窗/没有跳分享页
```

原因：

- App 生命周期刚进入 `RESUMED` 时，窗口焦点可能还不稳定。
- Android 对剪贴板读取有限制。
- 如果监听注册太晚，冷启动可能错过第一次时机。

修复思路：

```text
repeatOnLifecycle(RESUMED)
-> 延迟一小段时间
-> 读取剪贴板
-> DeepLinkParser 解析
-> token 去重
-> 跳转分享预览页
```

这个坑非常适合写进复盘。

---

## 17. 搜索和传输页

### 17.1 搜索

相关文件：

```text
ui/search/PanSearchContract.kt
ui/search/PanSearchViewModel.kt
ui/search/PanSearchScreen.kt
```

搜索页的重点：

- 输入关键词。
- 提交搜索。
- 从真实 Room 数据查。
- 点击结果继续走打开文件链路。

它不是假搜索，也不是写死列表。

### 17.2 传输页

相关文件：

```text
ui/transfer/TransferListContract.kt
ui/transfer/TransferListViewModel.kt
ui/transfer/TransferListScreen.kt
ui/transfer/TransferSettingsScreen.kt
```

传输页展示的是历史记录，不是完整后台下载器。

可以这样理解：

```text
transfer_history
-> TransferRepository
-> TransferListViewModel
-> TransferListScreen
```

答辩要诚实说：

> 当前传输页是真实历史数据展示，但不是完整的后台任务队列。如果继续拓展，可以做进度、暂停、失败重试。

---

## 18. UI 对齐悟空浏览器怎么读

相关文件：

```text
ui/component/WukongChrome.kt
ui/component/WukongFileTypeIcon.kt
ui/home/PanHomeScreen.kt
ui/file/FileListScreen.kt
ui/space/WukongSpaceScreens.kt
```

### 18.1 WukongChrome

这里放公共 UI 组件：

- 顶部 `网盘 / 文件` Tab。
- 返回、搜索、传输图标。
- 分段筛选栏。
- 空状态。
- 右下角 `+` 按钮。

为什么抽公共组件？

> 首页和文件页都要用同一套悟空风格，如果每个页面自己写，风格很容易不一致。

### 18.2 WukongFileTypeIcon

这里用 Compose Canvas / Surface 画文件类型图标。

优点：

- 不依赖图片资源。
- 图标颜色统一。
- 文件列表、搜索页、上传弹窗可以复用。

### 18.3 UI 对齐不是只调颜色

这次 UI 踩坑很值得总结：

- 页面整体下移：Scaffold Insets 重复。
- 字体太粗：`FontWeight.Black` 用太多。
- 文件列表不像：用了 Material 卡片感。
- 上传弹窗不像：文字块不如图形图标。
- 最近区域不像：小卡片和全宽分区的产品气质不同。

你要理解：

> UI 还原是结构、密度、字重、图标语义、边距、圆角共同作用，不是单独某个颜色。

---

## 19. Compose 基础语法速查

### 19.1 Composable 是什么

```kotlin
@Composable
fun FileRow(file: CloudFile) {
    Text(text = file.name)
}
```

可以理解为：

```text
一个能根据输入数据画 UI 的函数
```

### 19.2 State 驱动 UI

Compose 不是：

```text
找到 TextView，然后 setText
```

而是：

```text
State 改了
-> Composable 重新执行
-> UI 自动变
```

### 19.3 remember

`remember` 用来在重组之间保存局部状态。

例如：

```kotlin
var showDialog by remember { mutableStateOf(false) }
```

适合 UI 临时状态，不适合业务长期状态。

### 19.4 LaunchedEffect

用于在 Composable 里启动协程，常见用途：

- 收集 ViewModel 的 Effect。
- 页面首次进入时请求焦点。
- 根据参数变化触发一次加载。

### 19.5 Modifier

Modifier 控制：

- 大小
- padding
- 点击
- 背景
- 对齐
- 滚动

读 UI 时，很多“像不像”的问题都在 Modifier 上。

---

## 20. Kotlin 语法速查

### 20.1 data class

```kotlin
data class CloudFile(val fileId: String, val name: String)
```

自动生成：

- `equals`
- `hashCode`
- `toString`
- `copy`

MVI 里经常用：

```kotlin
state.copy(isLoading = false)
```

### 20.2 sealed interface

用于表达有限结果。

例如：

```kotlin
sealed interface UploadFileResult {
    data class Success(...) : UploadFileResult
    data object TooLarge : UploadFileResult
    data object Failed : UploadFileResult
}
```

好处：

- when 分支必须处理完整。
- 比返回 String/null 更安全。

### 20.3 suspend

表示挂起函数，通常用于：

- 数据库 IO。
- 文件 IO。
- 网络请求。

它不是新线程，但可以和协程调度器配合。

### 20.4 Flow

Flow 表示一串异步数据。

在项目里：

```text
Room DAO Flow
-> Repository Flow
-> ViewModel StateFlow
-> Compose collectAsStateWithLifecycle
```

### 20.5 扩展函数

例如：

```kotlin
private fun Long.toSizeText(): String
```

调用时像对象自己的方法：

```kotlin
file.sizeBytes.toSizeText()
```

它本质上是 Kotlin 提供的语法糖。

---

## 21. 调试时怎么排查

### 21.1 App 崩溃

排查顺序：

1. 看 Android Studio Logcat。
2. 搜索 `FATAL EXCEPTION`。
3. 看第一段自己包名的堆栈。
4. 找到具体文件和行号。
5. 判断是空指针、导航参数、数据库、权限还是 UI。

不要只说“崩了”，要能说：

```text
崩溃发生在什么文件第几行
触发动作是什么
异常类型是什么
```

### 21.2 UI 没刷新

按这条链路排查：

```text
数据库有没有写成功
-> DAO Flow 有没有查询这张表
-> Repository 有没有 map 成 Domain Model
-> ViewModel 有没有 collect
-> State 有没有变化
-> Screen 有没有读取这个 State
```

### 21.3 mock 数据看不到

常见原因：

```text
Room 已经有旧数据
```

解决：

- 真机清除 App 数据。
- 或卸载重装。
- 再启动，让 mock 重新入库。

### 21.4 分享链接不识别

排查：

- 剪贴板里是不是 SimplePan 分享链接。
- token 格式是否正确。
- App 是否真的回到前台。
- `AppNavGraph` 是否收集到生命周期 RESUMED。
- 是否被 token 去重逻辑过滤。

### 21.5 上传失败

排查：

- SAF 是否返回 Uri。
- metadata 是否读取成功。
- 文件是否超过 100MB。
- 私有目录复制是否成功。
- Room 写入是否成功。
- Snackbar 是否显示错误。

---

## 22. 建议你按 7 天学习

### 第 1 天：只读架构和数据模型

目标：

- 看懂 `domain/model`。
- 看懂 Entity 和 DAO。
- 能解释 Entity / Domain Model / DTO 区别。

小练习：

- 画出 `FileDto -> FileEntity -> CloudFile` 的转换图。

### 第 2 天：读文件列表

目标：

- 看懂 `FileListContract`。
- 看懂进入文件夹和返回上一级。
- 看懂筛选排序。

小练习：

- 找到“点击文件夹进入子目录”的完整链路。

### 第 3 天：读管理模式

目标：

- 看懂选中状态。
- 看懂重命名、删除、移动。
- 能解释为什么移动要禁止自身子目录。

小练习：

- 自己写一段伪代码：退出管理模式时清空选中状态。

### 第 4 天：读上传

目标：

- 看懂 SAF。
- 看懂 100MB 校验。
- 看懂复制私有目录。
- 看懂写 file 和 transfer_history。

小练习：

- 把上传流程写成 8 步流程图。

### 第 5 天：读 TXT 和视频打开

目标：

- 看懂 TXT 阅读器状态。
- 看懂左右滑动翻页。
- 看懂 FileProvider 打开视频。

小练习：

- 解释 `file://` 和 `content://` 的区别。

### 第 6 天：读分享和剪贴板

目标：

- 看懂 token。
- 看懂分享快照。
- 看懂剪贴板识别修复。

小练习：

- 写出为什么分享链接不能包含 file_id/path。

### 第 7 天：读 UI 对齐和整理答辩

目标：

- 看懂 `WukongChrome`。
- 看懂文件图标。
- 看懂首页和上传弹窗结构。

小练习：

- 用自己的话总结“UI 不像悟空时，我们怎么拆问题”。

---

## 23. 你应该能回答的 20 个问题

1. 为什么选择 Android + Kotlin + Compose？
2. 为什么用 MVI，而不是普通 MVVM？
3. State、Intent、Effect 分别是什么？
4. 为什么 Toast/导航属于 Effect？
5. Room 的 Flow 为什么能让 UI 自动刷新？
6. 上传后首页最近转存为什么自动出现？
7. 打开 TXT 后首页最近浏览为什么自动出现？
8. 为什么 mock JSON 不能直接给 UI 用？
9. Entity 和 Domain Model 有什么区别？
10. 为什么上传用 SAF？
11. 为什么要复制文件到 App 私有目录？
12. 100MB 文件大小校验在哪里做？
13. 为什么视频打开要用 FileProvider？
14. `file://` 和 `content://` 有什么区别？
15. TXT 固定字数分页有什么问题？
16. 分享链接为什么只放 token？
17. 分享快照解决什么问题？
18. 剪贴板识别为什么要等 App 回到前台？
19. UI 整体下移是怎么修的？
20. 这次项目你最大的技术成长是什么？

如果这些问题你都能用自己的话讲出来，说明你已经不是“只会跑项目”，而是真的理解了。

---

## 24. 建议你亲手改的小功能

为了证明你不是只看代码，建议挑 2-3 个小功能自己动手：

### 练习 1：新增音频筛选

目标：

- 在文件页筛选栏增加“音频”。
- 点击后只显示音频文件。

你会接触：

- `FileFilter`
- `FileListViewModel`
- `FileFilterBar`

### 练习 2：修改上传过大阈值

目标：

- 把 100MB 改成 50MB。
- 修改提示文案。
- 真机测试超过 50MB 文件。

你会接触：

- `UploadSizePolicy`
- `UploadFileUseCase`
- Snackbar 提示。

### 练习 3：最近记录显示文件类型图标

目标：

- 首页最近转存/浏览行增加文件类型图标。

你会接触：

- `RecentHomeRow`
- `WukongFileTypeIcon`
- `RecentRecord`

### 练习 4：分享链接解析单元测试

目标：

- 给合法链接、非法 token、非 SimplePan 链接写测试。

你会接触：

- `DeepLinkParser`
- 单元测试。

---

## 25. 最后怎么把代码讲给老师听

不要按文件夹机械介绍。建议按“问题 -> 方案 -> 代码 -> 验证”讲。

示例：

```text
问题：上传文件后首页最近转存要自动刷新。

方案：上传成功不直接手动刷新首页，而是写入 transfer_history 表。
首页 ViewModel 订阅 transfer_history 的 Room Flow。
Room 发现表变化后重新发射数据，State 更新后 Compose 自动重组。

代码：UploadFileUseCase 负责上传编排，TransferHistoryDao 提供 Flow 查询，
PanHomeViewModel 收集最近转存记录，PanHomeScreen 展示。

验证：真机上传一个 TXT，返回首页，不手动刷新也能看到最近转存。
```

这个讲法比“我用了 Room、Flow、Compose”更有说服力。

---

## 26. 你现在最应该补的理解

按重要性排序：

1. `FileListState / Intent / Effect`。
2. Room DAO 返回 Flow 的自动刷新机制。
3. SAF 上传为什么只有 Uri。
4. FileProvider 为什么生成 content URI。
5. 分享 token + 快照的设计。
6. TXT 分页为什么要迭代。
7. Compose 为什么是 State 驱动 UI。

先把这 7 个点吃透，答辩基本就稳了。

---

## 27. 相关文档

建议配合阅读：

- `docs/FINAL_ACCEPTANCE_AND_REVIEW_OUTLINE.md`
- `docs/DECISIONS.md`
- `docs/TXT_PAGINATION_V1.md`
- `docs/REVIEW_MATERIALS.md`
- `docs/DEVELOPER_GUIDE.md`

阅读顺序：

```text
CODE_STUDY_GUIDE.md
-> FINAL_ACCEPTANCE_AND_REVIEW_OUTLINE.md
-> DECISIONS.md
-> TXT_PAGINATION_V1.md
-> REVIEW_MATERIALS.md
```

---

## 28. 结语

你不需要一次性记住所有代码。正确的学习方法是：

```text
先读懂一条链路
-> 再自己复述
-> 再改一个小点
-> 最后用真机验证
```

这个项目最有价值的地方，不是“功能很多”，而是它把 Android 客户端常见能力串成了一条完整链路：

```text
本地数据
-> 状态管理
-> 文件访问
-> 系统能力
-> UI 交互
-> 异常处理
-> 复盘表达
```

你真正掌握它以后，后面再做类似 App，就不是从零开始了。
