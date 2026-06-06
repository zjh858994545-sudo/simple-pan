package com.example.simple_pan.ui.transfer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.simple_pan.ui.component.WukongPageBackground

// [设计] 为什么这样写：传输设置页目前只展示默认下载目录说明，和参考图一致；真正复制路径功能后续可接 ClipboardManager。
@Composable
fun TransferSettingsScreen(onBackClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WukongPageBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBackClick) {
                    Text(
                        text = "<",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.Black
                    )
                }
                Text(
                    modifier = Modifier.weight(1f),
                    text = "传输设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, Color(0xFFF0F0F0))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "默认下载位置",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                        Text(
                            text = "▣ 复制下载地址",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF3F3F3),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            text = "/Download/WuKong/NetDisk/",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF8C8C8C)
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "因安卓系统隐私安全策略，应用仅能访问指定目录文件，下载路径无法进行自定义更改。\n若您需要将网盘下载完成的文件保存到其它目录，可前往手机系统文件管理应用进行手动操作。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF8F8F8F),
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )
                }
            }
        }
    }
}
