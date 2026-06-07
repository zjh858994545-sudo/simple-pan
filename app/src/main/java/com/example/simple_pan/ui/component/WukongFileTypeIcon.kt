package com.example.simple_pan.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.simple_pan.domain.model.FileType

// [设计] 为什么这样写：文件类型图标在文件页、搜索页、传输页和分享页都会出现，抽成公共组件后能统一对齐悟空网盘的视觉风格。
@Composable
fun WukongFileTypeIcon(
    fileType: FileType,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        when (fileType) {
            FileType.Folder -> WukongFolderIcon()
            FileType.Video -> WukongVideoIcon()
            FileType.Txt -> WukongDocumentIcon()
            FileType.Image -> WukongImageIcon()
            FileType.Audio -> WukongAudioIcon()
            FileType.Other -> WukongArchiveIcon()
        }
    }
}

@Composable
private fun WukongFolderIcon() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val radius = w * 0.12f

        drawRoundRect(
            color = Color(0xFF91A5FF),
            topLeft = Offset(w * 0.08f, h * 0.18f),
            size = Size(w * 0.36f, h * 0.18f),
            cornerRadius = CornerRadius(radius, radius)
        )
        drawRoundRect(
            color = Color(0xFF6F86F6),
            topLeft = Offset(w * 0.04f, h * 0.30f),
            size = Size(w * 0.92f, h * 0.58f),
            cornerRadius = CornerRadius(radius, radius)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.46f),
            topLeft = Offset(w * 0.18f, h * 0.50f),
            size = Size(w * 0.62f, h * 0.08f),
            cornerRadius = CornerRadius(radius, radius)
        )
    }
}

@Composable
private fun WukongImageIcon() {
    IconTile(color = Color(0xFFF8B83E)) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = Color.White,
            radius = w * 0.08f,
            center = Offset(w * 0.68f, h * 0.30f)
        )
        val mountain = Path().apply {
            moveTo(w * 0.20f, h * 0.72f)
            lineTo(w * 0.38f, h * 0.48f)
            lineTo(w * 0.52f, h * 0.64f)
            lineTo(w * 0.62f, h * 0.52f)
            lineTo(w * 0.82f, h * 0.72f)
            close()
        }
        drawPath(mountain, Color.White)
    }
}

@Composable
private fun WukongVideoIcon() {
    IconTile(color = Color(0xFF8A5CF6)) {
        val w = size.width
        val h = size.height
        val play = Path().apply {
            moveTo(w * 0.40f, h * 0.32f)
            lineTo(w * 0.40f, h * 0.68f)
            lineTo(w * 0.72f, h * 0.50f)
            close()
        }
        drawPath(play, Color.White)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.32f),
            topLeft = Offset(w * 0.72f, h * 0.36f),
            size = Size(w * 0.16f, h * 0.28f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )
    }
}

@Composable
private fun WukongAudioIcon() {
    IconTile(color = Color(0xFFF35D72)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.07f, cap = StrokeCap.Round)

        drawCircle(
            color = Color.White,
            radius = w * 0.10f,
            center = Offset(w * 0.42f, h * 0.67f)
        )
        drawLine(
            color = Color.White,
            start = Offset(w * 0.52f, h * 0.28f),
            end = Offset(w * 0.52f, h * 0.67f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(w * 0.52f, h * 0.30f),
            end = Offset(w * 0.72f, h * 0.38f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun WukongDocumentIcon() {
    IconTile(color = Color(0xFF58C978)) {
        val w = size.width
        val h = size.height
        val fold = Path().apply {
            moveTo(w * 0.66f, h * 0.18f)
            lineTo(w * 0.82f, h * 0.34f)
            lineTo(w * 0.66f, h * 0.34f)
            close()
        }
        drawPath(fold, Color.White.copy(alpha = 0.52f))
        repeat(3) { index ->
            val y = h * (0.48f + index * 0.12f)
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.25f, y),
                size = Size(w * 0.42f, h * 0.05f),
                cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
            )
        }
    }
}

@Composable
private fun WukongArchiveIcon() {
    IconTile(color = Color(0xFF7C6BE8)) {
        val w = size.width
        val h = size.height
        val colors = listOf(
            Color(0xFFF35D72),
            Color(0xFFF8B83E),
            Color(0xFF58C978)
        )
        colors.forEachIndexed { index, color ->
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.16f, h * (0.22f + index * 0.18f)),
                size = Size(w * 0.68f, h * 0.12f),
                cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
            )
        }
        drawLine(
            color = Color.White.copy(alpha = 0.78f),
            start = Offset(w * 0.54f, h * 0.16f),
            end = Offset(w * 0.54f, h * 0.84f),
            strokeWidth = w * 0.04f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun IconTile(
    color: Color,
    content: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = color,
        shape = RoundedCornerShape(12.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            content()
        }
    }
}
