package com.example.simple_pan.ui.space

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.simple_pan.ui.component.WukongEmptyState
import com.example.simple_pan.ui.component.WukongPageBackground
import com.example.simple_pan.ui.component.WukongSegmentedTabs
import com.example.simple_pan.ui.component.WukongTitleTopBar

private const val TOTAL_SPACE_TEXT = "1.01TB"
private const val USED_SPACE_TEXT = "33.30MB"

// [设计] 为什么这样写：空间管理是首页“管理空间”的真实落点，先按参考图补齐容量卡片、获取容量和文件清理三块 UI。
@Composable
fun SpaceManagementScreen(
    onBackClick: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenTotalSpaceDetail: () -> Unit,
    onOpenCloudCollection: () -> Unit
) {
    var isSignInDialogVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WukongPageBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            WukongTitleTopBar(
                title = "空间管理",
                onBackClick = onBackClick,
                onTransferClick = onOpenTransfer,
                onSearchClick = onOpenSearch
            )
            Spacer(modifier = Modifier.height(14.dp))
            SpaceSummaryCard(onOpenTotalSpaceDetail = onOpenTotalSpaceDetail)
            Spacer(modifier = Modifier.height(26.dp))
            SectionTitle(text = "获取更多容量")
            Spacer(modifier = Modifier.height(10.dp))
            SpaceActionCard(
                iconText = "✓",
                iconColor = Color(0xFFFFD95B),
                title = "每日签到领空间",
                subtitle = "今日签到可领1GB，最多可领31GB",
                buttonText = "去签到",
                onClick = {
                    isSignInDialogVisible = true
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SpaceActionCard(
                iconText = "AD",
                iconColor = Color(0xFFB57AF2),
                title = "看视频领空间",
                subtitle = "看一次得200MB，每日可完成0/10次",
                buttonText = "去观看",
                onClick = {}
            )
            Spacer(modifier = Modifier.height(26.dp))
            SectionTitle(text = "文件清理")
            Spacer(modifier = Modifier.height(10.dp))
            SpaceActionCard(
                iconText = "⚡",
                iconColor = Color(0xFF64D08A),
                title = "清理云收藏文件",
                subtitle = "清理预计可释放0K空间",
                buttonText = "去清理",
                onClick = onOpenCloudCollection
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (isSignInDialogVisible) {
        WukongSignInDialog(
            onDismiss = {
                isSignInDialogVisible = false
            }
        )
    }
}

@Composable
private fun SpaceSummaryCard(onOpenTotalSpaceDetail: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "已用空间",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 19.sp
                        ),
                        color = Color(0xFF777777)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = USED_SPACE_TEXT,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 26.sp,
                            lineHeight = 32.sp
                        ),
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    modifier = Modifier
                        .width(1.dp)
                        .height(60.dp),
                    color = Color(0xFFEDEDED)
                ) {}
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 26.dp)
                ) {
                    Surface(
                        color = Color.Transparent,
                        onClick = onOpenTotalSpaceDetail
                    ) {
                        Text(
                            text = "总空间 >",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 19.sp
                            ),
                            color = Color(0xFF777777),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = TOTAL_SPACE_TEXT,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 26.sp,
                            lineHeight = 32.sp
                        ),
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = { 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp)),
                color = Color(0xFF9EA7FF),
                trackColor = Color(0xFFEDEDED)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpaceLegendDot(
                    modifier = Modifier.weight(1.18f),
                    text = "个人文件已用 33.30MB",
                    color = Color(0xFF9EA7FF)
                )
                SpaceLegendDot(
                    modifier = Modifier.weight(1f),
                    text = "云收藏已用 0KB",
                    color = Color(0xFFC7D0FF)
                )
                SpaceLegendDot(
                    modifier = Modifier.weight(0.68f),
                    text = "未用空间",
                    color = Color(0xFFD8D8D8)
                )
            }
        }
    }
}

@Composable
private fun SpaceLegendDot(
    modifier: Modifier = Modifier,
    text: String,
    color: Color
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                lineHeight = 15.sp
            ),
            color = Color.Black,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp
        ),
        color = Color.Black,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SpaceActionCard(
    iconText: String,
    iconColor: Color,
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                color = iconColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = iconText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            lineHeight = 21.sp
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 23.sp
                    ),
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    ),
                    color = Color(0xFF8A8A8A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                color = Color(0xFFF0F0F0),
                shape = RoundedCornerShape(12.dp),
                onClick = onClick
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                    text = buttonText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 19.sp
                    ),
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// [设计] 为什么这样写：总空间明细是空间管理卡片里的二级页面，按截图展示永久容量和新用户容量两张白卡。
@Composable
fun TotalSpaceDetailScreen(onBackClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WukongPageBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            WukongTitleTopBar(
                title = "总空间明细",
                onBackClick = onBackClick,
                onTransferClick = {},
                onSearchClick = {},
                showTransferButton = false,
                showSearchButton = false
            )
            Spacer(modifier = Modifier.height(18.dp))
            TotalSpaceDetailCard(
                title = "永久容量",
                value = "10.00GB",
                subtitle = "永久有效"
            )
            Spacer(modifier = Modifier.height(18.dp))
            TotalSpaceDetailCard(
                title = "新用户容量",
                value = "1.00TB",
                subtitle = "2026/07/07日到期"
            )
        }
    }
}

@Composable
private fun TotalSpaceDetailCard(
    title: String,
    value: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Black
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.Black,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF9A9A9A)
                )
            }
        }
    }
}

// [设计] 为什么这样写：我的订阅和我的分享当前没有真实数据，先做与参考图一致的空状态落点，避免首页入口点进去无页面。
@Composable
fun SimplePanEmptyScreen(
    title: String,
    onBackClick: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTransfer: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WukongPageBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            WukongTitleTopBar(
                title = title,
                onBackClick = onBackClick,
                onTransferClick = onOpenTransfer,
                onSearchClick = onOpenSearch
            )
            WukongEmptyState(
                modifier = Modifier.fillMaxSize(),
                text = "暂无内容"
            )
        }
    }
}

// [设计] 为什么这样写：云收藏文件页面参考截图包含筛选条、空状态和底部清理条，单独页面比复用普通空页更贴近真实产品。
@Composable
fun CloudCollectionScreen(
    onBackClick: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTransfer: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WukongPageBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            WukongTitleTopBar(
                title = "云收藏文件",
                onBackClick = onBackClick,
                onTransferClick = onOpenTransfer,
                onSearchClick = onOpenSearch
            )
            Spacer(modifier = Modifier.height(8.dp))
            WukongSegmentedTabs(
                items = listOf("全部", "传输成功", "传输失败"),
                selectedIndex = 0,
                onSelected = {}
            )
            Box(modifier = Modifier.weight(1f)) {
                WukongEmptyState(
                    modifier = Modifier.fillMaxSize(),
                    text = "暂无内容"
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "当前占用网盘0B，建议清理释放空间",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF555555),
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = Color(0xFFEDEDED),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {}
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        text = "一键清理",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

// [设计] 为什么这样写：签到弹窗是首页和空间管理都能触发的福利入口，抽成独立 Composable 保证两处视觉一致。
@Composable
fun WukongSignInDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "每日签到领空间",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "限时福利",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF8A8A8A),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(30.dp))
                    SignInRewardGrid()
                    Spacer(modifier = Modifier.height(30.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFFFE660),
                        shape = RoundedCornerShape(14.dp),
                        onClick = onDismiss
                    ) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            text = "签到领1GB空间",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(36.dp))
            Surface(
                modifier = Modifier.size(52.dp),
                color = Color.Black.copy(alpha = 0.58f),
                shape = CircleShape,
                onClick = onDismiss
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "×",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInRewardGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SignInRewardItem("1GB", "今天")
            SignInRewardItem("2GB", "第2天")
            SignInRewardItem("3GB", "第3天")
            SignInRewardItem("4GB", "第4天")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SignInRewardItem("10GB", "第7天")
            SignInRewardItem("6GB", "第6天")
            SignInRewardItem("5GB", "第5天")
        }
    }
}

@Composable
private fun SignInRewardItem(
    capacity: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(width = 56.dp, height = 48.dp),
            color = Color(0xFF6EA2FF),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.48f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = capacity,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF777777),
            fontWeight = FontWeight.Bold
        )
    }
}
