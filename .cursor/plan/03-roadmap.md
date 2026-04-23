# 03 · 六阶段开发路线图

本路线图与五阶段学习路线（00–59 教程章节）严格对齐。每个阶段包含：**学习章节 → 翻译任务 → 涉及模块 → 验收产出**。

> 约定：✅ = 必做；🔹 = 可延后；💡 = 可选增强。

---

## 阶段 0 · 工程基建 (M0)

**目标**：在开工写业务前，搭好骨架，避免后续反复调整。

### 任务

- ✅ 初始化 `pom.xml` 聚合父 POM（[`02-maven-modules.md`](./02-maven-modules.md) §4）
- ✅ 创建 `openclaw-common` / `openclaw-logging` / `openclaw-config` / `openclaw-secrets` 四个基础模块（空实现 + 基础契约）
- ✅ 创建 `openclaw-bootstrap` 最小可启动模块（`@SpringBootApplication` + `hello` endpoint）
- ✅ 配置 Maven 插件：compiler、surefire、failsafe、jacoco、spring-boot-maven-plugin
- ✅ 配置 CI 雏形（GitHub Actions 构建任务）🔹
- ✅ 编写根 `README.md` 指向 `.cursor/plan/`
- ✅ 配置 `.gitignore`、`.editorconfig`、`CODESTYLE.md`
- ✅ 搭建 `application.yml` + `application-dev.yml` + `application-test.yml` + `application-prod.yml` 的 profile 结构

### 验收
- `mvn clean package` 通过；`java -jar openclaw-bootstrap/target/*.jar` 能启动并响应 `/actuator/health`

---

## 阶段 1 · 全局认知：CLI + 命令 + 通道骨架 (M1，对应学习章节 00-06)

**目标**：把「进 → 命令 → 消息 → 通道 → 回复」的骨架贯通（允许 mock）。

### 1.1 任务清单

| # | 任务 | 对应学习章节 | 涉及模块 |
|---|---|---|---|
| ✅ | CLI 启动与命令框架（Picocli） | 01 | `openclaw-cli` `openclaw-commands` |
| ✅ | 命令执行与消息发送链路：Command → MessageBus → Channel | 02 | `openclaw-commands` `openclaw-channels-core` |
| ✅ | Gateway 运行时编排骨架（先只做方法分发 + mock 鉴权） | 03 | `openclaw-gateway-api` `openclaw-gateway-core` |
| ✅ | 通道适配器 SPI + 账号生命周期（`ChannelAdapter`/`AccountLifecycle`） | 04 | `openclaw-channels-core` |
| ✅ | 路由与会话键（`RoutingKey`/`SessionKey`） | 05 | `openclaw-routing` `openclaw-sessions` |
| ✅ | 自动回复流水线骨架（先走一条 mock Provider） | 06 | `openclaw-auto-reply` + mock provider |
| ✅ | **Mock Provider** 实现（回显/固定回复），用于打通全链路 | — | `openclaw-providers-api` 的 `tests` |
| ✅ | 最小 **Web 通道** `openclaw-channels-web`：HTTP POST 消息 → Agent → 回复 | — | `openclaw-channels-web` |
| ✅ | `openclaw-bootstrap` 装配上述模块，默认加载 Web 通道 + Mock Provider | — | `openclaw-bootstrap` |

### 1.2 验收
- `curl -X POST /api/channels/web/messages -d '{"text":"hello"}'` 返回 mock Agent 回复
- CLI（命令名 `openclaw-java`，避免与本机 TS 版冲突）：`openclaw-java chat --channel web --text "hi"` 产生相同效果
- 所有模块 JUnit 5 单测 ≥ 60% 覆盖率

---

## 阶段 2 · 模型与扩展 (M2，对应学习章节 07-11)

**目标**：让系统「真的能说话」。

### 2.1 任务清单

| # | 任务 | 章节 | 模块 |
|---|---|---|---|
| ✅ | Provider SPI 完整定义（含流式事件） | 07 | `openclaw-providers-api` |
| ✅ | **Google Gemini Provider**（首期必做） | 07 | `openclaw-providers-google` |
| ✅ | **Qwen Provider**（首期必做） | 07 | `openclaw-providers-qwen` |
| ✅ | Provider Registry + auth-profile 池 + cooldown + 回退 | 07 | `openclaw-providers-registry` |
| 🔹 | OpenAI / Anthropic / Copilot Provider（暂不做，按需扩展） | 07 | — |
| ✅ | 插件 SPI（`Plugin`/`PluginContext`） | 08 | `openclaw-plugin-sdk` |
| ✅ | 插件加载器（ServiceLoader + Spring 容器） | 08 | `openclaw-plugins` |
| ✅ | 配置、会话与存储（**MyBatis-Plus + MySQL + Flyway**） | 09 | `openclaw-config` `openclaw-sessions` |
| ⛔ | 媒体管线骨架（图/音/文件） | 10 | 暂不建立（仅文本） |
| ⛔ | 链接理解 / 媒体理解 | 10 | 暂不建立（仅文本） |
| 💡 | 复刻思路回顾（用 Java 再审一遍架构） | 11 | — |

### 2.2 验收
- 真实 OpenAI/Anthropic 能一问一答（经 mock key 接入 `mockito-wiremock`）
- 会话持久化：重启后能读到历史
- 插件系统能动态加载一个 demo 插件

---

## 阶段 3 · 智能体框架 (M3，对应学习章节 12-26 + OpenClaw 官方文档 07–15)

**目标**：复刻 `runEmbeddedPiAgent` 及其生态 —— 执行链路四层分离（`run/attempt/subscribe/runs`）、双层 Lane、FailoverError、上下文卫生、Hook Runner、Skills snapshot、Memory 双后端 fallback、Approval 状态机、子 Agent 编排与防递归。

### 3.1 任务清单（M3 拆为 6 子里程碑，详见 [`04-milestones.md §M3`](./04-milestones.md)）

| # | 子里程碑 | 章节 | 模块 |
|---|---|---|---|
| ✅ | M3.1 事件模型 + 四层分离骨架（`PiAgentRunner` 仅调度 / `AttemptExecutor` 单事务 / `SubscribeState` 事件聚合 / `ActiveRunRegistry`） | 07 / 12 / 16 / 21 / 22 / 23 | `openclaw-agents-core` `openclaw-stream` |
| ✅ | M3.2 工具系统 + 策略 pipeline（owner→profile→provider-profile→global→agent→group→sandbox→subagent） + before/after hook | 08 / 14 / 24 | `openclaw-tools` |
| ✅ | M3.3 双层 Lane 并发模型（sessionLane + globalLane × Cron/Main/Subagent） | 15 | `openclaw-session-lanes` |
| ✅ | M3.4 PiAgent 主链路 + AttemptExecutor 五步卫生 + `FailoverError` 分类回退 | 07 / 13 / 19 / 25 | `openclaw-agents-core` `openclaw-agents-fallback` `openclaw-providers-registry` |
| ✅ | M3.5 上下文引擎 + 记忆系统（`ContextWindowGuard` + `tool_use/tool_result pairing` + `FallbackMemoryManager` + `citations` policy） | 10 / 26 | `openclaw-context-engine` `openclaw-memory` |
| ✅ | M3.6 Skills snapshot + 子 Agent 编排 + Approval 状态机 + Hook Runner 升级 + 插件具名能力冲突治理 | 17 / 18 / 52 + 文档 15 | `openclaw-skills` `openclaw-agents-subagent` `openclaw-approval` `openclaw-hooks-runtime` `openclaw-plugins` |

### 3.2 验收

- 执行一个含工具调用的对话，能正确触发 tool → 回调 → 继续生成（含 `clock.now` + 子 Agent spawn + approval 三个场景）
- 并发 10 个 session，每个 session 内串行、跨 session 并行（产出 p50/p95/p99 报告）
- 失败注入：模拟 Provider 500 / 429 / 401，分别进入 `TIMEOUT/RATE_LIMIT/AUTH` 分类；402 billing 不回退直接抛；用户 abort 不被误降级
- 长会话 100 轮稳定无孤儿 `tool_result`；memory primary 故障后自愈切 builtin
- SKILL.md 改动触发下一轮 snapshot 刷新；插件具名能力冲突产出 diagnostics
- 核心 Agent 单元测试覆盖率 ≥ 80%

---

## 阶段 4 · Gateway 控制平面 (M4，对应学习章节 27-42 + OpenClaw 官方文档 05)

**目标**：把控制面 API 全部立起来，能被 UI / SDK 使用；热重载与重启分类明确化，接入 Idle Guard + Restart Sentinel。

### 4.1 任务清单

| # | 任务 | 章节 | 模块 |
|---|---|---|---|
| ✅ | Gateway 总览 + 协议帧定义 + Schema 验证 | 27 / 42 | `openclaw-gateway-api` |
| ✅ | WS 握手 & `connect` 鉴权 | 28 | `openclaw-gateway-ws` |
| ✅ | 方法鉴权与请求分发 | 29 | `openclaw-gateway-core` |
| ✅ | `chat.*` 方法组 | 30 | `openclaw-gateway-core` |
| ✅ | `send.*` / `agent.*` 方法组 | 31 | `openclaw-gateway-core` |
| ✅ | `sessions.*` 方法组 | 32 | `openclaw-gateway-core` |
| ✅ | `node.*` / `device.*` 方法组 | 33 | `openclaw-gateway-core` |
| ✅ | `config.*` / `skills.*` / `update.*` / `approval.*` 方法组 | 34 | `openclaw-gateway-core` |
| ✅ | 运维方法组（metrics、logs、reload 等） | 35 | `openclaw-gateway-core` |
| ✅ | HTTP 路由与安全网（CORS、CSRF、限流） | 36 | `openclaw-gateway-http` |
| ✅ | OpenAI 兼容 `/v1/chat/completions`（流式 SSE，仅文本） | 37 | `openclaw-gateway-openai-compat` |
| ⛔ | OpenResponses 端点 + 多模态 | 38 | 暂不建立（仅文本范围） |
| ✅ | 配置热重载 **+ reload 分类**（`HOT_RELOAD`/`NEEDS_RESTART` + `buildGatewayReloadPlan` + `GatewayIdleGuard` 空闲窗口兜底） | 39 + 文档 05 | `openclaw-server-runtime-config` |
| ✅ | 启动侧车 + **重启哨兵**（`RestartSentinel` 区分主动重启/崩溃） + 优雅关停 | 40 + 文档 05 | `openclaw-bootstrap` `openclaw-server-restart-sentinel` |
| ✅ | `hooks` & `tools-invoke` HTTP 入口 | 41 | `openclaw-gateway-hooks`（调用 `openclaw-hooks-runtime`） |

### 4.2 验收
- WS 端点通过 `wscat`/`websocat` 完成完整握手 → 方法调用 → 流式响应
- `/v1/chat/completions` 能被 `openai` 官方 SDK 调通
- OpenAPI 文档在 `/swagger-ui.html` 可见
- 热重载配置文件，服务不重启完成参数切换；变更特定 key 触发优雅重启

---

## 阶段 5 · 复刻与补齐 (M5，对应学习章节 43-59)

**目标**：接入真实通道、补齐控制面运行时、达到准生产状态。

### 5.1 运行时支撑

| # | 任务 | 章节 | 模块 |
|---|---|---|---|
| ✅ | `server-runtime-config` Java 版 | 45 | `openclaw-server-runtime-config` |
| ✅ | `server-tailscale`（侧车子进程） | 46 | `openclaw-server-tailscale` |
| ✅ | `server-discovery` | 47 | `openclaw-server-discovery` |
| ✅ | `server-lanes` | 48 | `openclaw-server-lanes` |
| ✅ | `server-model-catalog` | 49 | `openclaw-server-model-catalog` |
| ✅ | `server-session-key` | 50 | `openclaw-server-session-key` |
| ✅ | `server-startup-memory` | 51 | `openclaw-server-startup-memory` |
| ✅ | `exec-approval-manager`（补齐 UI/流程） | 52 | `openclaw-approval` |
| ✅ | 通道适配器框架补齐（53）+ 通道实现索引（59） | 53 / 59 | `openclaw-channels-core` + 各通道 |

### 5.2 生态模块

| # | 任务 | 章节 | 模块 |
|---|---|---|---|
| ✅ | 安全模块与审计系统 | 54 | `openclaw-security` |
| ✅ | **定时任务系统（ElasticJob-Lite + ZooKeeper）** | 55 | `openclaw-cron` |
| ✅ | 守护进程管理（`ProcessBuilder` + `ProcessHandle`） | 56 | `openclaw-daemon` |
| 🔹 | 浏览器自动化集成（**Playwright for Java**） | 57 | `openclaw-browser` |
| ⛔ | 语音合成系统 | 58 | 暂不建立（仅文本） |
| ✅ | **多插件冲突治理**（源优先级 `config > workspace > global > bundled` + 具名能力硬拒绝 + `diagnostics` 聚合） | 文档 15 | `openclaw-plugins` |

### 5.3 通道落地矩阵

| 优先级 | 通道 | 模块 | 状态 |
|---|---|---|---|
| P0 | Web（HTTP + WebSocket） | `openclaw-channels-web` | ✅ 必做，**唯一实现** |
| — | Telegram / Discord / Slack / Line / WhatsApp / Signal / iMessage | — | ⛔ 暂不建立 |

### 5.4 验收
- Web 通道接入并完成端到端对话（HTTP + WebSocket 双通道）
- 定时任务（ElasticJob）、守护进程能稳定运行
- 审计日志可查询、鉴权/限流全生效
- 所有模块 JaCoCo 覆盖率 ≥ 60%，核心 ≥ 80%

---

## 阶段 6 · 生产化（可选，M6）

- 💡 GraalVM Native Image 打包
- 💡 Helm Chart + K8s Operator
- 💡 OpenTelemetry 全链路追踪
- 💡 分布式部署（Redis 共享会话 / 消息队列）
- 💡 `packages/clawdbot`、`packages/moltbot` 两个子包的 Java 版本
- 💡 `apps/`（移动端/桌面端）仅保留 API，不翻译

---

## 进度追踪

- 使用根目录的 `TODO.md`（或 Linear/Jira）为每个 ✅ 任务建卡
- 每完成一个子任务在 [`04-milestones.md`](./04-milestones.md) 勾选
- 每周回顾：若某模块实现偏离原语义 > 20%，写入 `docs/translation-notes/` 说明取舍
