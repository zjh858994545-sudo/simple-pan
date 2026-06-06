package com.example.simple_pan.ui.theme

import androidx.compose.ui.graphics.Color

// [设计] 为什么这样写：用稳定的蓝色作为主色，避免默认模板紫色在网盘工具类 App 中显得过于示例化。
val CloudBlue = Color(0xFF2563EB)
val CloudBlueDark = Color(0xFF9DB7FF)
val CloudBlueContainer = Color(0xFFDCE7FF)
val CloudBlueDarkContainer = Color(0xFF1E3A6D)

// [设计] 为什么这样写：青绿色用于辅助状态和容量信息，和主蓝色区分开，避免界面只剩单一蓝色层次。
val TransferTeal = Color(0xFF0F766E)
val TransferTealDark = Color(0xFF7DD3C7)
val TransferTealContainer = Color(0xFFCDEFEA)
val TransferTealDarkContainer = Color(0xFF17413D)

// [设计] 为什么这样写：珊瑚红用于强调分享、删除等高注意力信息，但不作为大面积背景色。
val ActionCoral = Color(0xFFDC4A3D)
val ActionCoralDark = Color(0xFFFFB4AA)
val ActionCoralContainer = Color(0xFFFFDAD4)
val ActionCoralDarkContainer = Color(0xFF6B231C)

val AppBackground = Color(0xFFF7F9FC)
val AppSurface = Color(0xFFFFFFFF)
val AppSurfaceVariant = Color(0xFFE8EEF8)
val AppDarkBackground = Color(0xFF101418)
val AppDarkSurface = Color(0xFF181D23)
val AppDarkSurfaceVariant = Color(0xFF2D333B)
