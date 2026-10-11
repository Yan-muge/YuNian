package com.yunian.ai.feature.chat.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentReplyTextTest {
    @Test
    fun `image prompt only native bubble remains visible`() {
        val text = AgentReplyText.forDisplay("[[生图: 雨中的白猫]]", imageGenEnabled = true)

        assertEquals("（正在为你配图…）", text)
        assertFalse(text.contains("白猫"))
    }

    @Test
    fun `native bubble keeps dialogue and strips image description`() {
        val text = AgentReplyText.forDisplay("好呀，马上画给你\n[[生图: 雨中的白猫]]", true)

        assertEquals("好呀，马上画给你", text.trim())
        assertFalse(text.contains("白猫"))
    }

    @Test
    fun `empty reply is not converted into a successful image reply`() {
        assertTrue(AgentReplyText.forDisplay("  ", true).isBlank())
    }

    @Test
    fun `disabled image generation and ordinary text retain original behavior`() {
        assertEquals("[[生图: 白猫]]", AgentReplyText.forDisplay("[[生图: 白猫]]", false))
        assertEquals("我在呢", AgentReplyText.forDisplay("我在呢", true))
    }

    @Test
    fun `orchestration status narration is dropped at the chokepoint`() {
        // 真机泄漏原文：模型复述工具结果（已输出气泡 / 表情包无匹配 / 等待用户回复）
        val leak = "已经回复完了，表情包发送失败但气泡已经到位，等用户继续聊。"
        assertTrue(AgentReplyText.forDisplay(leak, imageGenEnabled = true).isBlank())
        assertTrue(AgentReplyText.forDisplay(leak, imageGenEnabled = false).isBlank())
    }

    @Test
    fun `system setting echo is dropped at the chokepoint`() {
        val echo = "伴侣：方唐婧\n设备ID：ly_abcdef\n设备时区：Asia/Shanghai"
        assertTrue(AgentReplyText.forDisplay(echo, imageGenEnabled = true).isBlank())
    }

    @Test
    fun `ordinary dialogue mentioning sticker is not dropped`() {
        // 仅含弱词（表情包）的正常对白必须放行，避免误伤
        assertEquals("给我发个表情包嘛", AgentReplyText.forDisplay("给我发个表情包嘛", true))
        assertEquals("我想你了，早点睡", AgentReplyText.forDisplay("我想你了，早点睡", true))
    }
}
