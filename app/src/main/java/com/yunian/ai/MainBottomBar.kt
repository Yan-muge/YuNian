package com.yunian.ai
import com.yunian.ai.uicommon.icon.AppIcons


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.yunian.ai.R
import com.yunian.ai.uicommon.theme.TabSelectedBackground
import com.kyant.backdrop.Backdrop

data class BottomNavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

/** 底栏顶部分割线高度。 */
private val BottomNavDividerHeight = 1.dp

/** 底栏图标行高度（不含分割线，也不含系统导航栏内边距）。 */
private val BottomNavRowHeight = 60.dp

/**
 * 底栏**自身**高度（分割线 + 图标行），**不含** `navigationBars` 内边距。
 *
 * 主 Tab 页容器（[MainTabScreen]）用它给 Pager 让位：总让位高度 =
 * 本值 + `WindowInsets.navigationBars` 的 bottom。
 *
 * 为什么必须由容器统一让位，而不是各 Tab 页自己写一个魔数：
 * 之前 HomeScreen / ProfileScreen / ContactsScreen 各写各的（80dp、80dp、0dp），
 * 一旦漏写（ProfileScreen 就没加系统导航栏内边距）最后一条就被底栏盖住。
 * 让位是一个**只有底栏知道答案**的问题，所以答案也只应出现在这里。
 */
val BottomNavBarHeight: Dp = BottomNavDividerHeight + BottomNavRowHeight

@Composable
fun FloatingGlassBottomNav(
    items: List<BottomNavItem>,
    currentIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null
) {
    // 微信风格底部导航（替代原液态玻璃悬浮胶囊）：
    // 实底不透明 surface 背景 + 顶部分割线，选中项用 C2BEF0 胶囊高亮。
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val contentColor = if (isDark) Color.White else Color(0xFF191919)
    val unselectedContent = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF8A829A)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE0E0E0)
    val selectedBg = TabSelectedBackground
    // 选中态图标+文字用中饱和紫，与顶部 tab 选中文字同色
    val selectedContent = Color(0xFF8E7CC3)

    var visualSelectedIndex by rememberSaveable { mutableIntStateOf(currentIndex) }
    var pendingNavigationIndex by remember { mutableStateOf<Int?>(null) }
    val currentOnItemClick by rememberUpdatedState(onItemClick)

    LaunchedEffect(currentIndex) { visualSelectedIndex = currentIndex }
    LaunchedEffect(pendingNavigationIndex) {
        val index = pendingNavigationIndex ?: return@LaunchedEffect
        withFrameNanos {}
        currentOnItemClick(index)
        if (pendingNavigationIndex == index) pendingNavigationIndex = null
    }
    val selectTab: (Int) -> Unit = { index ->
        if (index != visualSelectedIndex) {
            visualSelectedIndex = index
            pendingNavigationIndex = index
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 沉浸式（关键顺序）：底色必须先铺满**整块节点**——包含 navigationBars
            // （手势条 / 三键导航）所占的那一段高度——再把内容让出该内边距。
            // 反过来写（先 windowInsetsPadding 后 background）时，background 处于链条
            // 内层、只覆盖被压缩后的内容区，底部会空出系统导航栏的一整条，
            // 表现为「底栏悬浮在内容上、下方露出一条系统色」——即非沉浸。
            .background(colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomNavDividerHeight)
                .background(dividerColor)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomNavRowHeight)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == visualSelectedIndex
                // 纵向布局：图标在上、文字在下。选中态仅加淡紫背景胶囊，图标+文字保持同色染色。
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { this.selected = selected }
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = { selectTab(index) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val tintColor = if (selected) selectedContent else unselectedContent
                    val iconColorFilter = ColorFilter.tint(tintColor)
                    // 无胶囊背景：选中 = 图标+文字统一染紫，未选中 = 灰
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.title,
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer(colorFilter = iconColorFilter)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 11.sp
                        ),
                        color = tintColor
                    )
                }
            }
        }
    }
}

@Composable
fun WeChatTopBar(
    title: String,
    isVisible: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val bgColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = Modifier
            .background(if (isVisible) bgColor else Color.Transparent)
            .windowInsetsPadding(WindowInsets.statusBars)
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isVisible) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    ),
                    color = textColor,
                    textAlign = TextAlign.Start
                )
                Row(
                    modifier = Modifier.padding(end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions
                )
            }
        }
    }
}

@Composable
fun HomeTopBarActions(navController: NavHostController) {
    val iconBgColor = MaterialTheme.colorScheme.surfaceVariant
    val iconTint = MaterialTheme.colorScheme.onBackground

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(iconBgColor)
                .clickable { navController.navigate("contacts") },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Users,
                contentDescription = stringResource(R.string.nav_contacts),
                modifier = Modifier.size(20.dp),
                tint = iconTint
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(iconBgColor)
                .clickable { navController.navigate("profile") },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.UserRound,
                contentDescription = stringResource(R.string.nav_profile),
                modifier = Modifier.size(20.dp),
                tint = iconTint
            )
        }
    }
}
