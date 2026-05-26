package com.example.simple_pan.ui.file

import com.example.simple_pan.domain.model.CloudFile

// [语法] enum class 和 Java enum 类似，用一组固定常量表达有限选项。
// [设计] 为什么这样写：筛选项属于 UI 状态，不适合直接复用 FileType；例如“全部”不是文件类型，而是筛选策略。
enum class FileFilter {
    All,
    Image,
    Video,
    Document
}

// [语法] enum class 和 Java enum 类似，when 判断时能被编译器检查是否覆盖所有分支。
// [设计] 为什么这样写：阶段 2 先只实现综合排序，但用枚举保留扩展点，后续加按时间/名称排序不会改 State 结构。
enum class FileSortType {
    Comprehensive
}

// [语法] data class 相当于 Java 的 POJO/Bean，用于表达面包屑路径上的一段目录。
// [设计] 为什么这样写：路径栈不能只存 folderId，否则 UI 无法显示当前层级名称；把 id 和 name 放一起能支持“返回上一级”和标题展示。
data class FolderCrumb(
    val folderId: String?,
    val folderName: String
)

// [语法] data class 相当于 Java 的 POJO/Bean，适合承载弹窗所需的一组不可变 UI 状态。
// [设计] 为什么这样写：重命名弹窗有输入值、扩展名、错误提示和提交中状态，集中到 State 里能让 UI 只负责展示，不自己保存临时变量。
data class RenameDialogState(
    val isVisible: Boolean = false,
    // [语法] String? 表示可能没有目标文件，相当于 Java 的 @Nullable String。
    // [设计] 为什么这样写：弹窗关闭时没有重命名目标，用 null 明确表达“当前无目标”。
    val fileId: String? = null,
    val originalName: String = "",
    val editableName: String = "",
    val preservedExtension: String = "",
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false
)

// [语法] data class 相当于 Java 的 POJO/Bean，适合保存删除确认弹窗的一组状态。
// [设计] 为什么这样写：删除是高风险操作，弹窗需要固定当时选中的 id 集合，避免用户打开弹窗后列表刷新导致删除目标变化。
data class DeleteDialogState(
    val isVisible: Boolean = false,
    val fileIds: Set<String> = emptySet(),
    val selectedCount: Int = 0,
    val containsFolder: Boolean = false,
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false
)

// [语法] data class 相当于 Java 的 POJO/Bean，Kotlin 会自动生成 equals/hashCode/toString/copy。
// [设计] 为什么这样写：文件列表页用一个不可变 State 表达 loading、empty、error 和列表数据，Compose 只根据 State 重组，不在 UI 里手动刷新。
data class FileListState(
    val files: List<CloudFile> = emptyList(),
    // [语法] String? 表示可空字符串，相当于 Java 的 @Nullable String；根目录没有 folderId，所以用 null 表示。
    // [设计] 为什么这样写：currentFolderId 是后续 observeFiles(parentId) 的唯一来源，UI 不直接拼查询条件。
    val currentFolderId: String? = null,
    val currentFolderName: String = "根目录",
    val folderStack: List<FolderCrumb> = emptyList(),
    val filter: FileFilter = FileFilter.All,
    val sortType: FileSortType = FileSortType.Comprehensive,
    // [语法] Set<String> 类似 Java 的 Set<String>，用于保存不重复的文件 id。
    // [设计] 为什么这样写：管理模式的选择状态必须按 fileId 保存，不能按列表下标保存；否则筛选、排序、删除后容易选错行。
    val selectedFileIds: Set<String> = emptySet(),
    // [设计] 为什么这样写：用一个布尔值明确区分普通浏览态和管理态，后续底部操作栏、勾选圆圈都只依赖这一个状态来源。
    val isManageMode: Boolean = false,
    val isLoading: Boolean = true,
    // [语法] String? 表示可空字符串，相当于 Java 的 @Nullable String；没有错误时用 null 表示。
    val errorMessage: String? = null,
    val renameDialog: RenameDialogState = RenameDialogState(),
    val deleteDialog: DeleteDialogState = DeleteDialogState(),
    val initializedFromMock: Boolean = false
)

// [语法] sealed interface 类似 Java 里“受限的接口/抽象父类”，实现类型只能在编译期确定，when 分支更安全。
// [设计] 为什么这样写：用户行为统一收敛成 Intent，后续加入进入文件夹、筛选、排序时不会把点击逻辑散落到 Composable。
sealed interface FileListIntent {
    // [语法] data object 是 Kotlin 单例对象，类似 Java enum 单例值；这里不需要额外字段。
    // [设计] 为什么这样写：Retry 表示用户要求重新初始化并观察列表，UI 不直接调用 Repository。
    data object Retry : FileListIntent

    // [语法] data class 用于携带参数，相当于 Java 里一个只保存 folderId/folderName 的事件对象。
    // [设计] 为什么这样写：进入文件夹要同时保存 id 和展示名称，ViewModel 可以更新路径栈并重新观察子目录。
    data class EnterFolder(val folderId: String, val folderName: String) : FileListIntent

    // [语法] data object 是无参数单例事件，类似 Java enum 中的 BACK_TO_PARENT。
    // [设计] 为什么这样写：返回上一级不需要 UI 计算目标目录，目标由 ViewModel 根据 folderStack 决定，避免 UI 管业务状态。
    data object BackToParent : FileListIntent

    // [设计] 为什么这样写：筛选变化属于用户意图，由 ViewModel 更新 State 后统一派生列表，不让 Composable 自己过滤数据。
    data class ChangeFilter(val filter: FileFilter) : FileListIntent

    // [设计] 为什么这样写：排序变化属于用户意图，即使当前只有综合排序，也先保留 MVI 入口。
    data class ChangeSort(val sortType: FileSortType) : FileListIntent

    // [语法] data object 是 Kotlin 的单例事件，类似 Java enum 里的一个固定值。
    // [设计] 为什么这样写：进入管理模式不需要携带参数，统一通过 Intent 进入，避免 UI 直接修改 State。
    data object EnterManageMode : FileListIntent

    // [语法] data object 是 Kotlin 的单例事件，适合表达“退出管理模式”这种无参数动作。
    // [设计] 为什么这样写：退出时必须同时清空选中状态，把规则收敛在 ViewModel，防止不同 UI 入口遗漏清理。
    data object ExitManageMode : FileListIntent

    // [语法] data class 用来携带 fileId，相当于 Java 里一个只保存 fileId 字段的事件对象。
    // [设计] 为什么这样写：选择状态必须由 ViewModel 统一切换，UI 只表达“这个文件被点了”，避免 Composable 自己维护局部选择状态。
    data class ToggleFileSelection(val fileId: String) : FileListIntent

    // [语法] data object 是无参数单例事件，类似 Java enum 里的 TOGGLE_SELECT_ALL_VISIBLE。
    // [设计] 为什么这样写：全选必须由 ViewModel 基于当前可见列表计算，避免 UI 把筛选/排序后的列表规则复制一份。
    data object ToggleSelectAllVisible : FileListIntent

    // [设计] 为什么这样写：打开重命名弹窗要由 ViewModel 根据选中集合决定目标文件，UI 不自己挑文件。
    data object OpenRenameDialog : FileListIntent

    // [设计] 为什么这样写：关闭弹窗要清空临时输入和错误信息，集中在 ViewModel 里避免 UI 漏清状态。
    data object DismissRenameDialog : FileListIntent

    // [语法] data class 用来携带输入框内容，相当于 Java 事件对象 RenameInputChanged。
    // [设计] 为什么这样写：输入框内容也走 Intent，空名错误可以在用户继续输入时自然清掉。
    data class ChangeRenameInput(val inputName: String) : FileListIntent

    // [语法] data object 表示无参数提交动作，类似 Java enum 里的 CONFIRM_RENAME。
    // [设计] 为什么这样写：确认按钮只表达用户提交，具体空名、重名、写库逻辑留给 ViewModel。
    data object ConfirmRename : FileListIntent

    // [设计] 为什么这样写：打开删除确认框要冻结当前选中集合，避免 UI 直接把可变的 selectedFileIds 当删除目标。
    data object OpenDeleteDialog : FileListIntent

    // [设计] 为什么这样写：取消删除要清理提交状态和错误信息，保持下一次打开弹窗是干净状态。
    data object DismissDeleteDialog : FileListIntent

    // [语法] data object 表示无参数提交动作，类似 Java enum 里的 CONFIRM_DELETE。
    // [设计] 为什么这样写：确认删除只表达用户意图，递归软删除和刷新交给 ViewModel/Repository/Room Flow。
    data object ConfirmDelete : FileListIntent
}
