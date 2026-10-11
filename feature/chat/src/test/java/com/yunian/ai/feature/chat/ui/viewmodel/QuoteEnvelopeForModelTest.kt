package com.yunian.ai.feature.chat.ui.viewmodel

import com.yunian.ai.database.model.ChatMessage
import com.yunian.ai.database.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引用回复的「信封」不得进模型提示词。
 *
 * 背景（真机实测）：用户在某个角色身上引用回复 AI 的上一句后，AI 顺着那段
 * `[引用回复]…\n---\n…` 的伪聊天记录继续演，一轮连发 4 条气泡、把用户那一方的
 * 台词也写了出来。信封是给 UI 用的，提示词链路必须只收到用户真正输入的那句话。
 */
class QuoteEnvelopeForModelTest {

    private fun userMessage(raw: String) = ChatMessage(
        companionId = 7L,
        content = raw,
        isFromUser = true,
        type = MessageType.TEXT,
    )

    @Test
    fun `引用回复只把用户输入的那句送给模型`() {
        val quote = QuoteReply(
            messageId = 12L,
            authorName = "方唐婧",
            content = "了。想看就大大方方看，我又不躲。",
        )
        val raw = encodeQuotedMessage(quote, "算了，别穿了，这样就好，我也能光明正大的欣赏你的风光")
        val msg = userMessage(raw)

        assertEquals("算了，别穿了，这样就好，我也能光明正大的欣赏你的风光", msg.contentForModel())
        // 信封、分隔符、被引用原文都不许出现在提示词里
        assertFalse(msg.contentForModel().contains("[引用回复]"))
        assertFalse(msg.contentForModel().contains("---"))
        assertFalse(msg.contentForModel().contains("方唐婧"))
        // 但落库正文本身不变，UI 仍按引用块渲染
        assertTrue(msg.content.startsWith("[引用回复]"))
    }

    @Test
    fun `带媒体行的引用回复同样只留用户输入`() {
        val quote = QuoteReply(
            messageId = 3L,
            authorName = "方唐婧",
            content = "[图片]",
            mediaType = QuoteMediaType.IMAGE,
            mediaPath = "/tmp/a.png",
        )
        val raw = encodeQuotedMessage(quote, "这张好看")
        val msg = userMessage(raw)

        assertEquals("这张好看", msg.contentForModel())
    }

    @Test
    fun `普通消息与非文本消息的文本不变`() {
        val plain = userMessage("在呀")
        val image = ChatMessage(
            companionId = 7L,
            content = "[图片]",
            isFromUser = false,
            type = MessageType.IMAGE,
        )

        assertEquals("在呀", plain.contentForModel())
        assertEquals("[图片]", image.contentForModel())
    }

    @Test
    fun `信封残缺时不吞内容`() {
        val broken = userMessage("[引用回复]12|方唐婧: 想看就大大方方看")
        val empty = userMessage("[引用回复]12|方唐婧: 想看就大大方方看\n---\n")

        // 缺分隔符 ⇒ 不是合法信封，原样保留；分隔符后为空 ⇒ 退回原文，绝不把消息吞成空串
        assertEquals("[引用回复]12|方唐婧: 想看就大大方方看", broken.contentForModel())
        assertEquals("[引用回复]12|方唐婧: 想看就大大方方看\n---\n", empty.contentForModel())
    }
}
