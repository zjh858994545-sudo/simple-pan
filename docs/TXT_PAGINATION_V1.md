# TXT 阅读器 v1 固定字数分页复盘

## 1. 这份文档解决什么问题

TXT 阅读器已经能打开本地 TXT 文件，并且支持上一页、下一页和左右滑动翻页。当前实现是 v1 版本，分页策略叫做“固定字数分页”。

简单说：不管手机屏幕多大、字体多大、每一行能放多少字，先规定“每页 500 个字符”，然后把全文按 500 个字符一段切开。

这个版本不是最终最优方案，而是一个刻意保留的迭代点。它的价值是：先用很少的代码做出可演示的阅读器，再通过它暴露真实排版问题，为后续 v2“测量分页”做对比。

## 2. 当前实现位置

主要代码在这些文件：

- `app/src/main/java/com/example/simple_pan/ui/reader/TxtReaderViewModel.kt`
  - 负责读取 TXT 后生成分页数组。
  - 核心函数是 `String.toFixedLengthPages()`。

- `app/src/main/java/com/example/simple_pan/ui/reader/TxtReaderContract.kt`
  - 定义阅读器页面状态。
  - 重点字段是 `pages`、`currentPageIndex`、`currentPageText`。

- `app/src/main/java/com/example/simple_pan/ui/reader/TxtReaderScreen.kt`
  - 负责显示当前页内容。
  - 负责上一页、下一页按钮和左右滑动手势。

- `app/src/main/java/com/example/simple_pan/domain/usecase/ReadTxtFileUseCase.kt`
  - 负责从 App 私有目录读取真实 TXT 文件内容。
  - 负责处理文件不存在、路径缺失、编码不支持等异常。

## 3. 整体链路

用户点击 TXT 文件后，整个流程是：

1. 文件列表把点击事件发给 `FileListViewModel`。
2. `OpenFileUseCase` 判断文件类型。
3. 如果是 TXT，发出进入阅读器页面的事件。
4. `AppNavGraph` 导航到 `TxtReaderScreen`。
5. `TxtReaderScreen` 把 `fileId` 和 `fileName` 发给 `TxtReaderViewModel`。
6. `TxtReaderViewModel` 调用 `ReadTxtFileUseCase` 读取全文。
7. 读取成功后调用 `toFixedLengthPages()` 把全文切成页数组。
8. UI 从 `state.currentPageText` 取当前页文本并显示。

用一句话概括：文件打开逻辑判断“能不能进阅读器”，阅读器 ViewModel 负责“怎么分页”，Screen 只负责“显示哪一页”。

## 4. v1 分页算法核心思想

当前分页算法非常直接：

```kotlin
private fun String.toFixedLengthPages(): List<String> {
    if (isEmpty()) {
        return emptyList()
    }

    val pages = mutableListOf<String>()
    var startIndex = 0
    while (startIndex < length) {
        val endIndex = minOf(startIndex + FIXED_PAGE_CHAR_COUNT, length)
        pages += substring(startIndex, endIndex)
        startIndex = endIndex
    }
    return pages
}
```

核心变量：

- `FIXED_PAGE_CHAR_COUNT = 500`
  - 表示每页最多放 500 个字符。

- `startIndex`
  - 当前页从全文的哪个位置开始切。

- `endIndex`
  - 当前页切到哪里结束。
  - 用 `minOf(startIndex + 500, length)` 是为了避免最后一页越界。

- `pages`
  - 保存切出来的所有页面文本。

## 5. 用一个简单例子理解

假设全文有 1200 个字符，每页 500 个字符：

- 第 1 页：第 0 到 499 个字符。
- 第 2 页：第 500 到 999 个字符。
- 第 3 页：第 1000 到 1199 个字符。

所以最终会得到 3 页。

这里的重点是：v1 只关心“字符数量”，不关心“这些字符在屏幕上实际占多少空间”。

## 6. 为什么 v1 先这么做

我当时选择固定字数分页，主要是因为它适合阶段性实现：

1. 实现成本低
   - 不需要立刻研究 Compose 文本测量。
   - 不需要处理不同屏幕、字体、行高、换行规则。

2. 行为稳定
   - 同一篇文章、同一个 `FIXED_PAGE_CHAR_COUNT`，页数是稳定的。
   - 很容易测试“上一页/下一页/边界禁用”。

3. 方便先完成主流程
   - 阶段目标是先让 TXT 文件能打开、能分页、能翻页。
   - 如果一开始就做测量分页，容易卡在排版细节，影响整条文件打开链路。

4. 适合做前后对比
   - v1 的缺点很明显。
   - 后面做 v2 测量分页时，可以截图对比，说明自己不是只做功能，而是在迭代体验。

## 7. 当前 State 设计

`TxtReaderState` 里和分页有关的字段是：

```kotlin
val pages: List<String> = emptyList()
val currentPageIndex: Int = 0

val currentPageText: String
    get() = pages.getOrNull(currentPageIndex).orEmpty()

val pageCount: Int
    get() = pages.size

val currentPageNumber: Int
    get() = if (pages.isEmpty()) 0 else currentPageIndex + 1

val canGoPrevious: Boolean
    get() = currentPageIndex > 0

val canGoNext: Boolean
    get() = currentPageIndex < pages.lastIndex
```

这里有一个重要设计点：UI 不直接计算当前页文本，而是从 State 里拿 `currentPageText`。

这样做的好处是：

- UI 不需要知道分页数组怎么来的。
- UI 不需要自己判断页码是否越界。
- 后续 v2 替换分页算法时，Screen 层可以尽量不动。

## 8. 翻页逻辑

上一页：

```kotlin
private fun goToPreviousPage() {
    _state.update { currentState ->
        if (currentState.canGoPrevious) {
            currentState.copy(currentPageIndex = currentState.currentPageIndex - 1)
        } else {
            currentState
        }
    }
}
```

下一页：

```kotlin
private fun goToNextPage() {
    _state.update { currentState ->
        if (currentState.canGoNext) {
            currentState.copy(currentPageIndex = currentState.currentPageIndex + 1)
        } else {
            currentState
        }
    }
}
```

这两个函数只改 `currentPageIndex`，不会重新读取文件，也不会重新分页。

这样设计的原因：

- 文件内容已经在 `content` 和 `pages` 里了。
- 翻页只是展示位置变化，不应该重复做 IO。
- 边界判断集中在 ViewModel，按钮点击和滑动手势可以复用同一套逻辑。

## 9. 左右滑动翻页

`TxtReaderScreen` 里用 `detectHorizontalDragGestures` 检测左右滑动。

目前实现里：

- 左滑：上一页。
- 右滑：下一页。
- 滑动距离超过阈值才触发，阈值是 `72.dp`。

这里还有一个小细节：手势回调拿到的是像素距离，但阈值用的是 dp，所以代码里先用 `LocalDensity` 把 dp 转成 px。

这样能避免不同屏幕密度下滑动距离体验差异太大。

## 10. v1 的优点

v1 固定字数分页的优点很明确：

1. 简单
   - 核心代码只有一个 `while` 循环。

2. 可控
   - 每页字符数固定，页数容易推导。

3. 容易调试
   - 如果页数不对，只需要看全文长度和每页字符数。

4. 容易演示
   - 能快速展示“打开 TXT 文件”和“翻页阅读”的完整链路。

5. 给后续迭代留空间
   - 它的缺点刚好能引出 v2 测量分页。

## 11. v1 的缺点

v1 最大的问题是：字符数不等于屏幕排版空间。

具体缺点：

1. 不考虑屏幕高度
   - 小屏手机可能一页 500 字放不下，需要在页内继续滚动。
   - 大屏手机可能一页显示不满，下面留很多空白。

2. 不考虑字体大小
   - 如果用户调大字号，同样 500 字会占更多空间。
   - 如果字号小，同样 500 字又可能太少。

3. 不考虑中英文宽度差异
   - 中文字符通常接近等宽。
   - 英文单词、标点、空格宽度不一样。
   - 单纯按字符数切，无法保证每页视觉高度接近。

4. 不考虑自然段落
   - 可能在一句话中间切断。
   - 可能在代码块中间切断。
   - 阅读体验不够自然。

5. 不考虑 Compose 实际换行
   - 真正显示时，Compose 会根据容器宽度、字体、行高自动换行。
   - v1 算法完全不知道最终会换成多少行。

## 12. v2 为什么要做“测量分页”

v2 的目标不是按固定字符数切，而是根据真实 UI 可用空间切。

简单理解：

1. 先知道正文区域宽度和高度。
2. 知道字体大小、行高、样式。
3. 用 Compose 的文本测量能力计算“一段文字实际会占多高”。
4. 不断尝试往当前页加文字。
5. 一旦超过页面高度，就把前面能放下的部分作为一页。

v2 追求的是：每一页在当前设备上刚好填满屏幕，不需要页内滚动，也不浪费太多空白。

## 13. v1 到 v2 的可讲述迭代路线

答辩时可以这样讲：

第一版我没有一开始就做复杂排版，而是先做固定字数分页。这样可以快速验证 TXT 文件打开、内容读取、状态管理、翻页交互和最近浏览联动。

后来我发现固定字数分页有明显问题：同样 500 字在不同屏幕、不同字号下占用空间不一样，甚至会出现一页内部还要滚动的情况。这说明分页不能只看字符串长度，还要看文本真实渲染后的尺寸。

所以后续 v2 准备改成测量分页：根据正文区域尺寸、字体样式和 Compose 文本测量结果来决定每页放多少内容。这样阅读体验会更接近真实阅读器。

## 14. 我从 v1 学到的东西

1. 功能可以先闭环，再优化算法
   - 如果一开始追求完美分页，可能会拖慢整个文件打开功能。

2. UI 状态要设计得稳定
   - `pages + currentPageIndex` 这组状态很清楚，后续替换分页算法也不影响翻页 UI。

3. 算法要服务体验
   - 固定字数分页算法很简单，但体验不一定好。
   - 真正的阅读器分页需要考虑显示尺寸，而不是只考虑字符串长度。

4. 需要保留可对比材料
   - v1 截图能展示缺陷。
   - v2 截图能展示改进。
   - 这比只说“我优化了分页”更有说服力。

## 15. 怎么验证 v1

验证路径：

1. 打开 App，进入“文件”页。
2. 进入有 TXT 文件的目录。
3. 点击 TXT 文件。
4. 进入 TXT 阅读器。
5. 看底部页码，例如 `1 / 17`。
6. 点击“下一页”，页码应该变成 `2 / 17`。
7. 点击“上一页”，页码应该回到 `1 / 17`。
8. 第一页时“上一页”按钮应该不可点。
9. 最后一页时“下一页”按钮应该不可点。
10. 左右滑动时，页码应该跟着变化。

重点观察：

- 是否能正确显示 TXT 内容。
- 是否能正确分页。
- 是否不会越界。
- 是否有页内滚动。
- 是否存在句子被硬切断的问题。

## 16. 可以放进飞书文档的简短版

TXT 阅读器第一版采用固定字数分页：读取完整 TXT 内容后，每 500 个字符切成一页，页面状态保存为 `pages + currentPageIndex`。这样可以快速完成“打开 TXT、显示内容、上一页、下一页、滑动翻页”的完整链路。

这个方案实现简单、稳定、容易调试，但缺点也明显：它只关心字符串长度，不关心屏幕尺寸、字体大小、行高和中英文宽度，所以实际显示时可能出现一页过长、页内滚动、句子被切断等问题。

后续 v2 准备改成基于文本测量的分页：根据正文区域大小和字体样式计算每页能显示多少内容，让每一页更接近真实阅读器效果。v1 的截图可以作为对比材料，说明分页算法是逐步迭代出来的。
