# YuNian v2.2.0 / v2.2.1 变更与功能说明

> 版本：`versionCode 31` / `versionName 2.2.1`（v2.2.0 = code 30）
> 日期：2026-10-11　|　分支：`dev-mulai`　|　工程：`D:\WorkSpace\Project\YuNian`
> 本文是 v2.2.0 的**变更记录 + 功能分析**，并汇总**插件详情**与**世界书模板**两类长期参考；
> **第六节**为 v2.2.1 的补充修复（引用信封剥离 / 气泡条数上限 / 人设规则措辞）。

---

## 一、变更总览

| # | 变更项 | 类型 | 一句话 |
|---|---|---|---|
| 1 | 移除"液态玻璃效果"开关 | 清理 | 界面已改微信风格实底 surface，该开关不再产生视觉差异 |
| 2 | 底部 Tab 沉浸式修复 | 缺陷修复 | 修正 Modifier 顺序 + 统一让位，消除底部悬浮与色带 |
| 3 | 版本检查本地化 | 行为变更 | 恒返回"已是最新"，不再请求云端 |
| 4 | 插件默认启用状态调整 | 产品默认 | 消息通道 QQ/微信**默认关闭**；通用插件**默认全开**（原"通用插件默认只留两个"已回滚） |

**v2.2.1 补充修复**（详见第六节）：

| # | 变更项 | 类型 | 一句话 |
|---|---|---|---|
| 5 | 引用回复信封剥离 | 缺陷修复 | 引用块内部信封不再原样进提示词，只把用户真正输入的那句交给模型 |
| 6 | 气泡条数上限 4→3 | 行为变更 | 物理上削弱"一轮把双方台词都演完" |
| 7 | 人设规则 1 措辞修订 | 用户侧文本 | 「只输出角色对白」→「只输出你自己要说的话」，显式禁止替用户作答 |

> v2.2.0 代码改动共 16 个文件（+218 / −195，含测试与资源）；v2.2.1 在其之上追加 2 个代码文件 + 1 个测试 + 1 个 so 重编。

---

## 二、功能分析（逐项根因与机制）

### 1. 移除"液态玻璃效果"开关

**背景**：设置 → 主题 里的"液态玻璃效果"开关，是早期玻璃拟态视觉的总闸。后来界面整体改为微信风格的**实底 surface**，该开关已**不再产生任何视觉差异**。

**改动**：

| 文件 | 改动 |
|---|---|
| `feature/settings/.../ui/screen/ThemeScreen.kt` | 删除 `GlassEffectCard` 卡片、相关 import 与 `Color.luminance()` |
| `core/common/.../AppSettingsStore.kt` | 删除 `LIQUID_GLASS_ENABLED_KEY`、读写方法与两条 flow |
| `app/.../YuNianApplication.kt` | 删除启动时从 DataStore 回填 `GlassEffectGate` 的那段 |
| `feature/settings/src/main/res/values{,-en,-ja,-ko,-zh-rTW}/strings.xml` | 删除 `glass_effect_title` / `glass_effect_subtitle` |

**刻意保留**：`GlassEffectGate` 对象本身。它被 `LiquidGlass` / `PageBackdrop` / `ChatScreen` / `GroupChatScreen` / `StickerImportOverlay` 等 5+ 处消费；直接删除要改动全部消费方。现在它是一个**恒为 `false` 的降级闸门**（无持久化、无写入点），各消费方走既有的纯色分支。将来若要重新引入，只需恢复**一个写入点**（`GlassEffectGate.enabled = ...`）。

> 副作用：老用户即便曾打开过该开关，也不再被点亮（`enabled` 不再从存储读取）。

### 2. 底部 Tab 沉浸式修复

**现象**：高版本 Android（edge-to-edge / 沉浸式导航栏）下，底部 Tab 栏**悬浮在内容之上**，"关于应用"等列表末项被系统导航栏遮住——没有真正做到沉浸。

**根因（关键）**：`MainBottomBar.kt` 的 `FloatingGlassBottomNav` 里 **Modifier 顺序写反**：

```kotlin
// 错误：先 inset 再 background → 背景只铺到被压缩后的内容区，底部空出系统导航栏那一条
Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).background(surface)
// 正确：先 background 再 inset → 背景覆盖整个节点，系统导航栏区域也被着色
Modifier.fillMaxWidth().background(surface).windowInsetsPadding(WindowInsets.navigationBars)
```

同时，各 Tab 页**各自为底部让位居高**，魔数与口径不一（HomeScreen 固定 `80.dp`、ProfileScreen 固定 `80.dp` 未计导航栏内边距、ContactsScreen `0.dp`）。

**改动**：

| 文件 | 改动 |
|---|---|
| `app/.../MainBottomBar.kt` | 新增公共常量 `BottomNavBarHeight`（1dp 分割线 + 60dp 图标行）；把 `background` 调到 `windowInsetsPadding` **之前** |
| `app/.../MainNavGraph.kt` | `MainTabScreen` 里**单点**统一让位：`padding(bottom = BottomNavBarHeight + 导航栏内边距)` 应用到 `MainTabPager` |
| `feature/profile/.../HomeScreen.kt` | LazyColumn 底部内边距由 `80.dp + 导航栏内边距` 改为 `16.dp`（让位收敛到 MainTabScreen） |
| `feature/profile/.../ProfileScreen.kt` | 尾部 `Spacer` 由 `80.dp` 改为 `24.dp` |

**收益**：底部条**背景覆盖完整**（含系统导航栏区域），真正沉浸；让位口径**单点收敛**，新增 Tab 页不需要再猜魔数。

### 3. 版本检查本地化

**需求**：所有"检查版本更新"入口**一律返回"已是最新"**，不再请求云端。

**改动**：在 `feature/settings/.../update/AppUpdateManager.kt` 的 `checkForUpdates()` **单点收口**：

```kotlin
suspend fun checkForUpdates() {
    _updateCheckState.value = UpdateCheckState.LATEST
    _updateInfo.value = null
    _showUpdateDialog.value = false
}
```

**为什么是单点收口**而不是逐个删除入口：有 3 个消费方（`MainActivity` 冷启动、`CheckUpdateScreen`、`MainScreen` 更新对话框），在数据源处一次性改为本地，**结构上杜绝任何云端请求**。

**刻意保留**：下载/安装链路（签名清单、AES-GCM、SHA-256、Range 断点续传、镜像回退）**原样保留但不可达**；原方法体全文写进 KDoc，便于日后复原。`getCurrentVersionCode()` / `compareVersion()` 标注 `@Suppress("unused")`（仓库无 `-Werror`，仅告警）。

### 4. 插件默认启用状态调整（v2.2.0 产品默认）

**原需求**：消息通道 QQ、微信**默认关闭**；通用插件**默认只开启**"内置聊天工具协议技能"+"表情包偏好引擎"，其余默认关闭。

**最终结论（已部分回滚）**：

- **消息通道 QQ/微信默认关闭** —— 保留。
- **通用插件默认全开** —— 回滚。原"通用插件默认只留两个"的方案**放弃**。

**回滚原因（实测发现）**：原方案把 `chat.first_person_reasoning`（第一人称思考）也默认关闭了。实测发现——**这个插件不只注入思维链，它的规则文本里还夹着一条"思考泄漏红线"**：

> 思考过程与内心评估（如"等他继续聊""目前氛围是…""状态稳定"）绝对禁止出现在任何输出里……

关掉插件 = 思维链引导 **和这条红线一起消失**。而 Kotlin 侧的后处理兜底（`ResponsePostProcessor`）用的是**窄词表**（"等用户回答""等对方回"等未收录），且其首条判据"含『你』就放行"会让带称呼的泄漏直接穿透。结果是：**重装后元叙述泄漏明显增多**，出现"等他回答"这类内容。

> 注：这条红线本可拆出来做成独立恒注入段（治本），但当前决定**整体回滚**——最省事的做法就是别默认关它，让行为与 v2.1.5 一致。

**为什么不能只改蓝图**（关键设计决策）：

插件装载链路是 **蓝图全量装载 → 停用覆盖层做减法（unload）**：

```
loadDefaultBlueprint()  →  按 assets/blueprints/default.json 装载（条目 enabled 默认 true）
applyDisabledPluginOverlay()  →  对 PluginEnablementStore.disabledIds() 里的每个 id 调 host.unload()
```

- 蓝图 `default.json` 是**唯一装载源**，且是**只读资产**（全仓无写回代码）；
- 覆盖层**只能减不能加**。

所以若把蓝图条目写成 `enabled:false`：用户即便在设置页手动打开（`host.load` + 从停用集移除），**重启后蓝图仍跳过它 ⇒ 开启丢失**。

**采用的方案：出厂默认停用集**。KV 键 `plugin.enablement` **从未写过**（= 用户从未在本机改过任何插件开关 = 冷启动）时，返回一组"出厂默认停用"id；一旦用户动过任一开关，`setEnabled` 会把"当前集 ± 本次改动"整段落盘，此后以 KV 为准。于是"用户把出厂关掉的插件打开"会真的**跨重启保留**。

**改动**：

| 文件 | 改动 |
|---|---|
| `app/.../PluginEnablementStoreImpl.kt` | 新增构造参数 `factoryDisabledIds`（默认空集 = 旧行为）；`disabledIds()` 区分"键未写过（→ 出厂集）"与"读失败（→ 空集，fail-safe 不变）" |
| `app/.../YuNianApplication.kt` | 新增常量 `FACTORY_DISABLED_PLUGIN_IDS` 并在注册存储时注入 |
| `app/src/test/.../PluginEnablementStoreImplTest.kt` | 新增 3 条用例覆盖出厂集语义 |

**出厂默认停用集（2 个，最终版）**：

```
channel.qqbot              消息通道 · QQ 机器人
channel.wechat             消息通道 · 微信
```

**默认全开**：其余全部通用插件（`skill.builtin_chat_protocol`、`sticker.preference`、`automation.core`、`message.send`、`ui.assists`、`chat.first_person_reasoning`）保持启用，与 v2.1.5 一致。用户如需"纯聊天"，可在设置页自行关闭（关闭**跨重启保留**）。

**生效语义**：

| 场景 | 结果 |
|---|---|
| 新装用户（KV 从未写过） | 2 个通道插件装载后被卸载 ⇒ 开箱默认不启用 QQ/微信通道，通用插件全开 |
| 用户在设置页打开其中某个 | 该 id 从 KV 停用集移除 ⇒ 重启后**保持开启** |
| 用户关掉某个通用插件 | 加入 KV ⇒ 重启后**保持关闭** |
| KV 读取失败 / 损坏 | 空集（全部启用）——fail-safe 行为**不变** |
| 老用户（KV 已有内容） | 尊重其既有设置，不被出厂集覆盖 |

> 边界说明：`channel.wechat` / `channel.qqbot` 关闭只影响"通道接入 Channel 框架 + 认领回合内出站请求"。微信的**连接/心跳/重连/保活**由 `WeChatPollingService` 独立负责，App 内**定时主动消息**由 `CompanionMessageWorker`（WorkManager，`tools = emptyList()`）负责——两者**均不依赖**通道插件。

> 边界说明：`channel.wechat` / `channel.qqbot` 关闭只影响"通道接入 Channel 框架 + 认领回合内出站请求"。微信的**连接/心跳/重连/保活**由 `WeChatPollingService` 独立负责，App 内**定时主动消息**由 `CompanionMessageWorker`（WorkManager，`tools = emptyList()`）负责——两者**均不依赖**通道插件。

---

## 三、插件详情

> 完整清单与逐条说明见 **`docs/person-mulai-docs/通用插件清单.md`**，此处摘录要点。

### 3.1 分类规则

分类**只按 `PluginKind` 归**，不按 id 写死：

| `PluginKind` | 归属 Tab |
|---|---|
| `ADAPTER` | 消息通道 |
| `TOOL` / `SKILL` / `STICKER` / `PIPELINE` | 通用插件 |

### 3.2 通用插件（Tab 索引 1）

- `tool.grant` —— 工具授权（**保留条目**，非插件，无开关；管理需确认的 Agent 工具能力）
- `automation.core` —— 自动化工具（TOOL）
- `chat.first_person_reasoning` —— 第一人称思考（PIPELINE）
- `message.send` —— 消息发送（TOOL）
- `skill.builtin_chat_protocol` —— 内置聊天工具协议技能（SKILL）
- `sticker.preference` —— 表情包偏好引擎（STICKER）
- `ui.assists` —— 无障碍自动化（TOOL）

### 3.3 消息通道（Tab 索引 0）

- `channel.qqbot` —— QQ 机器人通道（ADAPTER）
- `channel.wechat` —— 微信通道（ADAPTER）

### 3.4 两个"开关关了也不生效"的坑

- `skill.builtin_chat_protocol` / `sticker.preference` 的 `setup()` 是**启动时一次性初始化**（幂等种子 / 文件钩子接线），**没有 `ctx.effect` 注销副作用** ⇒ 关掉开关不会回滚已写入的内容。
  - 要真正停掉气泡协议，应到**技能中心禁用 `builtin_chat_tool_protocol` 这条技能**，而非关插件开关（对纯聊天**不建议**停——停了会退化为整段大文本）。
- `ChatToolLifecyclePlugin`（工具执行状态卡片）**不在蓝图、不在设置页**，由聊天侧**按回合**注册/卸载，属于内部装配件。

---

## 四、世界书模板

> 完整字段表、可直接复制的空白模板与完整示例（身世·经营·相识），全部合并见 **`docs/person-mulai-docs/世界书导入格式说明.md`**（该文档自包含，不再附单独的 `.json` 文件）。

### 4.1 关键：`entries` 必须是对象（map），不是数组

本应用**同时**兼容两种格式（`WorldbookTransfer.parseAny` 自动识别）：

| 格式 | 顶层结构 | 支持度 |
|---|---|---|
| **ST World Info**（推荐） | `entries` 为**对象**，key = uid | 引擎自研条目，**5 值 `position`** 全支持 |
| 旧 `lianyu-worldbook` DTO | 顶层扁平字段 | 兼容读取 |

⚠️ **只有 `entries` 是数组时**才会走 `chara_card` 校验，而该 crate 的 `position` 枚举**只认 2 个值**、且会丢弃 `depth` / `role` / `scan_depth`。所以模板统一用 **map 格式**。

### 4.2 模板骨架

```json
{
  "name": "世界书名称",
  "description": "简短描述",
  "scan_depth": 10,
  "token_budget": 10000,
  "entries": {
    "0": {
      "uid": 0,
      "id": 0,
      "keys": ["触发词1", "触发词2"],
      "secondary_keys": [],
      "comment": "条目备注",
      "content": "命中后注入的内容",
      "enabled": true,
      "constant": false,
      "selective": false,
      "insertion_order": 1,
      "case_sensitive": false,
      "use_regex": false,
      "position": "after_char",
      "role": null,
      "scan_depth": null,
      "depth": null,
      "extensions": { "_priority": 100 }
    }
  }
}
```

### 4.3 两个易错点

- **`position` 取 5 值**：`before_char` / `after_char` / `top_of_chat` / `bottom_of_chat` / `at_depth`。要"贴近最近消息"用 `at_depth` 并配 `depth`。
- **优先级键是 `extensions._priority`，不是顶层 `priority`**（ST 规范无 priority，只有 `insertion_order`）：

  ```
  insertion_order = 2³² − priority      // 两者取反
  ```

  本应用导出约定：`insertion_order` 写**连续 1..N**（Rust 排序只需唯一），真实优先级由 `extensions._priority` 承载。若把 `insertion_order` 写成 10 位数，编辑器里限 4 位的"优先级"输入框会溢出显示乱数。

---

## 五、验证与产物

- **编译**：`./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`（仅存量 deprecation 告警，无错误）。
- **单测**：`PluginEnablementStoreImplTest` 覆盖出厂集语义（冷启动返回出厂集 / 开启可跨重启 / 用户集优先于出厂集）。
- **产物**：加壳 release APK `app_relase_2.2.0.apk`（code 30 / 2.2.0）、`app_relase_2.2.1.apk`（code 31 / 2.2.1），包名 `com.yunian.ai`。
- **构建命令**：
  ```
  ./gradlew assembleRelease -PyunianDisableMinify=true -x lintVitalAnalyzeRelease
  ```
  `-PyunianDisableMinify=true` 必须带，否则 dex2c 真实转译会在 NDK 编译处 rename 失败。

---

## 六、v2.2.1 补充修复

> 起因：真机聊天中出现两类"越界"——①**元叙述泄漏**（"等他回答"这类内心评估漏进气泡）；
> ②**代用户发言**（角色把用户那一方的台词也写出来，当自己的连发气泡发出去）。
> 定位后确认：②的主因是**引用回复的内部信封泄漏进提示词**，另有措辞与阈值的放大因素。

### 6.1 引用回复信封剥离（缺陷修复）

**现象**：用户**引用回复**某条消息后，模型这一轮**不再回应最新输入**，而是顺着被引用那句往下"续演"，甚至把用户回合也写完。

**根因**：`QuoteReply.encodeQuotedMessage()` 把引用编码成一段**内部信封**，直接拼进消息正文：

```
[引用回复]12|方唐婧: 了。想看就大大方方看，我又不躲。
---
算了，别穿了，这样就好，我也能光明正大的欣赏你的风光
```

而**解码它的 `parseQuotedTextContent` 只在 UI 里被调用**（`TextMessageItem` / `VoiceMessageItem`），提示词链路上从来没人解它——`sanitizeForModel` 不剥、`contentForModel()` 只特判图片。于是模型收到的是一条 user 消息里混着 **"作者名 + 冒号 + 被引用原文 + `---` 分隔符"** 的伪聊天记录：既冗余（被引用那条本就在历史里），又像一段**待续写的对话脚本**，模型很容易顺着往下演。

**改动**：

| 文件 | 改动 |
|---|---|
| `feature/chat/.../ChatTypeConverters.kt` | `contentForModel()` 新增 `stripQuoteEnvelopeForModel()`：识别引用信封并**只保留分隔符之后的用户真实输入**；信封残缺时**退回原文**，绝不把消息吞成空串 |
| `feature/chat/src/test/.../QuoteEnvelopeForModelTest.kt` | 新增 4 例（正常引用 / 带媒体行 / 普通消息不变 / 信封残缺不吞内容） |

> UI 显示不受影响——渲染仍读原始 `content`，剥离只发生在**送往模型**的那一份。

### 6.2 气泡条数上限 4 → 3（行为变更）

**背景**：`agent-native/src/agent.rs` 的 `BUBBLE_SOFT_CAP` 是 `emit_bubble` / `emit_segmented` 的**工具层硬阈值**——本轮气泡数达到该值后，再发**直接拒绝**（返回 `{"status":"rejected","reason":"bubble_cap_reached","limit":N}`），并注入收尾提示。

**真机实测**：上限为 4 时，模型倾向**把整场戏一次演完**；最后一条常常是**用户那一方的台词**（如"嗯，有婧姐这句话，我就踏实了，那我睡了"），被当作角色气泡连发出去。截图那轮正好是 4 条文字气泡（= 撞满上限）。

**改动**：`BUBBLE_SOFT_CAP` 由 `4` 收紧为 `3`，并同步修正注释（原文写"软上限 = 注入提示而非硬终止"，与代码实际的硬拒绝不符）。

**效果**：截到 3 可把末尾那条越界气泡挡掉，同时仍保留用户明确要求连发时的基本能力。相关 Rust 单测（`bubble_hard_cap_rejects_when_limit_reached` / `segmented_respects_remaining_quota`）均以**符号常量**断言，改值后依旧通过。

### 6.3 人设规则 1 措辞修订（用户侧文本，无需出包）

**根因（措辞层）**：规则 1 原文写「只输出**角色对白**」。LLM 对"对白"（dialogue）的默认理解是**双向对话**，这句本身可能在暗示它"把这段对白写完整"。

**修订对照**（仅第 1 条，其余规则不变）：

- 原：`……禁用括号。只输出角色对白，不写动作、神态、心理、旁白、场景说明。第3条场景内，……`
- 新：`……禁用括号。只输出你自己要说的话，不写动作、神态、心理、旁白、场景说明；绝不写用户那一方的台词、回应或心理，绝不替用户作答，一轮说完就停，下一句由用户自己发。第3条场景内，……`

即：把"角色对白"→"你自己要说的话"，并**显式禁止替用户作答**。

### 6.4 三道防线现状（诊断结论）

| 防线 | 位置 | 对"代用户发言"是否有效 |
|---|---|---|
| 提示词红线（技能第 5/7 条：禁自问自答 / 禁替用户说话） | `prompt_orchestrator.rs`（只要 `emit_bubble` 在可用工具里就整篇注入） | **已注入**，但抽象禁令压制力有限 |
| 确定性兜底 `ScriptTurnStripper` | agent 气泡路径每条都过 | **只认显式回合标记**（`用户：` / `你：`）；无标记的裸句子覆盖不到 |
| `AgentReplyText.forDisplay` | 全部气泡唯一必经点 | 管"思考泄漏 / 编排状态复述"，与"代用户发言"无关 |

**未采纳项（B）**：把协议技能第 5/7 条改成**带具体反例**——需 bump `BUILTIN_CHAT_TOOL_SKILL_VERSION` 才会对老用户生效，本轮**先观察 A+C 效果**再决定。
