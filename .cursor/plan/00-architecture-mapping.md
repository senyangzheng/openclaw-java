# 00 · 架构映射：openclaw (TS) → openclaw-java (Java)

本文给出 `openclaw` 源码主要目录 → `openclaw-java` Maven 模块 / 包 的对应关系，翻译时以此为索引。

---

## 1. 原项目结构速览

```
openclaw/
├── src/
│   ├── cli/                   CLI 入口与命令框架
│   ├── commands/              命令定义与执行
│   ├── entry.ts               应用总入口
│   ├── runtime.ts             运行时编排
│   ├── gateway/               Gateway 控制面（WS + HTTP）
│   ├── agents/                智能体框架（Agent 运行时）
│   ├── auto-reply/            自动回复流水线
│   ├── channels/              通道适配器框架与注册
│   ├── telegram/ discord/ whatsapp/ line/ slack/ imessage/ signal/ web/  具体通道
│   ├── providers/             模型 Provider（OpenAI/Anthropic/Google/Copilot…）
│   ├── routing/               路由与会话键
│   ├── sessions/              会话存储
│   ├── config/                配置加载与热重载
│   ├── secrets/               密钥/凭据
│   ├── security/              安全模块与审计
│   ├── plugins/ plugin-sdk/   插件系统
│   ├── media/ media-understanding/ link-understanding/  媒体管线
│   ├── memory/                记忆（上下文）
│   ├── context-engine/        上下文引擎
│   ├── tts/                   语音合成
│   ├── browser/               浏览器自动化
│   ├── cron/                  定时任务
│   ├── daemon/                守护进程
│   ├── process/ terminal/     进程/终端
│   ├── hooks/                 事件钩子
│   ├── logging/ logger.ts     日志
│   ├── i18n/                  国际化
│   ├── markdown/              Markdown 处理
│   ├── acp/                   ACP 协议（跨进程）
│   ├── node-host/ canvas-host/ 宿主进程
│   ├── pairing/               配对
│   ├── polls.ts               轮询
│   ├── signal/                信号处理
│   ├── infra/                 基础设施
│   ├── scripts/               运行脚本
│   ├── shared/ types/ utils/  共享工具与类型
│   └── test-utils/ test-helpers/  测试辅助
├── packages/                  独立子包（clawdbot、moltbot）
├── apps/                      桌面/移动端（ios、android、macos、shared）
├── extensions/                VSCode 扩展等
├── skills/                    skills 快照
└── scripts/                   构建与运维脚本
```

---

## 2. Java 工程结构（目标）

```
openclaw-java/                              Maven 聚合根（<packaging>pom</packaging>）
├── pom.xml                                 父 POM：BOM + dependencyManagement + 插件版本
├── openclaw-common/                        [M1] 工具、类型、错误码、SPI 基础契约
├── openclaw-logging/                       [M1] 日志门面封装（SLF4J + MDC + 审计）
├── openclaw-config/                        [M1] @ConfigurationProperties + 配置加载 + 热重载
├── openclaw-secrets/                       [M1] 密钥/凭据管理（本地 + 环境变量 + Vault 可选）
├── openclaw-sessions/                      [M1] 会话存储（JPA + Caffeine）
├── openclaw-routing/                       [M1] 路由表与会话键
│
├── openclaw-providers-api/                 [M2] Provider SPI：请求/响应抽象 + 流式事件
├── openclaw-providers-google/              [M2] Google Gemini  （首期必做）
├── openclaw-providers-qwen/                [M2] Qwen           （首期必做）
├── openclaw-providers-registry/            [M2] Provider 注册/选路/回退/cooldown/auth-profile
│   # ⛔ openclaw-providers-openai / anthropic / github-copilot 暂不建立（可后续增补）
│
├── openclaw-plugin-sdk/                    [M2] 插件 SPI
├── openclaw-plugins/                       [M2] 内置插件聚合
│
├── openclaw-memory/                        [M2] 记忆 / 上下文快照
├── openclaw-context-engine/                [M2] 上下文引擎（压缩、截断、窗口）
│   # ⛔ 以下多媒体/链接模块在当前范围内 **不建立**：
│   #   openclaw-media、openclaw-media-understanding、openclaw-link-understanding
│
├── openclaw-tools/                         [M3.2] 工具系统（tool schema + ToolPolicyPipeline + before/after hook + AdjustedParamsStore）
├── openclaw-skills/                        [M3.6] skills 快照与注入（workspace + watcher + env overrides + remote cache）
├── openclaw-agents-core/                   [M3.1/3.4] PiAgentRunner（只调度）/ AgentAttemptExecutor / SubscribeState / ActiveRunRegistry
├── openclaw-agents-fallback/               [M3.4] FailoverError / 分类器 / resolveFallbackCandidates / runWithModelFallback / AuthProfileRotator
├── openclaw-agents-subagent/               [M3.6] SessionsSpawnTool / SessionsSendTool / SubagentRegistry / SubagentAnnounceFlow（防递归 + allowAgents + nested lane）
├── openclaw-hooks-runtime/                 [M3.6] 统一 Hook Runner（priority / void 并行 / modifying 串行 merge / catchErrors）
├── openclaw-auto-reply/                    [M3.1] 自动回复流水线（M3 内部改走 PiAgentRunner）
├── openclaw-session-lanes/                 [M3.3] 双层 Lane 并发（sessionLane × globalLane × Cron/Main/Subagent）
├── openclaw-stream/                        [M3.1] AgentEvent sealed + ProviderChunk→AgentEvent 翻译器 + AgentEventSink
├── openclaw-approval/                      [M3.6] 审批状态机（ExecApprovalManager，timeout=null + 15s grace + 幂等 register）
│   # 注意：多模态输入 / 图片工具结果在 M3 内**以 UnsupportedOperationException 占位**，只保留接口
│
├── openclaw-gateway-api/                   [M4] Gateway 协议帧 + Schema 定义
├── openclaw-gateway-core/                  [M4] 方法分发、鉴权、分组
├── openclaw-gateway-ws/                    [M4] WebSocket 端点（握手/connect）
├── openclaw-gateway-http/                  [M4] HTTP 控制面 + 安全网
├── openclaw-gateway-openai-compat/         [M4] /v1/chat/completions 兼容（仅文本 + 流式）
├── openclaw-gateway-hooks/                 [M4] hooks / tools-invoke HTTP 入口
│   # ⛔ openclaw-gateway-openresponses（多模态端点）暂不建立，推迟至未来多模态范围恢复后
│
├── openclaw-cli/                           [M5] CLI 命令框架（Picocli）
├── openclaw-commands/                      [M5] 命令定义与执行
│
├── openclaw-channels-core/                 [M1] 通道适配器框架 + 账号生命周期（SPI 永久保留）
├── openclaw-channels-web/                  [M1] Web/HTTP + WebSocket 通道（**唯一实现**）
│   # ⛔ telegram / discord / whatsapp / line / slack / imessage / signal 全部暂不建立
│
├── openclaw-security/                      [M5] 安全模块 + 审计
├── openclaw-cron/                          [M5] 定时任务（**ElasticJob-Lite + ZooKeeper**）
├── openclaw-daemon/                        [M5] 守护进程管理（ProcessBuilder + ProcessHandle）
├── openclaw-browser/                       [M5] 浏览器自动化（Playwright for Java）
├── # openclaw-hooks（TS src/hooks/）已并入 openclaw-hooks-runtime（M3 重命名）
├── openclaw-i18n/                          [M5] 国际化
│   # ⛔ openclaw-tts / openclaw-process 暂不建立（仅文本范围，不做音频 / PTY）
│
├── openclaw-server-runtime-config/         [M4.0] 运行时配置（buildGatewayReloadPlan + GatewayIdleGuard + 60s 兜底）
├── openclaw-server-restart-sentinel/       [M4.0] 重启哨兵（主动重启 vs 崩溃）
├── openclaw-server-tailscale/              [M6]
├── openclaw-server-discovery/              [M6]
├── openclaw-server-lanes/                  [M6]
├── openclaw-server-model-catalog/          [M6]
├── openclaw-server-session-key/            [M6]
├── openclaw-server-startup-memory/         [M6]
│
└── openclaw-bootstrap/                     [M-LAST] Spring Boot 聚合启动，打可执行 jar / Docker
```

> M1–M6 与 `03-roadmap.md` 的阶段数字严格对应。

---

## 3. 关键模块对应表

| openclaw (TS) 路径 | openclaw-java 模块 | Java 主要包名 | 说明 |
|---|---|---|---|
| `src/cli/` | `openclaw-cli` | `com.openclaw.cli` | 使用 Picocli 重写命令解析 |
| `src/commands/` | `openclaw-commands` | `com.openclaw.commands` | 命令定义 + 分发器 |
| `src/entry.ts`、`src/runtime.ts` | `openclaw-bootstrap` | `com.openclaw.bootstrap` | `@SpringBootApplication` 聚合启动 |
| `src/gateway/` | `openclaw-gateway-*` | `com.openclaw.gateway.*` | 拆成 core/ws/http/openai-compat/hooks |
| `src/agents/` | `openclaw-agents-core` + `openclaw-agents-fallback` + `openclaw-agents-subagent` + `openclaw-session-lanes` + `openclaw-stream` | `com.openclaw.agents.*` | 核心难点，按职责拆成 5 个模块（详见下方 §4 细粒度映射） |
| `src/auto-reply/` | `openclaw-auto-reply` | `com.openclaw.autoreply` | 基于 agents 构建 |
| `src/channels/` | `openclaw-channels-core` | `com.openclaw.channels.core` | 通道 SPI + 注册中心 + 账号生命周期 |
| `src/telegram/` `discord/` … | **暂不翻译** | — | 仅保留 `ChannelAdapter` SPI，实现延后 |
| `src/providers/` | `openclaw-providers-api` + `providers-google` + `providers-qwen` + `providers-registry` | `com.openclaw.providers.*` | **首期仅 Google Gemini + Qwen**，其余 Provider 暂不做 |
| `src/routing/` | `openclaw-routing` | `com.openclaw.routing` | |
| `src/sessions/` | `openclaw-sessions` | `com.openclaw.sessions` | **MyBatis-Plus + MySQL + HikariCP + Flyway + Caffeine** |
| `src/config/` | `openclaw-config` | `com.openclaw.config` | `@ConfigurationProperties` + 监听文件变更 |
| `src/secrets/` | `openclaw-secrets` | `com.openclaw.secrets` | |
| `src/security/` | `openclaw-security` | `com.openclaw.security` | Spring Security + 审计 |
| `src/plugins/`、`src/plugin-sdk/` | `openclaw-plugin-sdk` + `openclaw-plugins` | `com.openclaw.plugin.*` | SPI + 动态加载 |
| `src/media/` | **暂不翻译** | — | 仅文本范围 |
| `src/media-understanding/` | **暂不翻译** | — | 仅文本范围 |
| `src/link-understanding/` | **暂不翻译** | — | 仅文本范围 |
| `src/memory/` | `openclaw-memory` | `com.openclaw.memory` | |
| `src/context-engine/` | `openclaw-context-engine` | `com.openclaw.context` | 压缩/截断恢复 |
| `src/tts/` | **暂不翻译** | — | 仅文本范围 |
| `src/browser/` | `openclaw-browser` | `com.openclaw.browser` | **Playwright for Java**（恢复保留） |
| `src/cron/` | `openclaw-cron` | `com.openclaw.cron` | **ElasticJob-Lite + ZooKeeper** |
| `src/daemon/` | `openclaw-daemon` | `com.openclaw.daemon` | |
| `src/process/`、`src/terminal/` | **暂不翻译**（`openclaw-daemon` 内用 `ProcessBuilder` 代替） | — | — |
| `src/hooks/` + `src/plugins/hook-runner-global.ts` | `openclaw-hooks-runtime` | `com.openclaw.hooks` | 统一 Hook Runner，供 agents/tools/approval/gateway 复用 |
| `src/logging/`、`src/logger.ts` | `openclaw-logging` | `com.openclaw.logging` | SLF4J + Logback + MDC |
| `src/i18n/` | `openclaw-i18n` | `com.openclaw.i18n` | `MessageSource` |
| `src/markdown/` | `openclaw-common`（子包） | `com.openclaw.common.markdown` | 使用 commonmark-java |
| `src/acp/` | 未定，先收敛到 `openclaw-agents` | `com.openclaw.agents.acp` | 跨进程协议 |
| `src/node-host/`、`src/canvas-host/` | 视需要拆分 | `com.openclaw.host.*` | 宿主进程通信 |
| `src/shared/`、`src/utils/`、`src/types/` | `openclaw-common` | `com.openclaw.common.*` | 基础设施 |
| `scripts/`、`Dockerfile*` | 根目录 + `docker/` | — | 保留脚本化能力 |
| `skills/` | `openclaw-skills`（资源） | — | 以 resources/classpath 挂载 |

---

## 4. 细粒度映射（M3 范围：Agent 框架）

> 依据 OpenClaw 官方文档 02（Agent 执行框架）/ 03（会话并发）/ 04（工具-审批-安全）/ 07（AI 框架总原理）/ 08-13 专题补齐，列出关键 TS 文件 / 符号 → Java 目标模块 / 类的映射。

### 4.1 PiAgent 主链路（对应 M3.1 / M3.4）

| TS 源位置（`openclaw/src/`） | Java 目标模块 | Java 类 / 包 | 关键常量 / 精确字段 |
|---|---|---|---|
| `agents/pi-embedded-runner/run.ts` · `runEmbeddedPiAgent` | `openclaw-agents-core` | `com.openclaw.agents.PiAgentRunner`（**只做调度**） | overflow 恢复 compact **最多 3 次** |
| `agents/pi-embedded-runner/attempt.ts` · `runEmbeddedAttempt` | `openclaw-agents-core` | `com.openclaw.agents.AgentAttemptExecutor`（五步卫生 + hook 注入） | 顺序：`sanitizeSessionHistory → validateProviderTurns → limitHistoryTurns → sanitizeToolUseResultPairing → replaceMessages → before_agent_start` |
| `agents/pi-embedded-runner/subscribe.ts` · `subscribeEmbeddedPiSession` | `openclaw-agents-core` | `com.openclaw.agents.SubscribeState` | 6 字段：`assistantTexts / toolMetas / compactionInFlight / pendingCompactionRetry: int / compactionRetryPromise / unsubscribed` |
| `agents/pi-embedded-runner/runs.ts` · `ACTIVE_EMBEDDED_RUNS` | `openclaw-agents-core` | `com.openclaw.agents.ActiveRunRegistry` | `register / clearIfMatches(==) / queueMessage / abort / waitForEmbeddedPiRunEnd`；`queueMessage` 返回 false 场景：`no_active_run / not_streaming / compacting` |
| `agents/pi-embedded-runner/runs.ts` · `waitForEmbeddedPiRunEnd` | `openclaw-agents-core` | `ActiveRunRegistry.waitForEnd(sessionId, timeoutMs)` | 默认 15000ms；最小 `Math.max(100, timeoutMs)`；超时返回 `false` 不 reject |
| `agents/pi-embedded-runner/runs.ts` · `EMBEDDED_RUN_WAITERS` | `openclaw-agents-core` | `ActiveRunRegistry.waiters: Map<sessionId, Set<Consumer<Boolean>>>` | — |
| `agents/pi-embedded-runner/compaction-timeout.ts` · shouldFlagCompactionTimeout / selectCompactionTimeoutSnapshot | `openclaw-agents-core` | `com.openclaw.agents.compaction.CompactionTimeoutSnapshotSelector` | 优先 pre-compaction 快照 |
| `agents/pi-embedded-runner/sanitize.ts` · sanitizeSessionHistory / sanitizeToolUseResultPairing | `openclaw-context-engine` | `com.openclaw.context.sanitizer.*` | `ToolUseResultRepairReport` 5 字段：`messages / added / droppedDuplicateCount / droppedOrphanCount / moved` |
| `agents/pi-embedded-runner/history-limit.ts` · limitHistoryTurns / getHistoryLimitFromSessionKey | `openclaw-context-engine` | `com.openclaw.context.HistoryTurnLimiter` | dm/group bucketing |

### 4.2 Fallback / FailoverError（对应 M3.4）

| TS 源位置 | Java 目标模块 | Java 类 |
|---|---|---|
| `agents/failover-error.ts` · FailoverError / reason | `openclaw-agents-fallback` | `com.openclaw.agents.fallback.FailoverError` + `FailoverReason` enum |
| `agents/failover-classifier.ts` · coerceToFailoverError | `openclaw-agents-fallback` | `com.openclaw.agents.fallback.FailoverReasonClassifier` |
| `agents/fallback-candidates.ts` · resolveFallbackCandidates | `openclaw-agents-fallback` | `com.openclaw.agents.fallback.FallbackCandidateResolver` |
| `agents/model-fallback-runner.ts` · runWithModelFallback | `openclaw-agents-fallback` | `com.openclaw.agents.fallback.ModelFallbackRunner`（含 `shouldRethrowAbort` 守卫） |
| `agents/auth-profile-rotate.ts` · advanceAuthProfile | `openclaw-agents-fallback` | `com.openclaw.agents.fallback.AuthProfileRotator` |

### 4.3 Session-Lane 并发（对应 M3.3）

| TS 源位置 | Java 目标模块 | Java 类 | 关键常量 / 精确字段 |
|---|---|---|---|
| `process/command-queue.ts` · `LaneState` | `openclaw-session-lanes` | `com.openclaw.lanes.LaneState` | 6 字段：`lane / queue / activeTaskIds / maxConcurrent(默认1) / draining(防重入) / generation(reset 自增)` |
| `process/command-queue.ts` · `QueueEntry` | `openclaw-session-lanes` | `com.openclaw.lanes.QueueEntry` | `warnAfterMs` **默认 2000ms**；`waitedMs >= warnAfterMs` 触发 `onWait` 只告警不取消 |
| `process/command-queue.ts` · `enqueueCommandInLane / drainLane` | `openclaw-session-lanes` | `com.openclaw.lanes.LaneDispatcher` | `if (draining) return` 防重入；失败也 pump |
| `process/command-queue.ts` · `isProbeLane` | `openclaw-session-lanes` | `com.openclaw.lanes.LaneDispatcher.isProbeLane` | 匹配 `"auth-probe:" / "session:probe-"` 静默错误日志 |
| `process/command-queue.ts` · `resetAllLanes` | `openclaw-session-lanes` | `com.openclaw.lanes.LaneDispatcher.resetAll` | `generation++ → activeTaskIds.clear() → drainLane`（SIGUSR1 热重启防脏） |
| `process/command-queue.ts` · `waitForActiveTasks` | `openclaw-session-lanes` | `com.openclaw.lanes.LaneDispatcher.waitForActive` | `POLL_INTERVAL_MS=50`；只等快照；超时 `drained=false` 不 reject |
| `process/command-queue.ts` · `clearCommandLane` | `openclaw-session-lanes` | `com.openclaw.lanes.LaneDispatcher.clearLane` | 只取消未开始任务抛 `CommandLaneClearedException` |
| `agents/pi-embedded-runner/lanes.ts` · `resolveSessionLane / resolveGlobalLane` | `openclaw-session-lanes` | `com.openclaw.lanes.LaneNames` | `session:` 前缀幂等；空回落 `CommandLane.MAIN` |
| `gateway/server-lanes.ts` · `applyGatewayLaneConcurrency / setCommandLaneConcurrency` | `openclaw-session-lanes` | `com.openclaw.lanes.GatewayLaneConcurrencyApplier` | `CommandLane: MAIN / CRON / SUBAGENT`；写完立刻 `drainLane` 实时生效 |
| `routing/session-key.ts` · dm/group bucketing | `openclaw-session-lanes` | `com.openclaw.lanes.SessionKeyResolver` | SPI，首版保留现状 + 扩展点 |

### 4.4 工具系统 + 策略 Pipeline（对应 M3.2）

> **区分**：外层 5 步（`createOpenClawCodingTools` 装配）× 内层 9 步（`applyToolPolicyPipeline` 分层）。详见 [`05-translation-conventions.md §13`](./05-translation-conventions.md)。

**外层 5 步：**

| TS 源位置 | Java 目标模块 | Java 类 | 关键常量 / 精确行为 |
|---|---|---|---|
| `agents/pi-tools.policy.ts` · `applyOwnerOnlyToolPolicy` | `openclaw-tools` | `com.openclaw.tools.policy.OwnerOnlyToolPolicy` | `senderIsOwner` **默认 false**（opt-in） |
| `agents/tool-policy-pipeline.ts` · `applyToolPolicyPipeline` | `openclaw-tools` | `com.openclaw.tools.policy.ToolPolicyPipeline` | 内层 9 步（见下方） |
| `agents/pi-tool-definition-adapter.ts` · `normalizeToolParameters` | `openclaw-tools` | `com.openclaw.tools.schema.ToolParameterNormalizer` | **必须在 Hook 注入之前**；根级 `anyOf/oneOf` 展开 |
| `agents/pi-tools.before-tool-call.ts` · `wrapToolWithBeforeToolCallHook` | `openclaw-tools` | `com.openclaw.tools.hook.BeforeToolCallHookWrapper` | 写入 `BEFORE_TOOL_CALL_WRAPPED` marker property |
| `agents/pi-tools.before-tool-call.ts` · `adjustedParamsByToolCallId` | `openclaw-tools` | `com.openclaw.tools.hook.AdjustedParamsStore` | LRU 上限 `MAX_TRACKED_ADJUSTED_PARAMS`（建议 4096） |
| `agents/pi-tools.before-tool-call.ts` · `consumeAdjustedParamsForToolCall` | `openclaw-tools` | `AdjustedParamsStore.consumeForToolCall(toolCallId)` | after hook 取"改写后参数"而非原参数 |
| `agents/pi-tools.before-tool-call.ts` · `runAfterToolCallHook` | `openclaw-tools` | `com.openclaw.tools.hook.AfterToolCallHookEmitter` | 成功 + 失败两条路径 fire-and-forget |
| `agents/tools/abort-signal-wrapper.ts` · `wrapToolWithAbortSignal` | `openclaw-tools` | `com.openclaw.tools.AbortSignalWrapper` | 仅当 `abortSignal` 非空时包 |

**内层 9 步（`buildDefaultToolPolicyPipelineSteps` + `pi-tools.ts` 追加）：**

| # | Step label（精确字面量） | stripPluginOnlyAllowlist |
|---|---|---|
| 1 | `tools.profile (${profile})` | **true** |
| 2 | `tools.provider-profile (${profile})` | **true** |
| 3 | `group tools.allow` | **true** |
| 4 | `tools.global` | false |
| 5 | `tools.global-provider` | false |
| 6 | `tools.agent (${agentId})` | false（仅 agentId 非空） |
| 7 | `tools.agent-provider (${agentId})` | false（仅 agentId 非空） |
| 8 | `sandbox tools.allow` | false（pipeline 外追加） |
| 9 | `subagent tools.allow` | false（pipeline 外追加） |

**子 Agent 工具：**

| TS 源位置 | Java 目标模块 | Java 类 |
|---|---|---|
| `agents/tools/sessions-spawn-tool.ts` | `openclaw-agents-subagent` | `com.openclaw.agents.subagent.SessionsSpawnTool` |
| `agents/tools/sessions-send-tool.ts` | `openclaw-agents-subagent` | `com.openclaw.agents.subagent.SessionsSendTool` |
| `agents/tools/resolve-subagent-tool-policy.ts` | `openclaw-tools` | `com.openclaw.tools.policy.SubagentPolicyDefaults`（默认 deny 9 工具） |

### 4.5 子 Agent 编排（对应 M3.6）

| TS 源位置 | Java 目标模块 | Java 类 |
|---|---|---|
| `agents/subagent-registry.ts` | `openclaw-agents-subagent` | `com.openclaw.agents.subagent.SubagentRegistry`（内存 + MySQL `oc_subagent_run` 落盘 + 启动恢复 + `agent.wait` 兜底 + 归档清扫） |
| `agents/subagent-announce.ts` | `openclaw-agents-subagent` | `com.openclaw.agents.subagent.SubagentAnnounceFlow`（`waitForSettled` + `NO_REPLY` 静默） |
| `agents/session-key.ts` · isSubagentSessionKey | `openclaw-routing` | `com.openclaw.routing.SessionKey.isSubagent()` |

### 4.6 上下文 + 记忆（对应 M3.5）

| TS 源位置 | Java 目标模块 | Java 类 | 关键常量 / 精确字段 |
|---|---|---|---|
| `agents/context-window-guard.ts` · `resolveContextWindowInfo` | `openclaw-context-engine` | `com.openclaw.context.ContextWindowInfoResolver` | `ContextWindowInfo{tokens, source: model/modelsConfig/agentContextTokens/default}`；`defaultTokens=32000` |
| `agents/context-window-guard.ts` · `evaluateContextWindowGuard` | `openclaw-context-engine` | `com.openclaw.context.ContextWindowGuard` | `HARD_MIN_TOKENS=16000`；`WARN_BELOW_TOKENS=32000` |
| `agents/session-transcript-repair.ts` · `ToolUseRepairReport` | `openclaw-context-engine` | `com.openclaw.context.sanitizer.ToolUseResultRepairReport` | 5 字段：`messages / added / droppedDuplicateCount / droppedOrphanCount / moved` |
| `agents/pi-embedded-runner/run.ts` · overflow recovery 分支 | `openclaw-context-engine` | `com.openclaw.context.OverflowRecoveryChain` | compact **最多 3 次** → truncate → `ContextOverflowUnresolvedException` |
| `memory/backend-config.ts` · `resolveMemoryBackendConfig` | `openclaw-memory` | `com.openclaw.memory.MemoryBackendConfigResolver` | 非 qmd 回落 builtin；qmd 解析失败也回落 |
| `memory/search-manager.ts` · `getMemorySearchManager` + `QMD_MANAGER_CACHE` | `openclaw-memory` | `com.openclaw.memory.MemorySearchManagerFactory` | 缓存 key = `buildQmdCacheKey(agentId, qmd)`；兜底返回 `{manager: null, error}` 不崩 |
| `memory/search-manager.ts` · `FallbackMemoryManager` | `openclaw-memory` | `com.openclaw.memory.FallbackMemoryManager` | **4 字段**：`primaryFailed / fallback / lastError / cacheEvicted`；`evictCacheEntry()` 必须 |
| `agents/tools/memory-tool.ts` · `createMemorySearchTool` | `openclaw-memory` | `com.openclaw.memory.tool.MemorySearchTool` | description 开头：`"Mandatory recall step: ..."`；`maxInjectedChars=4000` clamp；返回 shape `{results, provider, model, fallback, citations}` |
| `agents/tools/memory-tool.ts` · `createMemoryGetTool` | `openclaw-memory` | `com.openclaw.memory.tool.MemoryGetTool` | description 开头：`"Safe snippet read..."` |
| `memory/qmd-manager.ts` · qmd `status()` | `openclaw-memory` | `com.openclaw.memory.qmd.QmdMemoryManager.status()` | 固定返回 `{backend:"qmd", provider:"qmd", model:"qmd", files, vector:{enabled:true, available:true}}` |
| `gateway/server-startup-memory.ts` · `startGatewayMemoryBackend` | `openclaw-memory` | `com.openclaw.memory.MemoryStartupProbe` | 仅 qmd 触发；启动时提前 `probeVectorAvailability` 暴露配置错误 |
| `memory/manager-embedding-ops.ts` | `openclaw-memory` | `com.openclaw.memory.embedding.EmbeddingOps` | `chunkMarkdown / buildEmbeddingBatches / loadEmbeddingCache`；三表同步 `chunks + chunks_vec + chunks_fts` |
| `infra/skills-remote.ts` | `openclaw-skills` | `com.openclaw.skills.remote.RemoteSkillsCache` | 能力变化 `bumpSkillsSnapshotVersion({reason:"remote-node"})` |

### 4.7 审批 / 热重载 / 哨兵（对应 M3.6 + M4.0）

| TS 源位置 | Java 目标模块 | Java 类 | 关键常量 / 精确行为 |
|---|---|---|---|
| `gateway/exec-approval-manager.ts` · `ExecApprovalManager` | `openclaw-approval` | `com.openclaw.approval.ExecApprovalManager` | `RESOLVED_ENTRY_GRACE_MS=15_000`；同 id 未决 `register` 幂等返回同一 Future；同 id 已决 `register` 抛 `ILLEGAL_STATE`；timeout `resolve(null)` 不 reject |
| `gateway/exec-approval-manager.ts` · `Entry / Record` | `openclaw-approval` | `com.openclaw.approval.ExecApprovalRecord` | `id/createdAtMs/expiresAtMs/decision/resolvedAtMs/resolvedBy` |
| `gateway/server-methods/exec-approval.ts` · `request / waitDecision / resolve` | `openclaw-approval` | `com.openclaw.approval.ExecApprovalGatewayMethods` | **先 `register` 再响应 accepted**（race 防御）；`waitDecision` 在 15s grace 内仍可取 decision；decision 只允许 `allow-once / allow-always / deny` |
| `infra/exec-approvals.ts` · `requiresExecApproval` | `openclaw-approval` | `com.openclaw.approval.ExecApprovalPolicy` | `always` / `on-miss + allowlist + (!analysisOk \|\| !allowlistSatisfied)` |
| `gateway/config-reload.ts` · `buildGatewayReloadPlan` | `openclaw-server-runtime-config` | `com.openclaw.server.config.GatewayReloadPlanBuilder` | `ReloadKind: HOT_RELOAD / NEEDS_RESTART` |
| `gateway/config-reload.ts` · `deferGatewayRestartUntilIdle` | `openclaw-server-runtime-config` | `com.openclaw.server.config.GatewayIdleGuard` | 检查 `ActiveRunRegistry` / embedded runs / gateway queues / pending replies / auto-reply chunk queue |
| `gateway/restart-sentinel.ts` · `oc_restart.sentinel` | `openclaw-server-restart-sentinel` | `com.openclaw.server.restart.RestartSentinel` | 基于文件哨兵区分"主动重启 vs 崩溃重启" |

### 4.8 插件具名能力 + 冲突治理（对应 M3.6 + M5.0）

| TS 源位置 | Java 目标模块 | Java 类 | 关键行为 |
|---|---|---|---|
| `plugins/loader.ts` · source priority + realpath dedup | `openclaw-plugins` | `com.openclaw.plugins.loader.PluginSourceResolver` | `CONFIG > WORKSPACE > GLOBAL > BUNDLED`，同 realpath 整体忽略低优先级（禁止部分合并） |
| `plugins/registry.ts` · `registerGatewayMethod / HttpRoute / Command / Tool / MemorySlot` | `openclaw-plugins` | `com.openclaw.plugins.PluginRegistry` | **具名能力冲突硬拒绝**（不得 last-write-wins） |
| `plugins/registry.ts` · `registerHook` | `openclaw-plugins` | `com.openclaw.plugins.PluginRegistry.registerHook` | 唯一允许多注册的注册点 |
| `plugins/registry.ts` · diagnostics | `openclaw-plugins` | `com.openclaw.plugins.PluginRegistry.diagnostics()` | 所有冲突 / loader 错误统一收集 |
| `plugins/hook-runner-global.ts` · `createHookRunner` | `openclaw-hooks-runtime` | `com.openclaw.hooks.HookRunner` | 按 `priority` **降序**；`runVoidHook` 并行 `Promise.all`；`runModifyingHook` 串行 merge；默认 `catchErrors=true` |
| `plugins/hooks.ts` · 内置 hook 点 | `openclaw-hooks-runtime` | `com.openclaw.hooks.HookNames` | `before_agent_start / before_tool_call / after_tool_call / run_agent_end` |

---

## 5. 约定

- **模块前缀统一为 `openclaw-`**，避免与其他项目冲突。
- **包名统一为 `com.openclaw.<module>.<子包>`**。
- **BOM 版本**集中在父 POM `<dependencyManagement>`，子模块只写 `groupId + artifactId`。
- 每个翻译过来的模块都必须具备：`src/main/java`、`src/main/resources`、`src/test/java`，并引入 `spring-boot-starter-test` 最少依赖。
- 翻译时如果原 TS 模块 < 200 行且与另一模块耦合紧密，可**合并成同一个 Java 模块的子包**，避免过度拆分（详见 `05-translation-conventions.md`）。
