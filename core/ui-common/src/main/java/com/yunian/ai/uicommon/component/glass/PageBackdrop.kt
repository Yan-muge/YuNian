
package com.yunian.ai.uicommon.component.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.Backdrop

val LocalPageBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun ProvidePageBackdrop(backdrop: Backdrop?, content: @Composable () -> Unit) {
    // 液态玻璃全局开关（GlassEffectGate，默认关闭）：关闭时统一以 null 提供 backdrop，
    // 所有消费方（GlassCard / GlassSurface / GlassButton / LiquidBottomTabs / GlassTopBar /
    // GlassPageScaffold 等）走各自既有的纯色降级分支，无需逐一修改。
    val effectiveBackdrop = if (GlassEffectGate.enabled) backdrop else null
    CompositionLocalProvider(LocalPageBackdrop provides effectiveBackdrop, content = content)
}
