package com.yunian.ai.feature.chat.ui.viewmodel

import com.yunian.ai.database.model.ChatMessage
import com.yunian.ai.database.model.CompanionEntity
import com.yunian.ai.database.model.MessageType
import com.yunian.ai.domain.AiChatMessage
import com.yunian.ai.domain.AiCompanionInfo
import com.yunian.ai.domain.AiMessageType

internal fun CompanionEntity.toAiCompanionInfo(): AiCompanionInfo = AiCompanionInfo(
    id = id, name = name, personality = personality,
    age = age, backstory = backstory, speakingStyle = speakingStyle,
    systemPrompt = systemPrompt
)

internal fun ChatMessage.toAiChatMessage(): AiChatMessage = AiChatMessage(
    isFromUser = isFromUser, content = contentForModel(), timestamp = timestamp,
    type = when (type) {
        MessageType.IMAGE -> AiMessageType.IMAGE
        else -> AiMessageType.TEXT
    },
    companionId = companionId
)

/**
 * 送给模型的这条历史消息的文本。
 *
 * 图片消息的 content 是系统保留标签 "[图片]"，模型完全看不到画面内容，
 * 于是用户说「再生成一张」时它会另起炉灶（货不对板）。
 * 这里把 searchContent（生图时用的画面描述）作为**系统注记**附给模型看，
 * 但 **content 字段本身不变** —— 渲染层仍按保留标签渲染成图片气泡。
 *
 * 注意：早期版本用的是 `[图片]（画面：xxx）`，这个写法会被模型原样抄进回复，
 * 于是画面描述泄漏成了独立文本气泡（BUG-1 的根因诱导源）。
 * 现在改成"系统注记 + 显式禁止模仿"的措辞，模型不再有可照抄的模板。
 *
 * 另外，**「引用回复」的信封也必须在这里剥掉**（见 [stripQuoteEnvelopeForModel]）。
 */
internal fun ChatMessage.contentForModel(): String {
    if (type == MessageType.IMAGE && searchContent.isNotBlank()) {
        return "$content（系统注记：该图画面描述为 $searchContent；" +
            "此注记仅用于你理解图片内容，禁止在回复中输出任何「画面：」或括号包裹的画面描述）"
    }
    return content.stripQuoteEnvelopeForModel()
}

/**
 * 剥掉「引用回复」的信封，只把**用户真正输入的那句话**交给模型。
 *
 * 用户长按消息选「引用」再回复时，落库正文会被
 * [com.yunian.ai.feature.chat.ui.viewmodel.encodeQuotedMessage] 编码成：
 *
 * ```
 * [引用回复]<messageId>|<作者名>: <被引用文本>[\n[media]类型|路径]
 * ---
 * <用户真正输入的内容>
 * ```
 *
 * 这个信封的用途只有两个：UI 渲染引用块、以及点引用块跳到原消息。
 * 解析它的 [com.yunian.ai.feature.chat.ui.viewmodel.parseQuotedTextContent] **只在
 * TextMessageItem / VoiceMessageItem 里被调用**，提示词链路上从来没有人解它。
 *
 * 后果（真机实测）：模型收到的是一条把「作者名 + 冒号 + 被引用原文」和 `---` 分隔符
 * 混在一起的 **user 消息**。被引用的那条消息本来就在对话历史里，重复一次不但冗余，
 * 更像一段「伪聊天记录 / 待续写的对话脚本」——会诱导模型顺着往下演：自问自答、
 * 连带把**用户那一方的台词**也写出来（实测一轮发出 4 条气泡，最后一条是用户口吻）。
 *
 * 这里只保留 body。信息不丢：被引用的那条消息本身就是历史里的一条记录。
 */
private fun String.stripQuoteEnvelopeForModel(): String {
    val parsed = parseQuotedTextContent(this)
    return parsed.body.ifBlank { this }
}

internal fun List<ChatMessage>.toAiChatMessages(): List<AiChatMessage> =

    filter { it.type != MessageType.REASONING }.map { it.toAiChatMessage() }
