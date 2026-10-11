# YuNian v2.2.0 变更与功能说明

> 版本：`versionCode 30` / `versionName 2.2.0`
> 日期：2026-10-11　|　分支：`dev-mulai`　|　工程：`D:\WorkSpace\Project\YuNian`
> 本文是 v2.2.0 的**变更记录 + 功能分析**，并汇总**插件详情**与**世界书模板**两类长期参考。

---

## 一、变更总览

| # | 变更项 | 类型 | 一句话 |
|---|---|---|---|
| 1 | 移除"液态玻璃效果"开关 | 清理 | 界面已改微信风格实底 surface，该开关不再产生视觉差异 |
| 2 | 底部 Tab 沉浸式修复 | 缺陷修复 | 修正 Modifier 顺序 + 统一让位，消除底部悬浮与色带 |
| 3 | 版本检查本地化 | 行为变更 | 恒返回"已是最新"，不再请求云端 |
| 4 | 插件默认启用状态调整 | 产品默认 | 消息通道 + 4 个非聊天通用插件**默认关闭** |

代码改动共 16 个文件（+218 / −195，含测试与资源）。

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

**需求**：消息通道 QQ、微信**默认关闭**；通用插件**默认只开启**"内置聊天工具协议技能"+"表情包偏好引擎"，其余默认关闭。

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

**出厂默认停用集（6 个）**：

```
channel.qqbot              消息通道 · QQ 机器人
channel.wechat             消息通道 · 微信
automation.core            通用 · 自动化工具
message.send               通用 · 消息发送
ui.assists                 通用 · 无障碍自动化
chat.first_person_reasoning 通用 · 第一人称思考
```

**默认保留启用**：`skill.builtin_chat_protocol`（气泡/连发/表情用法，聊天观感核心）、`sticker.preference`（表情包偏好引擎）。

**生效语义**：

| 场景 | 结果 |
|---|---|
| 新装用户（KV 从未写过） | 6 个插件装载后被卸载 ⇒ 开箱即"纯聊天"默认 |
| 用户在设置页打开其中某个 | 该 id 从 KV 停用集移除 ⇒ 重启后**保持开启** |
| 用户关掉保留启用的插件 | 加入 KV ⇒ 重启后**保持关闭** |
| KV 读取失败 / 损坏 | 空集（全部启用）——fail-safe 行为**不变** |
| 老用户（KV 已有内容） | 尊重其既有设置，不被出厂集覆盖 |

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

> 完整字段表见 **`docs/person-mulai-docs/世界书导入格式说明.md`**；可直接导入的样例见 `世界书导入模板.json` 与 `世界书-身世经营相识.json`。

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
- **产物**：加壳 release APK `app_relase_2.2.0.apk`（versionCode 30 / versionName 2.2.0，包名 `com.yunian.ai`）。
- **构建命令**：
  ```
  ./gradlew assembleRelease -PyunianDisableMinify=true -x lintVitalAnalyzeRelease
  ```
  `-PyunianDisableMinify=true` 必须带，否则 dex2c 真实转译会在 NDK 编译处 rename 失败。
