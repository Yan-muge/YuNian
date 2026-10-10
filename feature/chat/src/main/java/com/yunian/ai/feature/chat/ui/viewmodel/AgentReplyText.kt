package com.yunian.ai.feature.chat.ui.viewmodel

import com.yunian.ai.domain.imagegen.ImageGenProtocol
import com.yunian.ai.network.ResponsePostProcessor

/** Native 气泡与视觉回复共用的展示清洗，避免仅含生图指令的有效回复被丢弃。 */
internal object AgentReplyText {
    private const val IMAGE_GEN_ONLY_REPLY_TEXT = "（正在为你配图…）"

    fun forDisplay(raw: String, imageGenEnabled: Boolean): String {
        // 症状 1 修复：Rust Agent 主路径不做 <think> 剥离（旧 Kotlin 路径的 stripThinkingContent
        // 已随 Cordis 取代 AiService 而失效），无独立 reasoning 通道的模型会把思考写进正文，
        // 被 emit_bubble 当气泡发出。此处是所有气泡（bubble 事件 + finalText 兜底 + vision 路径）
        // 必经的单点收口，统一剥离思考块后再走生图清洗。
        val thinkingStripped = ResponsePostProcessor.stripThinkingContent(raw)
        if (!imageGenEnabled) return thinkingStripped
        val stripped = ImageGenProtocol.sanitizeForDisplay(thinkingStripped)
        return when {
            stripped.isNotBlank() -> stripped
            ImageGenProtocol.isPromptOnly(thinkingStripped) -> IMAGE_GEN_ONLY_REPLY_TEXT
            else -> thinkingStripped
        }
    }
}
