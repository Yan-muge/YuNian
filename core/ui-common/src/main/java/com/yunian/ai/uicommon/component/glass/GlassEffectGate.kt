
package com.yunian.ai.uicommon.component.glass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 液态玻璃效果全局开关（**恒为 [enabled] = false**）。
 *
 * ## 现状：用户开关已下线
 *
 * 界面整体改为微信风格实底 surface 后，本开关不再产生任何视觉差异，
 * 因此「主题模式」设置页的入口与 `AppSettingsStore` 的持久化一并移除，
 * 本对象退化为一个**统一的降级开关**：始终关闭，全部玻璃消费方走纯色分支。
 *
 * 保留本对象（而不是删除）是为了不必改动各消费点的判定结构——
 * 将来若重新引入开关，只需恢复一个写入点（`GlassEffectGate.enabled = ...`）即可全局生效。
 *
 * ## 生效机制
 *
 * - [enabled] 用 `mutableStateOf` 承载：所有在组合期读取它的调用方
 *   （[ProvidePageBackdrop]、LiquidGlass.kt 的各 modifier 工厂、聊天页
 *   `LocalChatGlassEnabled` 提供点）自动订阅重组，切换开关即时全局生效。
 * - backdrop 系玻璃（GlassCard / GlassSurface / GlassButton / LiquidBottomTabs /
 *   GlassTopBar / GlassPageScaffold）通过 [ProvidePageBackdrop] 统一门控：
 *   关闭时以 `null` 提供 backdrop，各消费方走既有的纯色降级分支。
 * - draw 型玻璃（LiquidGlass.kt 的 `liquidGlass` / `frostedGlassNav` /
 *   `liquidGlassPill`）在各入口 modifier 内直接读取本开关，关闭时回退近不透明底色。
 *
 * ## 硬件分级
 *
 * `HardwareInfo.tier == LOW` 的强制禁用在各提供点与用户开关做 AND 合成
 * （如聊天页 `LocalChatGlassEnabled`），不在此处耦合——保持本开关纯用户语义。
 *
 * ## 持久化
 *
 * **无**。原 `AppSettingsStore` 的 `liquid_glass_enabled` 键随开关一并退役，
 * 启动时也不再由 Application 回填——即便老用户曾打开过，本开关仍保持 false。
 */
object GlassEffectGate {
    var enabled: Boolean by mutableStateOf(false)
}

/** 液态玻璃关闭时的回退底色（深色）：接近不透明，保证文字可读性，与 GlassCard 容器色一致。 */
val GlassFallbackDarkColor: Color = Color(0xFF20242B)

/** 液态玻璃关闭时的回退底色（浅色）：接近不透明，保证文字可读性，与 GlassCard 容器色一致。 */
val GlassFallbackLightColor: Color = Color(0xF7FFFFFF)
