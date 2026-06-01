package com.example.simple_pan.navigation

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.simple_pan.deeplink.DeepLinkParseResult
import com.example.simple_pan.deeplink.DeepLinkParser
import com.example.simple_pan.ui.file.FileListScreen
import com.example.simple_pan.ui.home.PanHomeScreen
import com.example.simple_pan.ui.reader.TxtReaderScreen
import com.example.simple_pan.ui.share.SharePreviewScreen
import kotlinx.coroutines.delay

// [设计] 为什么这样写：AppNavGraph 是全局导航入口，Activity 不关心具体页面，后续 Reader/Share 页面也能统一接进这里。
@Composable
fun AppNavGraph() {
    // [语法] rememberNavController() 会在 Compose 重组间记住 NavController，类似 Java 中把导航控制器保存在成员变量里。
    // [设计] 为什么这样写：NavController 必须在顶层稳定持有，否则重组时导航栈可能被重建，Tab 返回状态也会丢失。
    val navController = rememberNavController()
    val currentTopLevelRoute = navController.currentTopLevelRoute()
    val snackbarHostState = remember { SnackbarHostState() }
    ClipboardShareLinkHandler(
        navController = navController,
        snackbarHostState = snackbarHostState
    )

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        bottomBar = {
            // [设计] 为什么这样写：阅读器是沉浸式二级页面，不属于底部 Tab；只在顶层页面显示底部导航，避免阅读时误触切换页面。
            if (currentTopLevelRoute != null) {
                SimplePanBottomBar(
                    currentRoute = currentTopLevelRoute,
                    onDestinationClick = { destination ->
                        navController.navigate(destination.route) {
                            // [语法] lambda 里直接访问 destination，是 Kotlin 尾随 lambda 捕获外部变量的写法，类似 Java 匿名类闭包。
                            // [设计] 为什么这样写：切换底部 Tab 时回到图的起点，并保存/恢复状态，避免重复点 Tab 堆出多个相同页面。
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        // [语法] innerPadding 是 Scaffold 通过 lambda 传入的参数，类似 Java 回调参数；Modifier.padding 用它避开底部导航栏。
        // [设计] 为什么这样写：NavHost 放在 Scaffold 内容区，页面天然获得底部栏避让，不需要每个 Screen 自己处理系统间距。
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                PanHomeScreen()
            }
            composable(Routes.FILES) {
                FileListScreen(
                    onOpenTxtReader = { fileId, fileName ->
                        navController.navigate(Routes.txtReader(fileId, fileName))
                    },
                    onOpenSharePreview = { token ->
                        navController.navigate(Routes.sharePreview(token))
                    }
                )
            }
            composable(
                route = Routes.TXT_READER_ROUTE,
                arguments = listOf(
                    navArgument(Routes.TXT_READER_FILE_ID_ARG) {
                        type = NavType.StringType
                    },
                    navArgument(Routes.TXT_READER_FILE_NAME_ARG) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                // [语法] ?. 是 Kotlin 安全调用，相当于 Java 里先判断 arguments 是否为 null 再取值。
                // [设计] 为什么这样写：路由参数只用于定位阅读目标；如果极端情况下缺失，页面仍展示空骨架而不是崩溃。
                val fileId = backStackEntry.arguments?.getString(Routes.TXT_READER_FILE_ID_ARG).orEmpty()
                val fileName = backStackEntry.arguments?.getString(Routes.TXT_READER_FILE_NAME_ARG).orEmpty()
                TxtReaderScreen(
                    fileId = fileId,
                    fileName = fileName,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            // [设计] 为什么这样写：分享预览页是二级页面，只通过 token 进入，不加入底部 Tab；后续剪贴板识别和系统 DeepLink 都复用这条路由。
            composable(
                route = Routes.SHARE_PREVIEW_ROUTE,
                arguments = listOf(
                    navArgument(Routes.SHARE_PREVIEW_TOKEN_ARG) {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                // [语法] ?. 是 Kotlin 安全调用，相当于 Java 里先判断 arguments 是否为 null 再取值。
                // [设计] 为什么这样写：极端情况下 token 缺失时先进入骨架页，下一步加载真实快照时再统一显示错误状态。
                val token = backStackEntry.arguments?.getString(Routes.SHARE_PREVIEW_TOKEN_ARG).orEmpty()
                SharePreviewScreen(
                    token = token,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

// [设计] 为什么这样写：剪贴板检测属于 App 级入口能力，放在导航层可以复用现有分享预览路由，不让 Activity 或具体页面直接处理跳转。
@Composable
private fun ClipboardShareLinkHandler(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastHandledShareToken by remember { mutableStateOf<String?>(null) }

    // [语法] LaunchedEffect 会在组合中启动协程，并随 key 变化自动重启；repeatOnLifecycle 会在 RESUMED 状态运行内部代码。
    // [设计] 为什么这样写：剪贴板读取要等 App 真正回到前台后再做，延迟一小段时间能避开冷启动时窗口尚未获得前台权限的时机问题。
    LaunchedEffect(context, lifecycleOwner, navController, snackbarHostState) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(CLIPBOARD_SHARE_DETECTION_DELAY_MS)
            when (val result = context.detectClipboardShareLink()) {
                is ClipboardShareDetectionResult.Share -> {
                    if (result.token != lastHandledShareToken) {
                        lastHandledShareToken = result.token
                        navController.navigate(Routes.sharePreview(result.token)) {
                            // [设计] 为什么这样写：如果用户已经在同一个分享预览页，回到前台时不要再堆一层重复页面。
                            launchSingleTop = true
                        }
                        snackbarHostState.showSnackbar("已识别剪贴板分享链接")
                    }
                }
                ClipboardShareDetectionResult.InvalidShareLink -> {
                    snackbarHostState.showSnackbar("剪贴板中的分享链接无效")
                }
                ClipboardShareDetectionResult.NoShareLink -> Unit
            }
        }
    }
}

// [语法] sealed interface 表示受限结果类型，类似 Java 里固定子类集合的抽象父类型。
// [设计] 为什么这样写：剪贴板检测要区分“没分享链接”和“有 SimplePan 链接但格式不对”，这样验证时能看到到底卡在哪一步。
private sealed interface ClipboardShareDetectionResult {
    // [语法] data class 相当于 Java 的只读结果对象，用来携带解析成功后的 token。
    // [设计] 为什么这样写：导航只需要 token，不把原始剪贴板文本继续往下传，避免 UI 层误用 file_id/path 明文。
    data class Share(val token: String) : ClipboardShareDetectionResult

    // [语法] data object 是 Kotlin 单例对象，适合表达没有额外字段的固定结果。
    // [设计] 为什么这样写：普通剪贴板文本不应该打扰用户，所以 NoShareLink 会被静默忽略。
    data object NoShareLink : ClipboardShareDetectionResult

    // [设计] 为什么这样写：如果剪贴板里有 SimplePan 链接但缺 token 或 token 非法，就给用户可见提示，方便定位复制内容问题。
    data object InvalidShareLink : ClipboardShareDetectionResult
}

// [语法] 这是 Context 的扩展函数，相当于 Java 静态工具方法 ClipboardShareDetector.detect(context)。
// [设计] 为什么这样写：读取剪贴板是 Android 平台能力，解析规则由 DeepLinkParser 负责；这里把两者衔接成可供导航层判断的结果。
private fun Context.detectClipboardShareLink(): ClipboardShareDetectionResult {
    val clipboardText = readClipboardTextOrNull()
        ?: return ClipboardShareDetectionResult.NoShareLink
    return when (val result = DeepLinkParser.parse(clipboardText)) {
        is DeepLinkParseResult.Share -> ClipboardShareDetectionResult.Share(result.token)
        DeepLinkParseResult.InvalidToken,
        DeepLinkParseResult.MissingToken,
        DeepLinkParseResult.UnsupportedRoute -> ClipboardShareDetectionResult.InvalidShareLink
        DeepLinkParseResult.NotSimplePanLink -> ClipboardShareDetectionResult.NoShareLink
    }
}

// [语法] 这是 Context 的扩展函数；as? 是安全类型转换，失败时返回 null，类似 Java 的 instanceof 判断后再 cast。
// [设计] 为什么这样写：剪贴板可能为空、不是文本或系统服务不可用，统一转成 null，避免前台检测打断正常启动。
private fun Context.readClipboardTextOrNull(): String? {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    val clipData = try {
        clipboardManager.primaryClip
    } catch (exception: SecurityException) {
        null
    } ?: return null

    if (clipData.itemCount <= 0) {
        return null
    }

    val firstItemText = clipData.getItemAt(0)
        .coerceToText(this)
        ?.toString()
        ?.trim()
    return firstItemText?.takeIf { text -> text.isNotBlank() }
}

// [设计] 为什么这样写：Android 回前台后剪贴板读取权限可能比生命周期事件稍晚稳定，给窗口获取焦点留一个很短缓冲。
private const val CLIPBOARD_SHARE_DETECTION_DELAY_MS = 300L

// [设计] 为什么这样写：底部栏单独拆函数，后续如果要改样式或增加 badge，不会干扰 NavHost 的路由配置。
@Composable
private fun SimplePanBottomBar(
    currentRoute: String?,
    onDestinationClick: (TopLevelDestination) -> Unit
) {
    NavigationBar {
        for (destination in topLevelDestinations) {
            val selected = destination.route == currentRoute
            NavigationBarItem(
                selected = selected,
                onClick = { onDestinationClick(destination) },
                label = {
                    Text(text = destination.label)
                },
                icon = {
                    // [设计] 为什么这样写：当前阶段不额外引入图标依赖，先用稳定文本首字作为可访问的 Tab 视觉锚点。
                    Text(text = destination.label.first().toString())
                }
            )
        }
    }
}

// [语法] 这是扩展函数，相当于 Java 静态工具方法 NavControllerExt.currentTopLevelRoute(navController)。
// [设计] 为什么这样写：当前选中 Tab 的判断属于导航细节，封装后 BottomBar 只拿 route，不直接接触 back stack。
@Composable
private fun androidx.navigation.NavController.currentTopLevelRoute(): String? {
    // [语法] by 是 Kotlin 委托语法，这里把 State<NavBackStackEntry?> 委托成普通变量，类似 Java 里每次调用 state.getValue()。
    val navBackStackEntry by currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    for (destination in topLevelDestinations) {
        if (currentDestination?.hierarchy?.any { navDestination -> navDestination.route == destination.route } == true) {
            return destination.route
        }
    }
    return null
}
