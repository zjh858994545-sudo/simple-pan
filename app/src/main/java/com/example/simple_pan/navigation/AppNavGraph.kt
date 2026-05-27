package com.example.simple_pan.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.simple_pan.ui.file.FileListScreen
import com.example.simple_pan.ui.home.PanHomeScreen
import com.example.simple_pan.ui.reader.TxtReaderScreen

// [设计] 为什么这样写：AppNavGraph 是全局导航入口，Activity 不关心具体页面，后续 Reader/Share 页面也能统一接进这里。
@Composable
fun AppNavGraph() {
    // [语法] rememberNavController() 会在 Compose 重组间记住 NavController，类似 Java 中把导航控制器保存在成员变量里。
    // [设计] 为什么这样写：NavController 必须在顶层稳定持有，否则重组时导航栈可能被重建，Tab 返回状态也会丢失。
    val navController = rememberNavController()
    val currentTopLevelRoute = navController.currentTopLevelRoute()

    Scaffold(
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
        }
    }
}

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
