package com.yunian.ai.feature.profile
import com.yunian.ai.uicommon.icon.AppIcons


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yunian.ai.database.model.ChatGroup
import com.yunian.ai.database.model.ChatMessage
import com.yunian.ai.database.model.CompanionEntity
import com.yunian.ai.uicommon.theme.PinkPrimary
import com.yunian.ai.uicommon.theme.AdaptiveSizing
import com.yunian.ai.uicommon.theme.rememberAdaptiveSizing
import com.yunian.ai.uicommon.theme.TabSelectedBackground
import com.kyant.capsule.ContinuousCapsule
import com.yunian.ai.database.viewmodel.ChatGroupViewModel
import com.yunian.ai.uicommon.component.AppListItemLayout
import com.yunian.ai.uicommon.component.glass.GlassButton
import com.yunian.ai.uicommon.component.glass.LocalPageBackdrop
import com.yunian.ai.uicommon.component.glass.drawGlass
import com.yunian.ai.uicommon.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HomeTab {
    ALL, GROUP, FRIEND
}

@Composable
fun HomeScreen(
    onCompanionClick: (Long) -> Unit,
    onGroupClick: (Long) -> Unit,
    onAddClick: () -> Unit = {},
    onCreateGroupClick: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
    groupViewModel: ChatGroupViewModel = viewModel()
) {
    val chatListState by viewModel.chatListState.collectAsStateWithLifecycle()
    val groups by groupViewModel.groups.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(HomeTab.ALL) }
    val adaptiveSizing = rememberAdaptiveSizing()
    val colorScheme = AppTheme.colors
    val backdrop = LocalPageBackdrop.current

    Box(
        modifier = Modifier
            .fillMaxSize()

            .background(Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(modifier = Modifier.weight(1f))

                    Text(
                        text = "予念",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = colorScheme.onSurface
                    )

                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(
                                onClick = { onCreateGroupClick() },
                                backdrop = backdrop,
                                height = 36.dp,
                                horizontalPadding = 0.dp,
                                modifier = Modifier.size(36.dp),
                                surfaceColor = colorScheme.surfaceVariant.copy(alpha = 0.85f)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Users,
                                    contentDescription = "创建群聊",
                                    modifier = Modifier.size(20.dp),
                                    tint = colorScheme.onSurface
                                )
                            }
                            GlassButton(
                                onClick = { onAddClick() },
                                backdrop = backdrop,
                                height = 36.dp,
                                horizontalPadding = 0.dp,
                                modifier = Modifier.size(36.dp),
                                surfaceColor = colorScheme.surfaceVariant.copy(alpha = 0.85f)
                            ) {
                                Icon(
                                    imageVector = AppIcons.User,
                                    contentDescription = "添加好友",
                                    modifier = Modifier.size(20.dp),
                                    tint = colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colorScheme.outlineVariant.copy(alpha = 0.25f))
                )

                Spacer(modifier = Modifier.height(12.dp))

                HomeTabBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    backdrop = backdrop
                )

                Spacer(modifier = Modifier.height(8.dp))

                val chatCount = when (val state = chatListState) {
                    is HomeViewModel.UiState.Ready -> state.items.size
                    else -> 0
                }
                Text(
                    text = "${chatCount} 个会话 · ${groups.size} 个群聊",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }

            val displayGroups = when (selectedTab) {
                HomeTab.ALL, HomeTab.GROUP -> groups
                HomeTab.FRIEND -> emptyList()
            }
            val displayChats = when (val state = chatListState) {
                is HomeViewModel.UiState.Ready -> {
                    when (selectedTab) {
                        HomeTab.ALL, HomeTab.FRIEND -> state.items
                        HomeTab.GROUP -> emptyList()
                    }
                }
                else -> emptyList()
            }

            when {
                chatListState is HomeViewModel.UiState.Error -> {
                    val message = (chatListState as HomeViewModel.UiState.Error).message
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorHomeState(message = message)
                    }
                }
                chatListState is HomeViewModel.UiState.Loading && groups.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                displayGroups.isEmpty() && displayChats.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyHomeState()
                    }
                }
                else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // 底部导航（FloatingGlassBottomNav）是覆盖在内容之上的浮层，
                    // 其自身已含 navigationBars 内边距；内容必须让出「导航高度 + 手势条」，
                    // 否则联系人多时最后一条会被导航栏遮住（真机问题）。
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 80.dp +
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    itemsIndexed(displayGroups) { _, group ->
                        GroupListItem(
                            group = group,
                            onClick = { onGroupClick(group.id) },
                            adaptiveSizing = adaptiveSizing
                        )
                    }
                    itemsIndexed(displayChats) { _, item ->
                        ChatListItem(
                            companion = item.companion,
                            lastMessage = item.lastMessage,
                            hasUnread = item.hasUnread,
                            onClick = { onCompanionClick(item.companion.id) },
                            adaptiveSizing = adaptiveSizing
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(
    title: String
) {
    val colorScheme = AppTheme.colors

    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 4.dp, top = 8.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = colorScheme.onSurfaceVariant
    )
}

@Composable
fun HomeTabBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    backdrop: com.kyant.backdrop.Backdrop? = LocalPageBackdrop.current
) {
    // 微信风格顶部 tab（替代原液态玻璃悬浮胶囊）：
    // 无容器底色，选中项用 C2BEF0 胶囊高亮 + 近黑文字，未选中用次级文字色。
    val colorScheme = AppTheme.colors
    val isDark = colorScheme.background.luminance() < 0.5f
    val unselectedContent = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF8A829A)
    val selectedContent = Color(0xFF191919)
    val selectedBg = TabSelectedBackground

    val tabs = HomeTab.values().toList()
    val labels = mapOf(
        HomeTab.ALL to "全部",
        HomeTab.GROUP to "群聊",
        HomeTab.FRIEND to "好友"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) selectedBg else Color.Transparent)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = { onTabSelected(tab) }
                    )
                    .semantics { this.selected = selected }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labels[tab] ?: "",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp
                    ),
                    color = if (selected) selectedContent else unselectedContent
                )
            }
        }
    }
}

@Composable
fun GroupListItem(
    group: ChatGroup,
    onClick: () -> Unit,
    adaptiveSizing: AdaptiveSizing
) {
    val colorScheme = AppTheme.colors

    AppListItemLayout(
        isStartAligned = true,
        startSlot = {
            Box(
                modifier = Modifier.size(adaptiveSizing.avatarSize),
                contentAlignment = Alignment.TopEnd
            ) {
                if (group.avatarUrl != null) {
                    AsyncImage(
                        model = group.avatarUrl,
                        contentDescription = group.name,
                        modifier = Modifier
                            .size(adaptiveSizing.avatarSize)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(adaptiveSizing.avatarSize)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        PinkPrimary.copy(alpha = 0.6f),
                                        PinkPrimary.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Users,
                            contentDescription = group.name,
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(adaptiveSizing.iconSize)
                        )
                    }
                }
            }
        },
        endSlot = {},
        modifier = Modifier
            .fillMaxWidth()
            .drawGlass(
                backdrop = LocalPageBackdrop.current,
                shape = ContinuousCapsule,
                surfaceColor = colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        slotGap = AppTheme.dimens.avatarGap
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = adaptiveSizing.fontSizeBody.sp
                ),
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${group.getCompanionIdList().size} 人",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = (adaptiveSizing.fontSizeBody - 1).sp,
                    lineHeight = 20.sp
                ),
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ChatListItem(
    companion: CompanionEntity,
    lastMessage: ChatMessage?,
    hasUnread: Boolean = false,
    onClick: () -> Unit,
    adaptiveSizing: AdaptiveSizing
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val time = lastMessage?.let { dateFormat.format(Date(it.timestamp)) } ?: ""

    val colorScheme = AppTheme.colors

    AppListItemLayout(
        isStartAligned = true,
        startSlot = {
            Box(
                modifier = Modifier.size(adaptiveSizing.avatarSize),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(adaptiveSizing.avatarSize)
                        .clip(CircleShape)
                        .background(colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (companion.avatarUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(companion.avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = companion.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.User,
                            contentDescription = null,
                            tint = AppTheme.colors.captionContent,
                            modifier = Modifier.size((adaptiveSizing.avatarSize * 0.58f))
                        )
                    }
                }
                if (hasUnread) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(PinkPrimary)
                    )
                }
            }
        },
        endSlot = {},
        modifier = Modifier
            .fillMaxWidth()
            .drawGlass(
                backdrop = LocalPageBackdrop.current,
                shape = ContinuousCapsule,
                surfaceColor = colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        slotGap = AppTheme.dimens.avatarGap
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = companion.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = adaptiveSizing.fontSizeBody.sp
                        ),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (time.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = adaptiveSizing.fontSizeSmall.sp
                            ),
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = lastMessage?.content ?: "还没有聊天记录，开始聊天吧",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = (adaptiveSizing.fontSizeBody - 1).sp,
                        lineHeight = 20.sp
                    ),
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
        }
    }
}

@Composable
fun ErrorHomeState(message: String) {
    val colorScheme = AppTheme.colors

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.MessageCircle,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "会话加载失败",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyHomeState() {
    val colorScheme = AppTheme.colors

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.MessageCircle,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "还没有聊天记录",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "去通讯录找你的女友聊天吧",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}
