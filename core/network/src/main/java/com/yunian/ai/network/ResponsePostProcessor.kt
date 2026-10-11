package com.yunian.ai.network

object ResponsePostProcessor {

    private val LABEL_ONLY = Regex(
        "思考|思维|推理|分析|Thinking|Reasoning|Analysis|Thought|thought|thinking|LM_THINK|redacted_reasoning",
        RegexOption.IGNORE_CASE,
    )

    private val CLOSED_THINK_BLOCK_PATTERNS = listOf(

        Regex("""(?is)<think[^>]*>([\s\S]*?)</think\s*>"""),
        Regex("""(?is)<thinking[^>]*>([\s\S]*?)</thinking\s*>"""),
        Regex("""(?is)<thought[^>]*>([\s\S]*?)</thought\s*>"""),
        Regex("""(?is)<reflection[^>]*>([\s\S]*?)</reflection\s*>"""),
        Regex("""(?is)<reasoning[^>]*>([\s\S]*?)</reasoning\s*>"""),
        Regex("""(?is)<LM_THINK[^>]*>([\s\S]*?)</LM_THINK\s*>"""),
        Regex("""(?is)<redacted_reasoning[^>]*>([\s\S]*?)</redacted_reasoning\s*>"""),

        Regex("""(?is)```(?:thinking|thought|reasoning|analysis|think)\s*([\s\S]*?)```"""),

        Regex("""(?im)^#{1,3}\s*(思考|思维|推理|分析|Thinking|Reasoning|Analysis|Thought)\s*\n([\s\S]*?)(?=\n#{1,3}\s|$)"""),

        Regex("""(?is)【(思考|思维|推理|分析)】([\s\S]*?)【/\1】"""),

        Regex("""(?is)\[(思考|思维|推理|分析|thought|thinking)]\s*([\s\S]*?)\[/\1]"""),
    )

    private val UNCLOSED_THINK_OPEN_PATTERNS = listOf(
        Regex("""(?is)<think[^>]*>[\s\S]*$"""),
        Regex("""(?is)<thinking[^>]*>[\s\S]*$"""),
        Regex("""(?is)<thought[^>]*>[\s\S]*$"""),
        Regex("""(?is)<reflection[^>]*>[\s\S]*$"""),
        Regex("""(?is)<reasoning[^>]*>[\s\S]*$"""),
        Regex("""(?is)<LM_THINK[^>]*>[\s\S]*$"""),
        Regex("""(?is)<redacted_reasoning[^>]*>[\s\S]*$"""),
        Regex("""(?is)```(?:thinking|thought|reasoning|analysis|think)\s*[\s\S]*$"""),
    )

    private val PLAINTEXT_THINK_PREFIX = Regex(
        """(?im)^(?:思考过程|思维链|推理过程|内心独白|角色思考|分析过程|我的思考|最终回复|Final\s*Answer|Answer|Reasoning|Thought\s*Process)\s*[:：]\s*""",
    )

    private val PLAINTEXT_LEADING_COT = Regex(
        """(?is)^\s*(?:好的[，,]?\s*)?(?:我(?:现在|先)?(?:需要|得|要|应该)|让我(?:先|来)?|首先|接下来)\s*(?:分析|理解|判断|思考|推理)(?:一下|下)?(?:用户|对方)?(?:的)?(?:意图|意思|问题|消息|说的)[：:]?\s*""",
    )

    private val COT_SENTENCE_MARKERS = listOf(
        "思考过程", "思维链", "推理过程", "内心独白", "用户意图", "用户想表达",
        "我应该回复", "我需要回复", "我得回复", "作为AI", "作为助手",
        "不能让任何人知道", "系统提示", "根据人设", "按照人设",
        "先分析", "先理解用户", "最终回复应该", "输出格式",
    )

    // ── 元叙述 / 场景状态评估标记 ──────────────────────────────────────
    // 第一人称思考插件（chat.first_person_reasoning）让模型用「意识流」思考，但对无独立
    // reasoning 通道的模型，思考会写进正文。除了带前缀的 CoT，还有一种更难缠的形态：
    // 模型把「对当前场景/角色状态的旁观式评估」直接当正文输出，例如
    //   「好的，现在等他继续聊，目前氛围是周末夜晚的温馨时刻，越越状态稳定」
    // 这类文本是**整段元叙述**（描述自己在等待/评估氛围/评估状态），不是对用户说话，
    // 正常对话中几乎不会出现，故命中即判为思考泄漏（整体剥离，而非只切前缀）。
    private val META_NARRATION_MARKERS = listOf(
        "等他继续聊", "等他回复", "等他回话", "等他消息", "等待用户", "等待他",
        "目前氛围", "当前氛围", "此刻氛围", "氛围是", "氛围属于",
        "状态稳定", "情绪稳定", "状态不错", "状态良好", "情绪不错", "心情稳定",
        "继续观察", "保持观察", "等待后续", "静待", "先不打扰",
    )

    // ── 行动计划旁白标记 ──────────────────────────────────────────────
    // 另一种思考泄漏：模型把「接下来要做什么/不做什么」的行动计划直接当正文输出，
    // 例如「问完这句就等他了，不再发了」「我先不回了，等他接话」「这句话说完就停下」。
    // 这类文本主语是「我」、谓语是「计划动作」，是跟自己交代，不是对用户说话。
    // 与 META_NARRATION_MARKERS 的区别：前者评估「现状/氛围」，后者交代「下一步动作」。
    private val ACTION_PLAN_MARKERS = listOf(
        "不再发", "不再说", "不再问了", "先不回", "先不说", "不回了", "不说了",
        "就等他了", "就等对方", "就等着", "等他就行", "等他接话", "等他开口",
        "问完这句", "说完这句", "这句说完", "这句问完", "问完就", "说完就",
        "先停一下", "先停在这", "停在这", "打住", "就此打住", "就到这", "先到这",
        "我先观察", "我先看看他", "先看看他", "看他的反应", "看他怎么回",
    )

    // ── 对白特征兜底检测（不依赖枚举词，抓句式结构）──────────────────
    // 正常对白几乎必然含至少一个「互动信号」（情感/亲昵/应和），旁白则没有。
    private val INTERACTION_WORDS = listOf(
        "想你", "抱抱", "亲亲", "乖", "晚安", "早安", "哈哈", "嘿嘿", "嗯嗯",
        "喜欢", "爱你", "想你", "宝贝", "睡吧", "吃饭", "喝水", "累不累", "辛苦",
    )
    // 计划/汇报语气骨架：旁白（思考泄漏）常以这些词开头或包含它们，且无互动信号。
    // 刻意收窄——只用「明确指向自我计划/汇报」的词，不含「好了/这就/先」这类日常语气词，
    // 避免误伤「好了，不说了，睡觉」「等着，我这就来」等正常对白。
    private val PLAN_TONE_PREFIXES = listOf(
        "我已经回", "我先回", "我先不回", "已经回", "不再发", "不再说", "先不回",
        "先不说", "等着他", "就等他", "接下来", "下一步",
    )
    private val PLAN_TONE_WORDS = listOf(
        "就等他了", "就等着", "等着他", "等他接话", "等他回", "等对方", "不再发",
        "不再说", "先不回", "先不说", "问完就", "说完就", "已经回", "回了上面",
        "汇报", "计划", "打算", "下一步", "接下来",
    )

    fun extractThinkingContent(content: String): Pair<String, String?> {
        if (content.isBlank()) return content to null
        val extracted = mutableListOf<String>()
        var result = content

        for (pattern in CLOSED_THINK_BLOCK_PATTERNS) {
            result = pattern.replace(result) { match ->
                val body = pickThinkBody(match.groupValues)
                if (body.isNotBlank()) extracted += body
                ""
            }
        }

        for (pattern in UNCLOSED_THINK_OPEN_PATTERNS) {
            val match = pattern.find(result) ?: continue
            val openEnd = match.value.indexOf('>').takeIf { it >= 0 }?.plus(1)
                ?: match.value.indexOf('\n').takeIf { it >= 0 }?.plus(1)
                ?: 0
            val body = match.value.substring(openEnd.coerceAtMost(match.value.length)).trim()
            if (body.isNotBlank()) extracted += body
            result = result.removeRange(match.range)
        }

        result = result
            .replace(Regex("""(?is)</(?:think|thinking|thought|reflection|reasoning|LM_THINK|redacted_reasoning)\s*>"""), "")

        val beforePlain = result
        result = stripPlaintextThinking(result)
        if (result != beforePlain) {
            val removed = beforePlain.removePrefix(result).trim().ifBlank {
                beforePlain.replace(result, "").trim()
            }
            if (removed.isNotBlank() && removed.length >= 4) {
                extracted += removed
            }
        }

        val reasoning = extracted.joinToString("\n\n").trim().ifBlank { null }
        return result.trim() to reasoning
    }

    fun stripThinkingContent(content: String): String =
        stripIdleAckTail(extractThinkingContent(content).first)

    /**
     * 剥离「孤立语气词尾巴」：模型在正经说完一句对白后，有时会再补一个无内容的附和尾音
     * （如「嗯嗯」「嗯」「好呀」「知道了」）单独成句，造成多一条空泡。
     * 这里把以换行/句号分隔后、末尾那一段若只是纯语气词，直接去掉。
     * 刻意收窄：仅当尾段是「纯语气词 + 可选标点」，且不含任何实词时才剥离，避免误伤正常对白。
     */
    fun stripIdleAckTail(content: String): String {
        if (content.isBlank()) return content
        val parts = content
            .split(Regex("""\n+|(?<=[。！？!?～…])"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.size <= 1) return content.trim()
        val tail = parts.last()
        if (isPureIdleAck(tail)) {
            val kept = parts.dropLast(1).joinToString("").trim()
            return kept.ifBlank { content.trim() }
        }
        return content.trim()
    }

    private val IDLE_ACK_TOKENS = setOf(
        "嗯嗯", "嗯", "嗯呐", "好", "好的", "好呀", "好吧", "好哦", "行", "行吧", "知道了",
        "知道啦", "哦", "喔", "嗯呢", "好嘞", "是", "是的", "对", "对的", "没错",
    )

    private fun isPureIdleAck(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        // 去掉首尾的标点/波浪号后，剩余部分必须命中语气词表
        val core = t.trim('，', ',', '。', '.', '！', '!', '？', '?', '～', '~', ' ')
        if (core.isEmpty()) return false
        // 若包含「你/我/宝宝/想/爱/抱/睡」等实词，说明是对白，不是空语气词
        val hasContentWord = Regex("""[你您我他她宝宝亲爱老公老婆想爱抱睡来去在有没有]""").containsMatchIn(core)
        if (hasContentWord) return false
        return IDLE_ACK_TOKENS.contains(core)
    }

    fun looksLikeThinkingLeak(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.contains(Regex("""(?is)</?(?:think|thinking|thought|reflection|reasoning|LM_THINK|redacted_reasoning)\b"""))) {
            return true
        }
        if (PLAINTEXT_THINK_PREFIX.containsMatchIn(t)) return true
        val hit = COT_SENTENCE_MARKERS.count { t.contains(it) }
        if (hit >= 2) return true
        if (hit >= 1 && t.length > 80) return true
        return false
    }

    // ── 编排域（内部机制）词汇 · 结构判据 ──────────────────────────────
    // 「编排状态自述」泄漏（如「已经回复完了，表情包发送失败但气泡已经到位，等用户继续聊」）
    // 的根治靠**治源**（工具结果/系统提示不再写成命令式台词，见 agent.rs）。此处是
    // **唯一出海口**（AgentReplyText.forDisplay，覆盖全部气泡事件 + finalText 兜底）的结构兜底。
    //
    // 与 META_NARRATION_MARKERS 的本质区别：后者是「开放集短语枚举」——每换一种说法就得补一条；
    // 这里是**编排域词汇**：引擎自身使用的封闭词集（气泡/回合/工具/发送状态…），
    // 真人恋人对话里几乎不可能出现，因此可穷举、可控，属**领域划分**而非打地鼠。
    private val ORCHESTRATION_STRONG_MARKERS = listOf(
        "气泡", "回合", "工具调用", "调用工具", "工具结果", "系统提示", "系统控制", "系统设定",
        "人设", "设备ID", "设备时区", "会话ID", "发送失败", "已输出", "回复完了", "等待用户",
        "本轮", "上限", "表情包发送",
    )
    private val ORCHESTRATION_WEAK_MARKERS = listOf("表情包", "发送", "回复")

    /**
     * 判断气泡文本是否为「编排域内部文本」——系统/工具层内容被模型复述，而非角色对用户说的话。
     *
     * 命中规则（刻意收窄，避免误伤正常对白）：
     * 1. 命中 ≥2 个编排域**强词** → 直接判为内部文本（真人对白几乎不可能同时出现两个）；
     * 2. 命中 1 个强词 + 任意弱词，且**无对用户互动信号**（情感/亲昵词）→ 判为内部文本。
     *
     * 例：`已经回复完了，表情包发送失败但气泡已经到位，等用户继续聊。`
     * → 命中 `回复完了` / `发送失败` / `气泡` 三个强词 → 判为内部文本。
     * 反例：`给我发个表情包嘛`（仅弱词，无强词）→ 放行。
     */
    fun looksLikeOrchestrationLeak(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        val strongHits = ORCHESTRATION_STRONG_MARKERS.count { t.contains(it) }
        if (strongHits >= 2) return true
        if (strongHits >= 1) {
            val weakHit = ORCHESTRATION_WEAK_MARKERS.any { t.contains(it) }
            if (weakHit && INTERACTION_WORDS.none { t.contains(it) }) return true
        }
        return false
    }

    fun trimIdleEmotionOverDelivery(response: String, lastUserMessage: String): String {
        val raw = response.trim()
        if (raw.isEmpty()) return raw
        if (!AiContextTools.isIdleEmotionVent(lastUserMessage)) return raw

        val blocks = raw
            .split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val parts = if (blocks.size >= 2) {
            blocks.flatMap { block ->
                block.split(Regex("""(?<=[。！？!?～…])"""))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .ifEmpty { listOf(block) }
            }
        } else {
            raw.split(Regex("""(?<=[。！？!?～…\n])"""))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
        if (parts.size <= 1) {

            return enforceSingleActionFocus(trimSinglePackedSentence(raw))
        }

        val kept = mutableListOf<String>()
        for (part in parts) {
            if (isCarePackageClause(part) || isSecondaryActionClause(part) || hasAdviceDispatch(part)) {

                if (kept.isNotEmpty()) break
                continue
            }
            if (looksLikePreachOrLecture(part)) {
                if (kept.isNotEmpty()) break
                continue
            }
            if (kept.isEmpty()) {
                kept += part
                continue
            }

            if (kept.size >= IDLE_MAX_SPOKEN_SEGMENTS) break
            if (canKeepAsSameIntentBubble(kept, part)) {
                kept += part
            } else {
                break
            }
        }

        val result = kept.joinToString("\n\n").trim()
        if (result.isNotBlank()) {

            val tightenedParts = kept.map { enforceSingleActionFocus(trimSinglePackedSentence(it)) }
                .map { it.trim() }
                .filter { it.isNotBlank() && !isCarePackageClause(it) }
            val tightened = tightenedParts.joinToString("\n\n").trim()
            return tightened
        }

        val fallback = parts
            .asSequence()
            .map { enforceSingleActionFocus(trimSinglePackedSentence(it)) }
            .firstOrNull { it.isNotBlank() && !isCarePackageClause(it) }
            .orEmpty()
        return fallback
    }

    private const val IDLE_MAX_SPOKEN_SEGMENTS = 3

    private fun looksLikePreachOrLecture(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        val markers = listOf(
            "固然重要", "革命的本钱", "身体是", "要注意", "你应该", "你必须",
            "说到底", "归根结底", "总而言之", "综上所述", "我总结", "其实你要",
        )
        return markers.any { t.contains(it) }
    }

    private fun canKeepAsSameIntentBubble(kept: List<String>, next: String): Boolean {
        val n = next.trim()
        if (n.isEmpty()) return false
        if (isCarePackageClause(n) || isSecondaryActionClause(n) || hasAdviceDispatch(n)) return false
        if (looksLikePreachOrLecture(n)) return false
        val isQuestion = n.contains('？') || n.contains('?') ||
            n.endsWith("吗") || n.endsWith("吧") || n.endsWith("呢")
        val coreLen = n.trimEnd('。', '！', '？', '!', '?', '～', '…', '.', ' ').length
        if (coreLen <= 12) return true
        if (isQuestion && coreLen <= 36) return true

        val firstCore = kept.first().trimEnd('。', '！', '？', '!', '?', '～', '…', '.', ' ')
        if (firstCore.length <= 8 && coreLen <= 40) return true
        return false
    }

    private fun enforceSingleActionFocus(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return t
        if (containsCarePackageMarker(t) || CARE_DISPATCH_PREFIX.containsMatchIn(t) || isSecondaryActionClause(t)) {
            return trimSinglePackedSentence(t)
        }
        if (!hasExplicitMultiActionPack(t)) return t

        val sentences = t
            .split(Regex("""(?<=[。！？!?～…])"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (sentences.size <= 1) {

            return trimAtSecondAction(t)
        }
        val first = sentences.firstOrNull { s ->
            !isCarePackageClause(s) && !isSecondaryActionClause(s)
        }.orEmpty()
        return first.ifBlank { "" }
    }

    private fun hasExplicitMultiActionPack(text: String): Boolean {
        val packHints = listOf(
            "建议你", "我建议", "可以试试", "不如先", "推荐你",
            "方案", "第一步", "第二步", "首先", "其次", "最后记得",
            "要不要泡", "要不要听", "要不要睡", "要不要喝", "要不要躺",
        )
        val hit = packHints.count { text.contains(it) }
        if (hit >= 1 && (containsCarePackageMarker(text) || text.length > 48)) return true
        if (hit >= 2) return true

        val sentences = text.split(Regex("""(?<=[。！？!?～…])""")).map { it.trim() }.filter { it.isNotBlank() }
        if (sentences.size >= 2 && sentences.drop(1).any { isCarePackageClause(it) || isSecondaryActionClause(it) || hasAdviceDispatch(it) }) {
            return true
        }
        return false
    }

    private fun hasAdviceDispatch(text: String): Boolean {
        val t = text.trim()
        return t.contains("建议") || t.contains("可以试试") || t.contains("不如") ||
            t.contains("要不要") || t.startsWith("记得") || t.contains("别忘了")
    }

    private fun trimAtSecondAction(text: String): String {
        val markers = listOf(
            "建议你", "我建议", "可以试试", "不如先", "推荐你",
            "第一步", "第二步", "首先", "其次",
            "要不要泡", "要不要听", "要不要睡", "要不要喝", "要不要躺",
            "另外", "顺便", "还有啊",
        )
        var cut = Int.MAX_VALUE
        for (m in markers) {
            val i = text.indexOf(m)
            if (i >= 8 && i < cut) cut = i
        }
        val careCut = firstCareMarkerIndex(text)
        if (careCut >= 8 && careCut < cut) cut = careCut
        if (cut == Int.MAX_VALUE) return text
        val head = text.take(cut).trimEnd('，', ',', '；', ';', '、', ' ', '。', '.', '！', '!', '？', '?').trim()
        if (head.length < 4) return ""
        return ensureTerminalPunctuation(head)
    }

    private fun trimSinglePackedSentence(text: String): String {
        if (!containsCarePackageMarker(text) &&
            !CARE_DISPATCH_PREFIX.containsMatchIn(text) &&
            !isSecondaryActionClause(text)
        ) {
            return text
        }
        val clauses = text
            .split(Regex("""[，,；;、]"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (clauses.size <= 1) {

            val cut = firstCareMarkerIndex(text)
            return if (cut > 8) {
                ensureTerminalPunctuation(
                    text.take(cut).trimEnd('，', ',', '；', ';', '、', ' ').trim(),
                )
            } else {
                ""
            }
        }

        val kept = mutableListOf<String>()
        for (c in clauses) {
            if (isCarePackageClause(c) || isSecondaryActionClause(c)) break
            kept += c
        }
        if (kept.isEmpty()) {
            val cut = firstCareMarkerIndex(text)
            return if (cut > 8) {
                ensureTerminalPunctuation(
                    text.take(cut).trimEnd('，', ',', '；', ';', '、', ' ').trim(),
                )
            } else {
                ""
            }
        }
        return ensureTerminalPunctuation(kept.joinToString("，"))
    }

    private fun ensureTerminalPunctuation(text: String): String {
        val joined = text.trim()
        if (joined.isEmpty()) return joined
        val needsEnd = !joined.endsWith("。") && !joined.endsWith("！") &&
            !joined.endsWith("？") && !joined.endsWith("～") && !joined.endsWith("…") &&
            !joined.endsWith("?") && !joined.endsWith("!")
        return if (needsEnd) "$joined。" else joined
    }

    private fun containsCarePackageMarker(text: String): Boolean =
        CARE_PACKAGE_MARKERS.any { text.contains(it) }

    private fun firstCareMarkerIndex(text: String): Int {
        var min = Int.MAX_VALUE
        for (m in CARE_PACKAGE_MARKERS) {
            val i = text.indexOf(m)
            if (i >= 0 && i < min) min = i
        }
        val dispatch = CARE_DISPATCH_PREFIX.find(text)?.range?.first
        if (dispatch != null && dispatch < min) min = dispatch
        return if (min == Int.MAX_VALUE) -1 else min
    }

    private fun isCarePackageClause(text: String): Boolean {
        if (CARE_PACKAGE_MARKERS.any { text.contains(it) }) return true

        if (CARE_DISPATCH_PREFIX.containsMatchIn(text)) return true
        return false
    }

    private fun isSecondaryActionClause(text: String): Boolean {
        if (isCarePackageClause(text)) return true
        return SECONDARY_ACTION_MARKERS.any { text.contains(it) }
    }

    private val CARE_PACKAGE_MARKERS = listOf(

        "泡脚", "听歌", "早睡", "揉肩", "太阳穴", "洗头", "洗澡", "躺沙发", "去沙发",
        "喝热水", "喝杯水", "热牛奶", "早点睡", "别熬夜", "不许熬夜", "当夜猫", "夜猫",
        "捏捏", "按摩", "敷眼", "休息五分钟", "躺五分钟", "眯五分钟", "人一会儿", "靠着睡",
        "先去躺", "先去睡", "先去洗", "给你揉", "给你捏", "给你按",
        "要不要泡", "要不要听", "要不要睡", "要不要喝", "要不要躺",

        "肩膀", "靠我肩", "靠着我", "眯一会儿", "眯一会", "闭一会儿", "闭一会",
        "补回来", "补觉", "陪着你", "搂着你", "抱着你睡", "过来抱抱", "过来让我",
        "小夜灯", "开灯陪", "沙发上", "躺一会儿", "躺一会", "躺一下", "去躺",
        "揉揉", "按按", "捏一捏", "拍拍背", "盖好被子", "喝口水", "休息一下",
        "不许说不",
    )

    private val CARE_DISPATCH_PREFIX = Regex(
        """(?:现在)?先(?:去|把|来|让|给)|过来(?:让我|给我|抱抱|靠)?|不许再|今晚不许|要是敢|把脑袋靠|靠过来|闭上眼|先眯|先躺|先睡|先休息""",
    )

    private val SECONDARY_ACTION_MARKERS = listOf(
        "另外", "还有啊", "对了", "顺便", "顺便说", "再说一件", "还有一件",
        "你还记得", "我们改天", "明天记得", "别忘了",
        "不过看你", "不过你", "话说回来",
    )

    fun ensureNotHtml(body: String, response: okhttp3.Response) {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("<!") || trimmed.startsWith("<html", ignoreCase = true)) {
            val hint = when {
                response.code == 401 || response.code == 403 ->
                    " (请检查API密钥/APIPassword是否正确)"
                response.code == 404 ->
                    " (请检查API地址和模型名是否正确)"
                else -> " (HTTP ${response.code}，请检查API配置)"
            }
            throw Exception("服务器返回了网页而非API响应$hint")
        }
    }

    private fun pickThinkBody(groupValues: List<String>): String {
        val body = groupValues
            .drop(1)
            .lastOrNull { it.isNotBlank() && !LABEL_ONLY.matches(it.trim()) }
            ?: groupValues.getOrNull(1)
            ?: ""
        return body.trim()
    }

    private fun stripPlaintextThinking(text: String): String {
        var result = text.trim()
        if (result.isEmpty()) return result

        // 元叙述整段剥离（优先于前缀剥离）：命中「等待/氛围/状态评估」或「行动计划旁白」
        // 等旁观式描述时，整段视为思考泄漏直接置空。命中条件刻意收窄——要求命中 ≥1 个
        // 强评估/计划标记，且文本不含对用户的直接呼语/提问，避免误伤
        // 「宝宝你状态怎么样呀」「你不再说话我就睡了哦」这类正常关心/对话。
        if (looksLikeMetaNarration(result)) {
            return ""
        }

        repeat(3) {
            val next = PLAINTEXT_THINK_PREFIX.replace(result, "").trim()
            if (next == result) return@repeat
            result = next
        }

        if (PLAINTEXT_LEADING_COT.containsMatchIn(result) || COT_SENTENCE_MARKERS.any { result.contains(it) }) {
            val paragraphs = result.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotBlank() }
            if (paragraphs.size >= 2) {
                val last = paragraphs.last()
                val head = paragraphs.dropLast(1).joinToString("\n\n")
                val headLooksCot = COT_SENTENCE_MARKERS.any { head.contains(it) } ||
                    PLAINTEXT_LEADING_COT.containsMatchIn(head) ||
                    PLAINTEXT_THINK_PREFIX.containsMatchIn(head)
                if (headLooksCot && last.length in 2..120 && !looksLikeThinkingLeak(last)) {
                    return last
                }
            }

            val strippedLead = PLAINTEXT_LEADING_COT.replace(result, "").trim()
            if (strippedLead.length in 2 until result.length && !looksLikeThinkingLeak(strippedLead)) {
                val markerHits = COT_SENTENCE_MARKERS.count { strippedLead.contains(it) }
                if (markerHits == 0) {
                    result = strippedLead
                } else if (markerHits >= 2 && strippedLead.length > 60) {
                    result = ""
                }
            } else if (looksLikeThinkingLeak(result) && paragraphs.size < 2) {

                result = ""
            }
        }

        return result.trim()
    }

    /**
     * 判断文本是否为「元叙述 / 场景状态评估」——整段旁观式描述，而非对用户的对话。
     *
     * 命中条件（刻意收窄，防误伤）：
     * 1. 命中 ≥1 个强状态评估标记（「等他继续聊」「目前氛围」「状态稳定」等）；
     * 2. 且不含对用户的直接呼语（你/宝宝/亲爱的…）或提问语气——正常对话里 AI 说
     *    「宝宝你状态怎么样呀」也含「状态」，但那是关心不是旁白，必须放行。
     */
    private fun looksLikeMetaNarration(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        // 含第二人称呼语或疑问 → 是对用户说话，不是旁白，放行
        val directAddress = t.contains("你") || t.contains("您") ||
            t.contains("宝宝") || t.contains("亲爱的") || t.contains("老公") || t.contains("老婆")
        val asksQuestion = t.contains('？') || t.contains('?') ||
            t.endsWith("吗") || t.endsWith("呢") || t.endsWith("呀") || t.endsWith("吧")

        // ① 场景状态评估旁白（「等他继续聊」「目前氛围」「状态稳定」）
        val metaHit = META_NARRATION_MARKERS.count { t.contains(it) }
        if (metaHit > 0) return !directAddress && !asksQuestion

        // ② 行动计划旁白（「问完这句就等他了，不再发了」「我先不回，等他接话」）：
        //    命中行动计划标记，且是「我」对自己下一步的交代（含「我/先/就/不再」等
        //    计划语气），且不是对用户提问 → 判为思考泄漏。
        val actionHit = ACTION_PLAN_MARKERS.count { t.contains(it) }
        if (actionHit > 0) {
            val hasSelfPlanTone = t.contains("我") || t.contains("先") ||
                t.contains("就") || t.contains("不再") || t.contains("不先")
            return hasSelfPlanTone && !directAddress && !asksQuestion
        }
        // ③ 兜底：对白特征缺失 + 计划/汇报语气 → 判为旁白。
        //    这是比①②更通用的判据——不依赖枚举词，而是抓「句式结构」：
        //    旁白（思考泄漏）本质是「我」对自己状态/计划的短陈述，不含任何指向用户的
        //    互动信号（第二人称/疑问/情感互动词）。而正常对白几乎必然含至少一个互动信号。
        //    例：「已经回了上面那条了，等着他接话就行」「问完这句就等他了，不再发了」
        //        「我先不回了，等他接话」——都命中。
        if (isPlanOrReportNarration(t, directAddress, asksQuestion)) {
            return true
        }
        return false
    }

    /** 计划/汇报式旁白：以自我或计划/汇报语气为骨架，且完全无对用户互动信号的短陈述。 */
    private fun isPlanOrReportNarration(
        text: String,
        directAddress: Boolean,
        asksQuestion: Boolean,
    ): Boolean {
        if (directAddress || asksQuestion) return false
        val t = text.trim()
        // 太长的文本通常是真实对白（旁白泄漏一般是一句短陈述），超过阈值不判旁白，防误伤。
        if (t.length > 24) return false
        // 含情感/互动词 → 是对用户表达，放行。
        val hasInteraction = INTERACTION_WORDS.any { t.contains(it) }
        if (hasInteraction) return false
        // 计划/汇报语气骨架：以「我/先/就/已经/不再/不先/好了/行了/这就/等着」等词开头或包含，
        // 且是「自我交代下一步/汇报已完成」的陈述（而非对用户的询问/回应）。
        val planTone = PLAN_TONE_PREFIXES.any { t.startsWith(it) } ||
            PLAN_TONE_WORDS.any { t.contains(it) }
        return planTone
    }
}

