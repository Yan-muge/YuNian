package com.yunian.ai.feature.chat.data

import com.yunian.ai.common.ChatConstants
import com.yunian.ai.common.SecureLog
import com.yunian.ai.database.model.ChatMessage
import com.yunian.ai.database.model.MessageType
import com.yunian.ai.database.repository.ChatRepository
import com.yunian.ai.database.repository.filterDecrypted

class ChatContextResolver(
    private val chatRepository: ChatRepository
) {

    companion object {

        const val MAX_AI_CONTEXT_FETCH = ChatConstants.MAX_AI_CONTEXT_FETCH

        const val MAX_UI_MESSAGES = ChatConstants.MAX_UI_MESSAGES

        private const val CONTEXT_CACHE_SIZE = ChatConstants.CONTEXT_CACHE_SIZE

        /**
         * 缓存键"不可用"哨兵值。
         *
         * 绝不能复用 `0`：`0` 是一个**可命中的合法键**——旧实现用"取不到就记 0"，
         * 会让两次都取不到的场景互相命中，返回陈旧快照（见 [getHistoryForAi] KDoc）。
         */
        private const val UNCACHEABLE_KEY = -1L

        /** 取"最新一条对话消息 id"作为缓存键时的探针窗口（需大于尾部可能连续出现的过程性消息数）。 */
        private const val NEWEST_PROBE = 8
    }

    private val contextCache = object : LinkedHashMap<Long, Pair<Long, List<ChatMessage>>>(
        CONTEXT_CACHE_SIZE, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Pair<Long, List<ChatMessage>>>?): Boolean {
            return size > CONTEXT_CACHE_SIZE
        }
    }

    private val cacheLock = Any()

    /**
     * 取进入 AI 上下文的历史消息。
     *
     * ## 缓存键与快照一致性（2026-10-11「上下文丢失」加固）
     *
     * 旧实现的缓存键是 `getRecentMessagesSync(companionId, 1)` 的**唯一一条**消息 id，
     * 取不到时退化成 `0`。两个缺陷：
     * - 该查询先按时间倒序取最新 1 条，再由仓储层剔除 `TOOL_ACTIVITY`；若最新一条恰是
     *   被剔除的过程性消息，过滤后为空 → 键退化为 `0`；
     * - `0` 是**可命中的合法键**：一旦上一次拉取也退化成 `0`，本次会直接返回**陈旧快照**，
     *   表现为"最新用户消息没进上下文"，AI 只能对着旧上下文自说自话。
     *
     * 现改为：
     * 1. 用**与历史拉取同一过滤口径**的小窗口探针取"最新一条对话消息 id"作为命中判据；
     *    取不到用 [UNCACHEABLE_KEY] 表示**不可缓存**，`0` 不再充当"取不到"，杜绝两次
     *    "都取不到"互相命中；
     * 2. 写入缓存的键**恒等于该快照自身的尾部 id**（自洽）——只要探针读到更新的消息，键
     *    必然不同，因此**结构上不可能**再把陈旧快照当成命中返回；探针与尾部不一致时仅记录
     *    可观测日志（说明本次读取跨越了一次写入）。
     */
    suspend fun getHistoryForAi(companionId: Long): List<ChatMessage> {
        val limit = MAX_AI_CONTEXT_FETCH
        val probeKey = newestDialogueMessageId(companionId)

        if (probeKey != UNCACHEABLE_KEY) {
            synchronized(cacheLock) {
                val cached = contextCache[companionId]
                if (cached != null && cached.first == probeKey) {
                    SecureLog.d("ChatContextResolver", "AI context cache hit for companion=$companionId, key=$probeKey")
                    return cached.second
                }
            }
        }

        SecureLog.d("ChatContextResolver", "Fetching AI context for companion=$companionId, limit=$limit, key=$probeKey")
        val history = fetchDialogueHistory(companionId, limit)
        // 键恒等于本次快照的**尾部 id**（自洽）：下次探针若读到更新的消息，键必然不同 → 不会命中陈旧快照。
        val key = history.lastOrNull()?.id ?: UNCACHEABLE_KEY
        if (probeKey != UNCACHEABLE_KEY && key != probeKey) {
            // 读到的最新一条在拉取期间又变了：仅作可观测记录（此时 history 比探针更新，仍可用）。
            SecureLog.w(
                "ChatContextResolver",
                "AI context moved during fetch (probe=$probeKey, tail=$key) for companion=$companionId"
            )
        }
        if (key != UNCACHEABLE_KEY) {
            synchronized(cacheLock) {
                contextCache[companionId] = key to history
            }
        }
        return history
    }

    /**
     * 最新一条「对话消息」id，口径与 [fetchDialogueHistory] 完全一致（解密 + 剔除过程性消息）。
     *
     * 空会话、或探针窗口内全为过程性消息时返回 [UNCACHEABLE_KEY]。
     */
    private suspend fun newestDialogueMessageId(companionId: Long): Long =
        chatRepository.getRecentMessagesSync(companionId, NEWEST_PROBE)
            .filterDecrypted()
            .excludeNonDialogue()
            .lastOrNull()?.id
            ?: UNCACHEABLE_KEY

    /** 拉取对话历史，口径 = 解密过滤（[filterDecrypted]）+ 剔除过程性消息（[excludeNonDialogue]）。 */
    private suspend fun fetchDialogueHistory(companionId: Long, limit: Int): List<ChatMessage> =
        chatRepository.getRecentMessagesSync(companionId, limit)
            .filterDecrypted()
            .excludeNonDialogue()

    suspend fun getShortHistoryForAi(companionId: Long, shortLimit: Int): List<ChatMessage> {
        val effectiveLimit = shortLimit.coerceAtMost(MAX_AI_CONTEXT_FETCH)
        return chatRepository.getRecentMessagesSync(companionId, effectiveLimit)
            .filterDecrypted()
            .excludeNonDialogue()
    }

    /** 剔除过程性消息：推理过程（REASONING）与工具调用卡片（TOOL_ACTIVITY）都不进入 AI 上下文。 */
    private fun List<ChatMessage>.excludeNonDialogue(): List<ChatMessage> =
        filter { it.type != MessageType.REASONING && it.type != MessageType.TOOL_ACTIVITY }

    fun clearCache(companionId: Long) {
        synchronized(cacheLock) {
            contextCache.remove(companionId)
        }
    }

    fun clearAllCache() {
        synchronized(cacheLock) {
            contextCache.clear()
        }
    }

    fun capUiMessages(allMessages: List<ChatMessage>): Pair<List<ChatMessage>, Boolean> {
        if (allMessages.size <= MAX_UI_MESSAGES) return allMessages to false
        val dropped = allMessages.size - MAX_UI_MESSAGES
        SecureLog.w("ChatContextResolver", "UI messages capped: dropped $dropped oldest messages")
        return allMessages.takeLast(MAX_UI_MESSAGES) to true
    }
}
