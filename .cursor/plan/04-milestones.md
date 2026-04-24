# 04 · 里程碑与验收

---

## M0 · 工程基建 ✅

- [x] 父 POM：`com.openclaw:openclaw-java-parent:0.1.0-SNAPSHOT`，`packaging=pom`；锁定 `java.version=21` / `maven.compiler.release=21` / `project.build.sourceEncoding=UTF-8`
- [x] BOM 导入：`spring-boot-dependencies:3.3.4` + `testcontainers-bom:1.20.3`（`<scope>import</scope>`）
- [x] 版本锁矩阵（`dependencyManagement`）：`fastjson2 2.0.53` / `mybatis-plus 3.5.9` / `mysql-connector-j 9.0.0` / `flyway 10.20.0` / `elasticjob 3.0.4` / `picocli 4.7.6` / `springdoc 2.6.0` / `knife4j 4.5.0` / `playwright 1.48.0` / `json-schema-validator 1.5.2` / `caffeine 3.1.8` / `bucket4j-core 8.10.1` / `jjwt 0.12.6` / `logstash-logback-encoder 8.0` / `commonmark 0.22.0` / `wiremock 3.9.2` / `assertj 3.26.3`；同时为全部 `com.openclaw:*` 内部模块（18 个）在 `dependencyManagement` 下集中声明 `${project.version}`
- [x] 插件锁（`pluginManagement`）：`maven-compiler-plugin 3.13.0`（`release=21` + `parameters=true`）/ `maven-surefire-plugin 3.5.1`（`-Xshare:off` 适配 JaCoCo agent）/ `maven-failsafe-plugin 3.5.1` / `maven-jar-plugin 3.4.2` / `spring-boot-maven-plugin 3.3.4` / `jacoco-maven-plugin 0.8.12`（`prepare-agent` + `verify` 阶段 `report`）
- [x] 模块骨架登记：`openclaw-common` / `openclaw-logging` / `openclaw-config` / `openclaw-secrets` 按 M0 归类注册到父 POM `<modules>`
- [x] `openclaw-common`：`error/ErrorCode` SPI + `error/CommonErrorCode`（7 个枚举值：`INTERNAL_ERROR` / `ILLEGAL_ARGUMENT` / `ILLEGAL_STATE` / `NOT_FOUND` / `UNSUPPORTED` / `JSON_SERIALIZE` / `JSON_DESERIALIZE`）+ `error/OpenClawException`（携带 `errorCode` + 不可变 `context` Map 用于结构化日志）+ `model/Result<T>`（统一返回包装）+ `json/JsonCodec`（Fastjson2 适配器）+ `util/Strings`（`defaultIfBlank` 等纯工具）
- [x] `openclaw-logging`：`MdcKeys`（固定 key 常量）+ `MdcScope`（`AutoCloseable`，try-with-resources 入场→出场恢复；支持 fluent `with(key,val)` 链式追加）+ 共享 `openclaw-logback-base.xml`（可被子模块 `<include>`）
- [x] `openclaw-config`：`OpenClawProperties`（`openclaw.profile` / `node-name` / `startup-timeout`）+ `OpenClawConfigAutoConfiguration`（`@AutoConfiguration` + `@EnableConfigurationProperties`）
- [x] `openclaw-secrets` 骨架：`SecretResolver` SPI + `EnvSecretResolver`（读环境变量）+ `InMemorySecretResolver`（测试用）+ `CompositeSecretResolver`（按顺序回落）+ `OpenClawSecretsAutoConfiguration`
- [x] `openclaw-bootstrap`：`OpenClawApplication`（`@SpringBootApplication`）+ `web/FastJson2WebMvcConfiguration`（注册 `FastJsonHttpMessageConverter` 作为首选 JSON 编解码器）+ `web/HelloController`（`GET /hello` 冒烟端点）+ `application.yml` / `application-dev.yml` / `application-prod.yml` / `application-test.yml` / `logback-spring.xml` / `logback-cli.xml`
- [x] Actuator：`management.endpoints.web.exposure.include=health,info,metrics` + `health.probes.enabled=true` + `show-details=when-authorized`；`mvn spring-boot:run` 启动后 `/actuator/health` 返回 `UP`
- [x] 根文档：`README.md`（项目简介 / 模块地图 / 快速开始）+ `CODESTYLE.md`（命名约定 / 构造注入 / 无字段注入 / 无裸 `RuntimeException`）
- [x] 单测：`openclaw-common` 下 `JsonCodecTest`（序列化 / 反序列化 / 异常映射）；`openclaw-logging` 下 `MdcScopeTest`（嵌套 scope + 恢复语义）；`openclaw-secrets` 下 `CompositeSecretResolverTest`（回落顺序）；`openclaw-bootstrap` 下 `OpenClawApplicationTest`（`@SpringBootTest` 上下文可启动）

**交付物**：可运行的空壳 + 父 POM

---

## M1 · 全局认知（对应学习 00–06）✅

- [x] `openclaw-cli`：`picocli 4.7.6` + `picocli-spring-boot-starter`；`RootCommand`（命令名 **`openclaw-java`**，避开本机原生 `openclaw` CLI 冲突）+ 子命令 `ChatCommand`（`openclaw-java chat --text "..."`，调用 `ChatCommandService`）+ `ChannelsCommand`（`openclaw-java channels`，列出 `ChannelRegistry` 已注册通道）
- [x] `openclaw-cli`：`OpenClawCliRunner` 实现 `ApplicationRunner` + `ExitCodeGenerator`，受 `openclaw.cli.enabled` 控制；当启用时执行 Picocli 后以 `context.close()` 主动优雅退出，避免 Tomcat 常驻；`OpenClawCliAutoConfiguration` 注册 `RootCommand` / 子命令 / `OpenClawCliRunner` beans（使用 picocli-spring-boot-starter 提供的 `IFactory`）
- [x] `openclaw-cli`：`bin/openclaw-java` 启动脚本（设置 `openclaw.cli.enabled=true` + 指向 `logback-cli.xml` 静默 banner/INFO）；`logback-cli.xml` 仅保留 `WARN` 以上到 stderr，CLI 输出纯净
- [x] `openclaw-commands`：`ChatCommandService`（内存命令注册表 + 默认 `chat` / `channels list` / `ping` 命令封装）+ `OpenClawCommandsAutoConfiguration`（无条件装配）
- [x] `openclaw-channels-core`：`ChannelAdapter` SPI（`channelId` / `send(OutboundMessage)` / `onReceive(Consumer<InboundMessage>)`）+ `AccountLifecycle`（账号挂载/离线钩子）+ `ChannelRegistry`（按 `channelId` 去重注册；重复抛 `OpenClawException(ILLEGAL_STATE)`）+ `InboundMessage` / `OutboundMessage` record + `OpenClawChannelsCoreAutoConfiguration`
- [x] `openclaw-channels-web`：`WebChannelAdapter`（`channelId="web"`，内存队列派发）+ `WebChannelController`（`POST /api/channels/web/messages` 阻塞回复；**M2.2 追加** `POST /api/channels/web/messages/stream` SSE，含 `delta`/`tool_call`/`done`/`error` 四种事件）+ 请求校验 `@Validated` + `@NotBlank text`；默认 `channelId/accountId/conversationId` 回落使用 `Strings.defaultIfBlank`
- [x] `openclaw-channels-web`：`GatewayHttpController`（`POST /api/gateway` 单包信封分发到 `MethodDispatcher`，为 M4 的 WS 栈准备）+ `OpenClawChannelsWebAutoConfiguration`
- [x] `openclaw-routing`：`RoutingKey`（`channelAccount + conversationId`，带 `toSessionKey()` 反射为会话键）+ `ChannelAccount`（`channelId + accountId` 复合键）
- [x] `openclaw-sessions`（M1 基线版，M2.1 增强为 JDBC）：`SessionKey`（`channel:account:conversation` 三段式）+ `Session`（`messages: List<ChatMessage>` + `append()`）+ `SessionRepository` SPI + `InMemorySessionRepository`（`ConcurrentHashMap` 存储 + `loadOrCreate` 语义）+ `OpenClawSessionsAutoConfiguration`（默认 store=memory）
- [x] `openclaw-auto-reply`：`AutoReplyPipeline.handle(InboundMessage)`：`MdcScope` 注入 `channel/sessionId/provider` → `session.loadOrCreate` → `append(user)` → `provider.chat(ChatRequest)` → `append(assistant)` → `sessions.save(session)` → `OutboundMessage.replyTo(inbound, reply)`；配套 `auto-reply.inbound` / `auto-reply.outbound` 结构化日志含 `elapsedMs / finishReason`
- [x] `openclaw-auto-reply`：`OpenClawAutoReplyAutoConfiguration` 装配 `AutoReplyPipeline` bean；M1 默认依赖 Mock `EchoProviderClient`（`ProviderClient` 空实现返回 `"[mock] " + prompt`），M2.2 切换到真实 Qwen 后此 fallback 自动让位
- [x] `openclaw-gateway-api`：`GatewayRequest`（`id/method/authToken/params`）+ `GatewayResponse`（`id/success/result/errorCode/errorMessage` + `success(id, result)` / `failure(id, code, msg)` 静态工厂）+ `Methods` 常量（`chat.send` / `chat.history` / `channels.list` / `node.ping`）
- [x] `openclaw-gateway-core`：`MethodHandler` SPI（`method()` + `handle(GatewayRequest): Map<String,Object>`）+ `MethodDispatcher`（构造时按 `method()` 去重建表，重复抛 `ILLEGAL_STATE`；分发用 `MdcScope` 注入 `requestId`；未知方法返回 `NOT_FOUND`；`OpenClawException` 按 `errorCode` 序列化；其余异常降级 `INTERNAL_ERROR`）+ `AuthGuard` SPI + `MockAuthGuard`（配置 `openclaw.gateway.auth-token` 为空时 permissive，非空时等值比对；无效 token 抛 `ILLEGAL_ARGUMENT`）
- [x] `openclaw-gateway-core`：4 个内置方法 `methods/PingMethodHandler`（`node.ping` 返回 `nodeName + upSince`）/ `ChannelsListMethodHandler`（`channels.list` 从 `ChannelRegistry` 取）/ `ChatSendMethodHandler`（`chat.send` 走 `AutoReplyPipeline`）/ `ChatHistoryMethodHandler`（`chat.history` 从 `SessionRepository` 取最后 N 条）+ `OpenClawGatewayCoreAutoConfiguration`
- [x] `openclaw-bootstrap`：`application.yml` 汇总 `openclaw.node-name / cli.enabled / gateway.auth-token / sessions.store` 等；多 profile（`dev` 内存 + 排除 `DataSourceAutoConfiguration`；`prod`/`test` 默认 + 连接串走环境变量）
- [x] 端到端冒烟：`curl -XPOST http://localhost:8080/api/channels/web/messages -d '{"accountId":"u1","conversationId":"c1","text":"hi"}'` 返回 `[mock] hi`；启用 `OPENCLAW_QWEN_ENABLED=true` + `DASHSCOPE_API_KEY=...` 后返回真实模型回复（已在对话联调记录）
- [x] 单测：`InMemorySessionRepositoryTest`（`loadOrCreate/append/save` 幂等）× 多例；`RoutingKeyTest`（`toSessionKey` 映射）；`ChannelRegistryTest`（注册/去重/查询）；`MethodDispatcherTest`（4 方法分派 + 未知方法 + 鉴权失败 + 业务异常降级）；`ChatCommandServiceTest`（默认命令 happy path）；`AutoReplyPipelineTest`（M2.2 起新增 streamHandle 用例；M2.4 追加 `ChatCommand` 短路用例）

**交付物**：可完成「Web 通道发消息 → Mock Agent → 回复」（并已联调到真实 Qwen）

---

## M2 · 模型与扩展（对应学习 07–11）

### M2.1 · 会话持久化 ✅
- [x] `openclaw-sessions` pom 升级：MyBatis-Plus / MySQL / Flyway / Caffeine 以 `<optional>true</optional>` 引入
- [x] Flyway `V1__sessions.sql`：`oc_session` + `oc_message`（append-only，`(session_id, seq)` 唯一键）
- [x] `jdbc` 子包：`SessionEntity` `MessageEntity` `SessionMapper` `MessageMapper` `JdbcSessionRepository`
- [x] 增量追写：`save(Session)` 只 insert `seq >= dbCount` 的部分；Caffeine 缓存 `sessionKey → sessionId`
- [x] `OpenClawSessionsJdbcAutoConfiguration`：`@ConditionalOnProperty(openclaw.sessions.store=jdbc)` + `@AutoConfigureBefore(内存版)`
- [x] Testcontainers-MySQL `JdbcSessionRepositoryIT`：persist/restore/incremental-append/delete-cascade
- [x] `openclaw-bootstrap`：显式引入 MyBatis-Plus/MySQL/Flyway，dev profile 默认 `store=jdbc`，test profile 排除 DataSource autoconfig

### M2.2 · Provider SPI 流式化 + Gemini Provider + Registry ✅
- [x] `openclaw-providers-api`：`ChatChunkEvent`（sealed：Delta / ToolCall / Done / Error）/ `ToolCallChunk` / `AuthProfile` / `CooldownPolicy`（含指数退避 `delayForAttempt`）
- [x] `ProviderClient.streamChat`：`default Flux<ChatChunkEvent>` 默认实现包装阻塞 `chat()`
- [x] `openclaw-providers-qwen` 增补流式 SSE：`WebClient` + `ServerSentEvent` + Fastjson2 解 `data:` 行（含 tool_calls 增量）
- [x] `openclaw-providers-google`（新模块）：Gemini 非流式 `generateContent` + 流式 `streamGenerateContent?alt=sse`，`x-goog-api-key` 头部鉴权，system → `systemInstruction`
- [x] `openclaw-providers-registry`（新模块）：`DefaultProviderRegistry` + `CompositeProviderClient`（@Primary）+ 指数退避 cooldown + 失败回退
- [x] `openclaw-auto-reply`：`AutoReplyPipeline.streamHandle(InboundMessage)` → `Flux<ChatChunkEvent>`，流结束后把完整 assistant 消息落会话
- [x] `openclaw-channels-web`：`POST /api/channels/web/messages/stream` SSE 端点（event: delta / tool_call / done / error）
- [x] 单测覆盖：providers-api（流式默认适配 + Cooldown 指数退避）/ Qwen（SSE delta + tool_calls + finish_reason）/ Gemini（阻塞 + 流式 + role 映射 + safety）/ Registry（排序 + cooldown + 回退 + 回恢复）

### M2.3 · 凭据持久化 ✅
- [x] `openclaw-secrets` 新增 `crypto/EnvelopeCipher`（AES-256-GCM 双层 KEK/DEK）+ `SecretsCryptoProperties`（`openclaw.secrets.crypto.kek-base64`）
- [x] `openclaw-secrets` 新增 `vault/AuthProfileVault` SPI + `InMemoryAuthProfileVault`（作为基础 autoconfig 的默认 bean）
- [x] `openclaw-secrets` JDBC 栈：`AuthProfileEntity` + `AuthProfileMapper` + `JdbcAuthProfileVault`（MyBatis-Plus；save/find/listByProvider/delete；extras 明文 JSON，apiKey envelope 加密）
- [x] Flyway `V2__secrets.sql`：`oc_auth_profile`（`provider_id, profile_id` 唯一键，`data_ct/data_iv/dek_ct/dek_iv` varbinary 列）
- [x] `OpenClawSecretsJdbcAutoConfiguration`：`@ConditionalOnProperty(openclaw.secrets.store=jdbc)` + `@ConditionalOnClass(BaseMapper/DataSource)`，当 store=jdbc 且 KEK 配置就绪时产出 `EnvelopeCipher` + `JdbcAuthProfileVault` beans
- [x] `openclaw-providers-registry`：`ProviderRegistry.authProfile(providerId)` 默认方法 + `DefaultProviderRegistry` 持有可选 `AuthProfileVault`；autoconfig 用 `ObjectProvider<AuthProfileVault>` 懒注入
- [x] Qwen / Google autoconfig：新增 `qwenApiKey` / `googleApiKey` bean 作为单次 vault→properties 解析点；Qwen RestClient defaultHeader 注入 Bearer（移除 per-request header 覆盖）；Gemini 新增 `GeminiProviderClient(props, rc, wc, apiKey)` 构造器解耦 apiKey 与 properties
- [x] 单测：`EnvelopeCipherTest`（round-trip / tamper-detect / wrong-KEK / 非法 KEK）× 6，`InMemoryAuthProfileVaultTest` × 3，`DefaultProviderRegistryTest` 新增 vault 用例 × 2
- [x] 集成测：`JdbcAuthProfileVaultIT`（Testcontainers-MySQL，插入→读→解密→更新→删除）

### M2.4 · 插件体系 ✅
- [x] `openclaw-plugin-sdk`：纯 API 模块（不依赖 Spring Boot），含 `OpenClawPlugin` SPI（`id/version/description/order/onLoad/onUnload`）、`PluginContext`（`registerSingleton/publishEvent/environment/beanFactory`）、`PluginDescriptor`（loaded 快照）、`PluginLoadException`
- [x] `openclaw-plugins`：`PluginLoader`（`ServiceLoader<OpenClawPlugin>` + include/exclude/fail-fast 过滤 + order 排序 + `ContextRefreshedEvent` 触发 + `DisposableBean` 反序 `onUnload`）、`DefaultPluginContext`（bean 名自动前缀 `plugin.<id>.`）、`PluginRegistry` 查询 API、`PluginProperties`（`openclaw.plugins.*`）、`OpenClawPluginsAutoConfiguration`
- [x] 内置 demo：`com.openclaw.plugins.demo.HelloPlugin`（通过 `META-INF/services/com.openclaw.plugin.OpenClawPlugin` 注册）在 `onLoad` 打印启动日志并注册 `HelloGreeter` bean（`plugin.hello.greeter`）
- [x] `openclaw-config` 新增 `hotreload/HotReloadable` SPI + `ConfigChangeEvent` + `ConfigReloadPublisher`（fan-out 到 `HotReloadable` beans 再透传 `ApplicationEventPublisher`）
- [x] `openclaw-config` 新增 `ConfigWatcher`：基于 `WatchService.poll(debounce)` 的单线程 daemon，按父目录注册 + 文件名过滤 + 纳秒级 debounce；`@ConditionalOnProperty(openclaw.config.hot-reload.enabled=true)` 门控，关闭时零 FD / 零线程
- [x] `HotReloadProperties`（`enabled/paths/debounce`）；`OpenClawConfigAutoConfiguration` 内嵌 `HotReloadConfiguration` 隔离 watcher beans，避免未开启时产生多余 bean
- [x] bootstrap：`application.yml` 新增 `openclaw.plugins.*` + `openclaw.config.hot-reload.*` 示例；`openclaw-bootstrap/pom.xml` 引入 `openclaw-plugins`
- [x] 单测：`PluginLoaderTest`（`ApplicationContextRunner` 避开 Mockito self-attach；覆盖 discover/register bean/exclude/enabled=false 共 4 用例）、`ConfigWatcherTest`（真实 `WatchService` + `@TempDir` + 手写 poll 等待，覆盖 modify→通知 / 事件外发 / disabled 静默 共 3 用例）

**交付物**：接入真实 Provider（Gemini 或 Qwen）的一次问答，会话写入 MySQL

---

## M3 · 智能体框架（对应学习 12–26 + OpenClaw 文档 07–15 + **实战 16–21**）

> 按 OpenClaw 官方文档"执行链路四层分离（`run / attempt / subscribe / runs`）+ 双层 Lane + FailoverError + 上下文卫生 + Hook Runner + Skills snapshot + Memory 双后端 fallback"的硬要求拆分为 6 个子节。每一节独立可交付、独立可验收。
>
> 依赖顺序：M3.1（地基） → M3.2（工具 pipeline） → M3.3（Lane） → M3.4（主链路 + 回退） → M3.5（上下文 + 记忆） → M3.6（Skills + 子 Agent + 审批 + Hook Runner 升级）。

### M3 · 实战源文档索引（字段级对照基准）

| 子节 | 原理文档（7–15） | 实战文档（16–21，**字段/常量/精确顺序**） |
|---|---|---|
| M3.1 执行状态机 | 07 / 12 | [17 · 智能体执行状态机实现实战](https://openclaw-docs.dx3n.cn/beginner-openclaw-framework-focus/17-%E6%99%BA%E8%83%BD%E4%BD%93%E6%89%A7%E8%A1%8C%E7%8A%B6%E6%80%81%E6%9C%BA%E5%AE%9E%E7%8E%B0%E5%AE%9E%E6%88%98) |
| M3.2 工具策略 + 审批 | 08 / 09 | [18 · 工具策略与审批状态机实现实战](https://openclaw-docs.dx3n.cn/beginner-openclaw-framework-focus/18-%E5%B7%A5%E5%85%B7%E7%AD%96%E7%95%A5%E4%B8%8E%E5%AE%A1%E6%89%B9%E7%8A%B6%E6%80%81%E6%9C%BA%E5%AE%9E%E7%8E%B0%E5%AE%9E%E6%88%98) |
| M3.3 并发 Lane | 03 | [21 · 并发队列与 Lane 状态机实现实战](https://openclaw-docs.dx3n.cn/beginner-openclaw-framework-focus/21-%E5%B9%B6%E5%8F%91%E9%98%9F%E5%88%97%E4%B8%8ELane%E7%8A%B6%E6%80%81%E6%9C%BA%E5%AE%9E%E7%8E%B0%E5%AE%9E%E6%88%98) |
| M3.5 上下文 + 记忆 | 08 / 11 | [16 · 上下文管理实现实战](https://openclaw-docs.dx3n.cn/beginner-openclaw-framework-focus/16-%E4%B8%8A%E4%B8%8B%E6%96%87%E7%AE%A1%E7%90%86%E5%AE%9E%E7%8E%B0%E5%AE%9E%E6%88%98) + [19 · AI 记忆系统状态机实现实战](https://openclaw-docs.dx3n.cn/beginner-openclaw-framework-focus/19-AI%E8%AE%B0%E5%BF%86%E7%B3%BB%E7%BB%9F%E7%8A%B6%E6%80%81%E6%9C%BA%E5%AE%9E%E7%8E%B0%E5%AE%9E%E6%88%98) |
| M3.6 Hook + 审批 + 子 Agent | 13 | [20 · Hook 插件注入状态机实现实战](https://openclaw-docs.dx3n.cn/beginner-openclaw-framework-focus/20-Hook%E6%8F%92%E4%BB%B6%E6%B3%A8%E5%85%A5%E7%8A%B6%E6%80%81%E6%9C%BA%E5%AE%9E%E7%8E%B0%E5%AE%9E%E6%88%98) + 18（审批部分） |

### M3 · 执行状态机总图（照抄自实战 17）

```
idle
 → queued(session lane)
 → queued(global lane)
 → attempting
 → streaming
 → compacting (optional)
 → completed | failed
 → idle

补充控制态：
  aborting                       (用户/超时)
  waiting_compaction_retry       (订阅层等压缩重试结束)
```

所有 M3.1–M3.4 的代码行为必须能对应到上述状态之一，禁止出现"游离态"。

### M3.1 · 事件模型 + 四层分离骨架

- [ ] `openclaw-stream`：`AgentEvent` sealed 类型体系（`TurnStart / AssistantDelta / ToolCallRequested / ToolCallResult / ToolCallError / CompactionStart / CompactionEnd / TurnEnd / RunEnd / RunError`）+ `AgentEventSink`（Reactor `Sinks.Many` 封装，支持多订阅者 + 背压）
- [ ] `openclaw-stream`：`ProviderChunkToAgentEvent` 翻译器：把 provider 层的 `ChatChunkEvent`（Delta/ToolCall/Done/Error）翻译成稳定的 `AgentEvent`，聚合工具调用片段为完整 `ToolCallRequested`
- [ ] `openclaw-agents-core`：`AgentRun`（runId + sessionKey + requestId + createdAt + status）+ `AgentRunContext`（scope 内共享字段：messages snapshot / auth profile / model ref / abortSignal）+ `AgentScope`（执行域抽象，后续 attempt 在同一 scope 内运行）
- [ ] `openclaw-agents-core`：`ActiveRunRegistry`（对应 `src/agents/pi-embedded-runner/runs.ts`）：
    - `register(sessionId, handle)` → 内部 `ACTIVE_EMBEDDED_RUNS.set(...)`；日志区分 `run_started` vs `run_replaced`
    - `clearIfMatches(sessionId, handle)`：**必须** handle `==` 校验才删除，防竞态误删新 run
    - `queueMessage(sessionId, msg)` → `boolean` 返回：`false` 的三种场景需日志打 `reason=no_active_run / not_streaming / compacting`
    - `abort(sessionId)` → 调用 `handle.abort()`
    - `waitForEmbeddedPiRunEnd(sessionId, timeoutMs)`：默认超时 **15000ms**，最小 `Math.max(100, timeoutMs)`；超时返回 `false` 不抛异常；内部维护 `EMBEDDED_RUN_WAITERS: Map<sessionId, Set<Consumer<Boolean>>>`
    - `resolveSessionKeyForRun(runId)` 反查
- [ ] `openclaw-agents-core`：`PiAgentRunner`（对应 `runEmbeddedPiAgent`）**仅做调度**：创建 `AgentRun` → 入 lane → attempt 调用 → failover → finally 清理 active run；不做任何 provider 调用细节
- [ ] `openclaw-agents-core`：`AgentAttemptExecutor`（对应 `runEmbeddedAttempt`）**单次事务**：构建 system prompt → 建 session scope → 挂 subscribe → 发 prompt → 等压缩 → 收尾 hook → finally unsubscribe
- [ ] `openclaw-agents-core`：`SubscribeState`（对应 `subscribeEmbeddedPiSession`）**完整字段**（严格对齐 TS `EmbeddedPiSubscribeState`）：
    - `assistantTexts: List<String>`
    - `toolMetas: List<{toolName, meta}>`
    - `compactionInFlight: boolean`
    - `pendingCompactionRetry: int`
    - `compactionRetryPromise: CompletableFuture<Void>`（nullable）
    - `unsubscribed: boolean`（一旦 `true`，所有后续 event 视为 `AbortError`）
    - `waitForCompactionRetry()` 等 `compactionInFlight=false && pendingCompactionRetry=0`
    - 事件分发（对应 `createEmbeddedPiSessionEventHandler`）：`message_start / message_update / message_end / tool_execution_start/update/end / auto_compaction_start/end / agent_start / agent_end`
- [ ] `AutoReplyPipeline` 内部改写：`handle / streamHandle` API 保持不变，内部改走 `PiAgentRunner.run(req)`，旧的一次 `provider.chat` 路径作为 "no-agent fast path" 可通过 `openclaw.agent.enabled=false` 关闭以便回退 debug
- [ ] Web 通道 SSE 端点升级：`/api/channels/web/messages/stream` 输出 `AgentEvent`（event 名对齐：`turn.start / assistant.delta / tool.call.requested / tool.call.result / tool.call.error / turn.end / run.end / run.error`）；旧名 `delta/tool_call/done/error` 保留一个版本作为别名
- [ ] 单测：
    - `ActiveRunRegistryTest`（注册 / `run_replaced` 日志 / handle-mismatch 保护 / `clearIfMatches` == 校验 / reverse lookup / `queueMessage` 三种 false 场景 `no_active_run / not_streaming / compacting`）× 7
    - `WaitForEmbeddedPiRunEndTest`（默认 15000ms 超时返回 `false` / 最小 `Math.max(100, x)` / 正常结束返回 `true` / 多 waiter 都收到通知）× 4
    - `SubscribeStateTest`（完整 6 字段 / 压缩等待 `waitForCompactionRetry` / `unsubscribed=true` 后事件视为 AbortError）× 4
    - `PiAgentRunnerTest`（调度不执行细节 / finally 清理 / abort 不进入 fallback / 状态机总图每个状态可达）× 5
    - `ProviderChunkToAgentEventTest`（delta 聚合 / toolCall 片段拼接 / done 收束）× 4
- [ ] 集成测（WebMvc + WebTestClient）：`/messages/stream` 返回新 AgentEvent 名称，完整回合含 `turn.start → assistant.delta × N → turn.end → run.end`

**交付物**：执行链路四层地基立起来，可跑通"端到端一次不含工具的对话"，SSE 输出对齐 AgentEvent 规范。

### M3.2 · 工具系统 + 策略 pipeline

- [ ] `openclaw-tools`：`Tool` SPI（`name / description / parameters(JsonSchema) / executor`）+ `ToolRegistry`（具名能力注册，冲突硬拒绝）+ `ToolExecutor`（`execute(ToolCall, AgentRunContext)`）
- [ ] `openclaw-tools`：**外层 5 步装配**（对应 `createOpenClawCodingTools`，顺序**不可配置**）：

    | Step | Java 类 / 方法 | 关键不变量 |
    |---|---|---|
    | 1 | `OwnerOnlyToolPolicy.apply(tools, senderIsOwner)` | `senderIsOwner` 默认 `false`（安全 opt-in） |
    | 2 | `ToolPolicyPipeline.apply(tools, innerSteps)` | 见下方 **内层 9 步**  |
    | 3 | `ToolParameterNormalizer.normalize(tool)` | **必须在 Step 4 之前**，否则 `wrapToolWithBeforeToolCallHook` 包裹后 execute 变旧，normalize 无效 |
    | 4 | `BeforeToolCallHookWrapper.wrap(tool, hookRunner)` | 写入 `BEFORE_TOOL_CALL_WRAPPED` marker property（不可枚举），防止 `pi-tool-definition-adapter` 重复包裹 |
    | 5 | `AbortSignalWrapper.wrap(tool, abortSignal)`（仅当 abortSignal 非空） | execute 前后 check abort 优先中断 |

- [ ] `openclaw-tools`：**内层 Policy Pipeline 9 步**（对应 `buildDefaultToolPolicyPipelineSteps` + `pi-tools.ts` 追加），**精确 step label 固定**：

    ```
    1. tools.profile (${profile})          stripPluginOnlyAllowlist=true
    2. tools.provider-profile (${profile}) stripPluginOnlyAllowlist=true
    3. group tools.allow                   stripPluginOnlyAllowlist=true
    4. tools.global                        stripPluginOnlyAllowlist=false
    5. tools.global-provider               stripPluginOnlyAllowlist=false
    6. tools.agent (${agentId})            stripPluginOnlyAllowlist=false   (仅 agentId 非空)
    7. tools.agent-provider (${agentId})   stripPluginOnlyAllowlist=false   (仅 agentId 非空)
    8. sandbox tools.allow                 (pipeline 外追加)
    9. subagent tools.allow                (pipeline 外追加)
    ```

- [ ] `openclaw-tools`：`PolicyStep` 抽象 + `PolicyProvenance`（记录当前 step label，用于 diagnostics 输出 `tools.profile (balanced) stripped 3 unknown entries`）
- [ ] `openclaw-tools`：`BeforeToolCallHookWrapper`（对应 `wrapToolWithBeforeToolCallHook`）**精确行为**：
    1. `outcome = runBeforeToolCallHook({toolName, params, toolCallId})`
    2. `outcome.blocked=true` → `throw new OpenClawException(ILLEGAL_STATE, outcome.reason)`
    3. `outcome.params != null` → `adjustedParamsByToolCallId.put(toolCallId, outcome.params)`；LRU 上限 `MAX_TRACKED_ADJUSTED_PARAMS`（建议 4096），超出驱逐最旧条目
    4. 用 `outcome.params` 而非原始 `params` 调 `execute(...)`
    5. 单插件参数合并规则：`{...originalParams, ...hookResult.params}`；多插件顺序执行 + `last-write-wins` merge（`params/block/blockReason` 三字段 `next ?? acc`）
- [ ] `openclaw-tools`：`AdjustedParamsStore.consumeForToolCall(toolCallId)`：after-hook 取"改写后参数"而非原参数；`pi-tool-definition-adapter` 必须通过此方法取参
- [ ] `openclaw-tools`：`AfterToolCallHookEmitter`（对应 `handleToolExecutionEnd`）**精确行为**：
    - 成功路径 `try` 末尾：`hookRunner.runAfterToolCall({toolName, params: afterParams, result})`
    - 失败路径 `catch` 块：`hookRunner.runAfterToolCall({toolName, params, error: described.message})`
    - 两条路径均 **fire-and-forget**（不 await，不链回主链路）；hook 抛错只 `log.debug` 不传播
    - 携带 `durationMs`
- [ ] `openclaw-tools`：内置工具集（首批）—— `clock.now` / `echo.say`（回显）/ `http.get`（受限白名单）；不含 bash/PTY/`apply_patch`（`apply_patch` **对非 OpenAI provider 默认关闭**，M5 再说）
- [ ] `openclaw-tools`：`SubagentPolicyDefaults`（对应 `resolveSubagentToolPolicy`）：默认 deny `sessions_spawn / sessions_send / sessions_list / sessions_history / gateway / agents_list / cron / memory_search / memory_get`，防止子 Agent 拿编排主控权
- [ ] 单测：
    - `OwnerOnlyToolPolicyTest`（默认 `senderIsOwner=false` 剥离 / owner 保留）× 2
    - `ToolPolicyPipelineTest`（9 step 精确顺序 / `stripPluginOnlyAllowlist` 仅前 3 步生效 / agentId 空时跳过 6/7 步）× 6
    - `ToolParameterNormalizerTest`（根级 `anyOf` 展开 / 必须在 hook 前）× 3
    - `BeforeToolCallHookWrapperTest`（block / 改参 / `BEFORE_TOOL_CALL_WRAPPED` marker 防重复包裹 / LRU 驱逐 / 多插件 last-write-wins）× 6
    - `AfterToolCallHookEmitterTest`（成功 + 失败两路径 + fire-and-forget + `consumeForToolCall` 取 afterParams）× 4
    - `SubagentPolicyDefaultsTest`（默认 deny 名单覆盖 9 个工具）× 1

**交付物**：可跑通"Agent 调用 `clock.now` 工具 → Agent 回填 assistant.delta → turn.end"的完整 E2E；工具策略 pipeline 顺序与治理完全对齐官方文档。

### M3.3 · 双层 Lane 并发模型

- [ ] `openclaw-session-lanes`：`LaneState` **完整字段**（对应 TS `LaneState`）：
    - `lane: String`
    - `queue: Deque<QueueEntry>`
    - `activeTaskIds: Set<Long>`
    - `maxConcurrent: int`（默认 `1` 串行；`setCommandLaneConcurrency` 可改，改后立刻 `drainLane` 实时生效）
    - `draining: boolean`（防重入 pump 标志；`true` 时不再触发新一轮 drain）
    - `generation: long`（默认 0；`resetAllLanes` 时 `++`，让旧 finally 回写因 generation 不匹配被忽略）
- [ ] `openclaw-session-lanes`：`QueueEntry` **完整字段**：
    - `task: Supplier<CompletableFuture<?>>`
    - `resolve: Consumer<Object>` / `reject: Consumer<Throwable>`
    - `enqueuedAt: long`
    - `warnAfterMs: long`（**默认 2000ms**，对应 TS `opts?.warnAfterMs ?? 2_000`）
    - `onWait: LongConsumer`（nullable；`waitedMs >= warnAfterMs` 时调用 + `log.warn`，**只告警不取消**）
- [ ] `openclaw-session-lanes`：`LaneNames` 解析规则（对应 `resolveSessionLane / resolveGlobalLane`）：
    - `resolveSessionLane(key)`：`cleaned = key.trim()` 空值回落 `CommandLane.MAIN`；已以 `session:` 前缀起头则保持（幂等），否则拼 `session:${cleaned}`
    - `resolveGlobalLane(lane)`：空值默认 `CommandLane.MAIN`
    - `CommandLane` 枚举：`MAIN / CRON / SUBAGENT`
- [ ] `openclaw-session-lanes`：`LaneDispatcher.enqueue(lane, task, opts)`：创建/取 `LaneState` → `enqueuedAt = System.currentTimeMillis()` → `queue.addLast(entry)` → 立即 `drainLane(state)`
- [ ] `openclaw-session-lanes`：`drainLane(state)` **精确流程**：
    1. `if (state.draining) return`（防重入）
    2. `state.draining = true; try { while (active < maxConcurrent && !queue.isEmpty()) { pump(...) } } finally { state.draining = false; }`
    3. 任务**成功 + 失败都要继续 pump**，不饿死后续
- [ ] `openclaw-session-lanes`：**Probe Lane 静默**（对应 `isProbeLane`）：`lane.startsWith("auth-probe:") || lane.startsWith("session:probe-")` 时任务失败**不打错误日志**（探针失败是预期）
- [ ] `openclaw-session-lanes`：双层 enqueue：`enqueueInSessionLane(sessionKey, task)` → 内部再 `enqueueInGlobalLane(globalLaneName, task)`；`GatewayLaneConcurrencyApplier.apply(cfg)` 对应 `applyGatewayLaneConcurrency`，分别设置 `CRON / MAIN / SUBAGENT` 三类全局并发上限；**热重载时再次调用同一方法实时生效**
- [ ] `openclaw-session-lanes`：`SessionKeyResolver` SPI（从 `RoutingKey` → `SessionKey`）；默认实现**保持现状**（`channel:account:conversation` 三段式），但暴露扩展点以便后续实现 dm→main 桶 / group 独立的策略
- [ ] `openclaw-session-lanes`：`resetAllLanes()` **精确步骤**（对应 `"Used after SIGUSR1 in-process restarts where interrupted tasks' finally blocks may not run"`）：
    1. `state.generation++`
    2. `state.activeTaskIds.clear()`（强制归零；旧 finally 可能永远不跑了）
    3. 保留 `queue`，重新 `drainLane`（积压任务继续执行）
- [ ] `openclaw-session-lanes`：`clearCommandLane(lane)`：只取消 `queue` 里未开始的任务，`reject` 抛 `CommandLaneClearedException`；**不影响** 已 `active` 的任务
- [ ] `openclaw-session-lanes`：`waitForActiveTasks(timeoutMs) → {drained: boolean}`：
    - `POLL_INTERVAL_MS = 50`
    - 进入时快照 `activeAtStart: Set<Long>`（当前所有 active ID）
    - 每 50ms 检查这批 ID 是否全完成；**只等这批，不等新进来的任务**
    - 超时返回 `{drained: false}`，**不 reject**
- [ ] `openclaw-session-lanes`：虚拟线程 worker（`Thread.ofVirtual().factory()` + `Executors.newThreadPerTaskExecutor`）；配置 `max-queue` 可选上限，满时拒绝并返回显式错误
- [ ] `openclaw-agents-core`：`PiAgentRunner.run` 调用 `enqueueInSessionLane(...)` → `enqueueInGlobalLane(...)`；形成 `sessionLane → globalLane → attempt` 三层调用栈
- [ ] **session I/O 搬入 lane**：`AutoReplyPipeline` 把 `sessions.loadOrCreate → append(user) → submit → append(assistant) → sessions.save` 整个闭包作为 task 交给 `SessionLaneCoordinator.enqueueInSessionLane`，消除当前同 session 并发 下 "`loadOrCreate` 唯一键竞争 + `save` 丢消息" 的两类遗留竞态（A1 只在 `JdbcSessionRepository.insertEntity` 做了 `DuplicateKeyException` 软兜底，不是根治方案）。配套单测：10 线程并发同 sessionKey → DB 只增长一份完整消息序列，不丢失、不冲突
- [ ] 单测：
    - `LaneDispatcherTest`（同会话串行 / 跨会话并行 / `maxConcurrent` 改值实时生效 / `draining` 防重入 / 失败也 pump）× 7
    - `LaneNamesTest`（`session:` 幂等前缀 / 空 key 回落 MAIN / `resolveGlobalLane` 空值默认）× 4
    - `ProbeLaneSilenceTest`（probe 失败无错误日志 / 非 probe 正常告警）× 2
    - `ResetAllLanesTest`（generation 防污染：旧 finally 回写被忽略 / activeTaskIds 归零 / 队列保留 + 重新 drain）× 3
    - `WaitForActiveTasksTest`（POLL_INTERVAL=50 / 仅等快照时的 active / 超时 `drained=false` 不 reject）× 3
    - `SessionKeyResolverTest`（默认行为不变 / 可替换策略）× 2
    - 并发 JUnit 压测：10 session × 20 round 的 delay 报告（p50 / p95 / p99）

**交付物**：能稳定跑 10 个会话并发、每个会话内严格串行；reset / abort / max-concurrent 配置生效；官方文档"lane 排队 + 并发隔离"可全部自检通过。

### M3.4 · PiAgent 主链路 + AttemptExecutor + FailoverError

- [ ] `openclaw-agents-core`：`AttemptExecutor.execute(AgentRunContext)` 完整版：
    1. `sanitizeSessionHistory(...)` 清洗
    2. `validateProviderTurns(...)`（Gemini / Qwen / 通用）
    3. `limitHistoryTurns(...)`（按 `sessionKey` 解析历史上限，dm vs group 分桶）
    4. `sanitizeToolUseResultPairing(...)`（修复孤儿 tool_use/tool_result）
    5. 挂 subscribe
    6. `runBeforeAgentStart(...)` hook（返回 `prependContext` 拼到 prompt 前）
    7. `session.prompt(...)`（provider 调用）
    8. `waitForCompactionRetry()`
    9. 收尾 hook `runAgentEnd(...)`
    10. finally `unsubscribe()` + `clearActiveEmbeddedRun(...)`
- [ ] `openclaw-agents-core`：压缩超时快照选择（对应 `run/compaction-timeout.ts`）：`shouldFlagCompactionTimeout` 判定 + `selectCompactionTimeoutSnapshot`（优先 pre-compaction 快照，避免拿到"半压缩"状态脏消息）
- [ ] `openclaw-agents-fallback`：`FailoverError` record + `FailoverReason` enum（`BILLING / RATE_LIMIT / AUTH / TIMEOUT / FORMAT / UNKNOWN`）+ `FailoverReasonClassifier`（先看 HTTP status 402/429/401/403/408/400 → 再看错误码 `ETIMEDOUT` → 再看 message 文本）+ `FailoverErrorDescriber`（提取 `{message, reason, status, code}`）
- [ ] `openclaw-agents-fallback`：`resolveFallbackCandidates`：主模型入队 → 配置 `agents.defaults.model.fallbacks` → 别名解析 → allowlist 约束 → 去重；支持 `fallbacksOverride`（含空数组 = 仅尝试主模型）
- [ ] `openclaw-agents-fallback`：`runWithModelFallback`：遍历 candidates → 成功立刻返回 `{result, provider, model, attempts}` → 失败时 `shouldRethrowAbort` 命中则直接抛（用户主动中断优先） → 否则 `coerceToFailoverError` + 记录 `attempts.push({provider, model, reason, status, code})` → 全失败抛 `AllModelsFailedException(attempts)` 包裹可读 JSON
- [ ] `openclaw-providers-registry`：现有 `CompositeProviderClient` 改为在 `ProviderCall` 失败时先走 `FailoverReasonClassifier`，再根据 reason 决定：`AUTH` → 切 auth profile / `RATE_LIMIT` → cooldown 当前 key / `BILLING` → 升级为不可回退（直接抛） / `TIMEOUT / FORMAT / UNKNOWN` → 按候选继续
- [ ] `openclaw-agents-core`：`AuthProfileRotator`（对应 `advanceAuthProfile`）：配合 `AuthProfileVault` 暴露"当前 profile / 下一个 profile"语义，attempt 失败后按 reason 决定是否切
- [ ] 单测：`FailoverReasonClassifierTest`（HTTP status × 错误码 × message 文本 × 空错误回落）× 10，`FallbackCandidatesTest`（别名 / override / 空数组 / allowlist 约束）× 6，`ModelFallbackRunnerTest`（abort 不 fallback / billing 不 fallback / 全失败汇总 / 成功立即返回）× 6，`AttemptExecutorTest`（五步卫生顺序 / 压缩超时 snapshot / hook 注入）× 8，`AuthProfileRotatorTest`（AUTH 切换 / RATE_LIMIT cooldown 不切 profile）× 3

**交付物**：注入 provider 500 / 429 / 401 时，系统能正确分类并走对应路径；用户主动 abort 不会被误降级；`attempts[]` 输出带 reason/status/code 便于观测。

### M3.5 · 上下文引擎 + 记忆系统

- [ ] `openclaw-context-engine`：`ContextWindowInfo`（对应 `resolveContextWindowInfo`）：
    - `tokens: long`
    - `source: enum { model, modelsConfig, agentContextTokens, default }`
    - 来源优先级 `modelsConfig > model > agentContextTokens > default`
    - `defaultTokens = 32000`（默认值常量）
- [ ] `openclaw-context-engine`：`ContextWindowGuard.evaluate(info, HARD_MIN_TOKENS=16000, WARN_BELOW_TOKENS=32000)`：
    - `shouldBlock` → 在 `PiAgentRunner` 进入 attempt **之前**抛 `ContextWindowTooSmallException(tokens)`
    - `shouldWarn` → 只 `log.warn`，仍继续执行
- [ ] `openclaw-context-engine`：`ToolUseResultRepairReport`（对应 `ToolUseRepairReport`）完整字段：
    - `messages: List<AgentMessage>`
    - `added: List<ToolResultMessage>`（缺失结果插入的 synthetic error result）
    - `droppedDuplicateCount: int`
    - `droppedOrphanCount: int`
    - `moved: boolean`
- [ ] `openclaw-context-engine`：`SessionHistorySanitizer` + `ToolUseResultPairingSanitizer`（对应 `sanitizeSessionHistory` + `sanitizeToolUseResultPairing`）：移动错位 `toolResult` 到对应 `assistant.toolCall` 后面 / 删除重复 `toolResult` / 对缺失结果插入 synthetic error result 防断链
- [ ] `openclaw-context-engine`：`HistoryTurnLimiter`（对应 `limitHistoryTurns` + `getHistoryLimitFromSessionKey`）：按 dm/group 分流解析配置 `dms.<user>.historyLimit` / `historyLimit`
- [ ] `openclaw-context-engine`：`OverflowRecoveryChain` **精确步骤**：
    1. `compactEmbeddedPiSessionDirect(...)` **最多 3 次**（对应 TS `for (let i = 0; i < 3; i++)`）
    2. 仍未解决 → `truncateOversizedToolResultsInSession(...)`
    3. 仍未解决 → 抛 `ContextOverflowUnresolvedException`（可读错误而非崩溃）
- [ ] `openclaw-memory`：`MemorySearchManager` SPI：`search / readFile / status`（可选 `sync / probeEmbeddingAvailability / probeVectorAvailability / close`）
- [ ] `openclaw-memory`：`BuiltinMemoryManager`（基于 MySQL 存储 + 简单 LIKE 搜索，首版）；`QmdMemoryManager` 接口预留（首版可抛 `UnsupportedOperationException`）
- [ ] `openclaw-memory`：`MemoryBackendConfigResolver`（对应 `resolveMemoryBackendConfig`）：`memory.backend` 非 `qmd` 直接回落 `builtin`；`qmd` 解析 `command/collections/update/limits`；**解析失败不崩，回落 builtin**
- [ ] `openclaw-memory`：`getMemorySearchManager(agentId, cfg)` 行为：
    1. `backend=qmd` 时先查 `QMD_MANAGER_CACHE`（key = `buildQmdCacheKey(agentId, qmd)`）
    2. 未命中则 `QmdMemoryManager.create(...)` + 写缓存 + 包一层 `FallbackMemoryManager`
    3. qmd 初始化失败 → `log.warn` → 动态加载 `MemoryIndexManager.get(...)` 兜底
    4. 兜底也失败 → 返回 `{manager: null, error}` 给工具层返回 disabled，**不 throw 崩主流程**
- [ ] `openclaw-memory`：`FallbackMemoryManager`（对应 TS 同名类）**完整 4 字段状态机**：
    - `primaryFailed: boolean`（默认 false）
    - `fallback: MemorySearchManager`（nullable，懒加载）
    - `lastError: String`（nullable）
    - `cacheEvicted: boolean`（防重复 evict）
    - `search(...)` 抛错 → `primaryFailed=true` + `primary.close()` + `evictCacheEntry()`（从 `QMD_MANAGER_CACHE.remove(cacheKey)` + `cacheEvicted=true`） → `ensureFallback()` 懒加载 builtin
    - `close()` 回收 primary + fallback，`evictCacheEntry()` 清缓存（下次 `getMemorySearchManager` 能重试 qmd，"在线自愈"的核心）
- [ ] `openclaw-memory`：`MemoryTools`（面向 LLM 的工具）—— **description 精确文案**（不可自编）：
    - `memory_search` description 开头必须是 `"Mandatory recall step: semantically search MEMORY.md + memory/*.md..."`（强迫模型回答前先做记忆召回）
    - `memory_get` description 开头必须是 `"Safe snippet read from MEMORY.md or memory/*.md with optional from/lines; use after memory_search to pull only the needed lines and keep context small."`
    - 执行链：解析 `agentId(sessionKey)` → `getMemorySearchManager(...)` → qmd 内部 **scope guard** `isScopeAllowed(sessionKey)` 不匹配返回 `[]` 不报错 → `waitForPendingUpdateBeforeSearch()` 确保最近写入已索引 → `manager.search(query, {maxResults, minScore, sessionKey})` → `decorateCitations(...)` → qmd 模式 `clampResultsByInjectedChars(..., limits.maxInjectedChars)`（默认 4000）
    - `memory_search` **返回 shape 固定**：`{results, provider, model, fallback: boolean, citations: boolean}`
    - qmd `status()` 返回 shape 固定：`{backend:"qmd", provider:"qmd", model:"qmd", files: totalDocuments, vector:{enabled:true, available:true}}`
- [ ] `openclaw-memory`：`CitationsPolicy`（`on / off / auto`）：auto 模式下 direct chat 附引用，group/channel 默认不加；`decorateCitations` 格式化路径 + 行号
- [ ] `openclaw-memory`：`MemoryStartupProbe`（对应 `startGatewayMemoryBackend`）：仅 `backend=qmd` 时触发 → 提前 `getMemorySearchManager(...)` + `probeVectorAvailability()`，成功 `log.info` 失败 `log.warn`；**作用：把故障暴露在启动阶段**
- [ ] `openclaw-memory`：`EmbeddingOps`（对应 `manager-embedding-ops.ts`）：`chunkMarkdown` / `buildEmbeddingBatches` 按预算切批 / `loadEmbeddingCache` 去重 / 批次失败有限次重试 + 退避 / `chunks + chunks_vec + chunks_fts` 三表同步更新保持检索一致
- [ ] 单测：
    - `ContextWindowGuardTest`（HARD_MIN=16000 阻断 / WARN_BELOW=32000 警告 / defaultTokens=32000 回落 / source 优先级）× 6
    - `ToolUseResultPairingSanitizerTest`（移动 / 重复 / synthetic error / `ToolUseRepairReport` 字段齐全）× 5
    - `OverflowRecoveryChainTest`（最多 3 次 compact / 继续 truncate / 最终 readable error 不崩）× 4
    - `MemoryBackendConfigResolverTest`（非 qmd 回落 / qmd 解析失败回落 builtin）× 3
    - `FallbackMemoryManagerTest`（primary 失败切 fallback / `evictCacheEntry` 生效 / `ensureFallback` 懒加载 / `close` 级联 / `cacheEvicted` 防重复）× 6
    - `MemoryToolsTest`（scope guard 返回 `[]` 不报错 / `waitForPendingUpdateBeforeSearch` 生效 / `maxInjectedChars=4000` clamp / citations 三态 / 返回 shape 固定）× 6
    - `MemoryStartupProbeTest`（qmd 成功 info / 失败 warn / 非 qmd 跳过）× 3

**交付物**：长会话 100 轮后仍稳定；孤儿 toolResult 不再触发 provider 400；memory backend 故障自愈；`maxInjectedChars` 生效；citations 在 dm/group 行为分化。

### M3.6 · Skills + 子 Agent + Approval + Hook Runner 升级

- [ ] `openclaw-skills`：`Skill` 元数据（`id / name / primaryEnv / snapshotVersion / source / prompt`）+ `SkillSource` enum（来源优先级 `EXTRA < BUNDLED < MANAGED < PERSONAL < PROJECT < WORKSPACE`）
- [ ] `openclaw-skills`：`WorkspaceSkillSnapshotBuilder`（对应 `buildWorkspaceSkillSnapshot`）：按优先级合并 + 生成 `WorkspaceSkillSnapshot{ prompt, skills, resolvedSkills, version }`
- [ ] `openclaw-skills`：`SkillsWatcher`（对应 `ensureSkillsWatcher`）：watch `SKILL.md` add/change/unlink → debounce → `bumpSkillsSnapshotVersion` → 下一轮 attempt 自动刷新
- [ ] `openclaw-skills`：`SkillEnvOverrides`（对应 `applySkillEnvOverrides`）：按 skill config 写入 env，自动注入 `primaryEnv + apiKey`，返回 `Restorable` 结束后回滚（`try-with-resources`）
- [ ] `openclaw-skills`：`RemoteSkillsCache`（对应 `src/infra/skills-remote.ts`）：`remoteNodes` 缓存（nodeId / platform / commands / bins）；能力变化 `bumpSkillsSnapshotVersion({reason:"remote-node"})`（即使本地 SKILL.md 未改）
- [ ] `openclaw-agents-subagent`：`SessionsSpawnTool`（对应 `sessions-spawn-tool.ts`）：
    - 防递归：当前会话 `isSubagentSessionKey` 直接拒绝
    - agent 白名单：`subagents.allowAgents` 校验跨 agent spawn
    - 创建子会话 key：`agent:<target>:subagent:<uuid>`
    - `sessions.patch` 可选覆盖 model / thinkingLevel
    - 网关启动子 run：`method=agent`、`lane=subagent`
    - 注册 `registerSubagentRun(...)`
- [ ] `openclaw-agents-subagent`：`SubagentRegistry`（对应 `subagent-registry.ts`）：内存 + 落盘（MySQL `oc_subagent_run` 表，Flyway `V3__subagent.sql`）+ 启动恢复 `restoreSubagentRunsOnce()` + `agent.wait` RPC 兜底 + 归档清扫（`archiveAfterMinutes`）
- [ ] `openclaw-agents-subagent`：`SubagentAnnounceFlow`（对应 `subagent-announce.ts`）：先 `waitForSettled()`（避免压缩重试中误报）→ 构造 `triggerMessage` 给父 Agent 自己总结（不直接硬回写用户）→ 支持 `NO_REPLY` 静默完成
- [ ] `openclaw-agents-subagent`：`SessionsSendTool`（对应 `sessions-send-tool.ts`）：支持 `sessionKey / label` 定位 + sandbox `sessionToolsVisibility=spawned` + `tools.agentToAgent.allow` 策略校验 + 嵌套 lane `AGENT_LANE_NESTED`
- [ ] `openclaw-approval`：`ExecApprovalDecision` enum（`ALLOW_ONCE / ALLOW_ALWAYS / DENY` + `null = timeout`；**timeout 不是 reject，调用方必须判 null**）+ `ExecApprovalRecord`（`id / command / createdAtMs / expiresAtMs / decision / resolvedAtMs / resolvedBy`）
- [ ] `openclaw-approval`：`ExecApprovalManager`（对应 `src/gateway/exec-approval-manager.ts`）**精确行为**：
    - `RESOLVED_ENTRY_GRACE_MS = 15_000`
    - `create(cmd, timeoutMs)` → 生成 `id / createdAtMs / expiresAtMs`
    - `register(record)`：写 pending map + 返回 `CompletableFuture<ExecApprovalDecision>`；**同 id 未决幂等返回同一 Future**；**同 id 已决则抛 `ILLEGAL_STATE("already resolved")`**
    - `timeout` 触发时：`resolve(null)`（不挂起也不 reject）+ 延迟 `RESOLVED_ENTRY_GRACE_MS` 后从 map 删
    - `resolve(id, decision)`：已决再次 resolve 返回 `false`（幂等）；成功后同样 15s grace
    - grace 窗口作用：`waitDecision` 可能在 `request` 响应 accepted 之后才来，grace 保证仍能取到 decision
- [ ] `openclaw-approval`：`ExecApprovalPolicy`（对应 `requiresExecApproval`）**精确规则**：
    - `ask == "always"` → 永审
    - `ask == "on-miss" && security == "allowlist" && (!analysisOk || !allowlistSatisfied)` → 审批
    - 其他情况 → 不审批
- [ ] `openclaw-approval`：Gateway 端点契约（M4 真正接入）—— `exec.approval.request` 时序**必须**：
    1. 校验参数
    2. `manager.create(...)`
    3. **先 `manager.register(...)` 再响应**（race 防御：若响应后客户端才来 `waitDecision`，entry 可能尚未注册）
    4. 广播 `exec.approval.requested`
    5. two-phase：先回 `accepted` → 等 decision → 再推送 `resolved`
    `exec.approval.waitDecision`：entry 不存在返回 `{error: "expired or not found"}`；存在则等 Future（可能 `null = timeout`）
    `exec.approval.resolve`：`decision` 只能是 `allow-once / allow-always / deny`，其余直接拒
- [ ] `openclaw-hooks-runtime` **（新模块）**：`HookRunner` 独立于 plugins，可被 agents/tools/approval 共用：
    - **排序**：按 `priority` **降序**（高优先级先执行），同 priority 按注册时间
    - `runVoidHook(name, evt, ctx)`：并行 `CompletableFuture.allOf(...)`；用于观察型 hook（`agent_end / gateway_start / after_tool_call`）
    - `runModifyingHook(name, evt, ctx, merge)`：顺序串行；每 handler 返回 delta → `acc = {...acc, ...delta}`；用于可修改 hook（`before_agent_start / before_tool_call`）
    - 默认 `catchErrors=true`：单个 hook 报错只 `log.error`，**不打断主链路**；`catchErrors=false` 仅调试用
    - 内置 hook 点：`before_agent_start` / `before_tool_call` / `after_tool_call` / `run_agent_end`
    - `before_agent_start` 合并规则：`systemPrompt` 后者覆盖、`prependContext` 按顺序拼接
    - `before_tool_call` 合并规则：`{params: next.params ?? acc.params, block: next.block ?? acc.block, blockReason: next.blockReason ?? acc.blockReason}`
    - **`HookOutcome` 三态**：`modify`（merge 后继续）/ `block`（抛 `HookBlockedException`，不执行后续 hook 与主链路）/ `shortCircuit`（终止 hook 链 + 跳过主链路，由调用方直接返回 `reply`）；优先级 `block > shortCircuit > modify`
- [ ] **冗余删除（M3 起执行）**：
    - 删除 `openclaw-auto-reply` 中的 `ChatCommand` SPI 与 `ChatCommandDispatcher`；M2.4 的 `HelloChatCommand` 迁为 `HelloBeforeAgentStartHook`（注册 `before_agent_start` priority=500，匹配 `/hello` 前缀返回 `HookOutcome.shortCircuit("Hi from HelloPlugin!")`）；外部行为完全不变
    - 删除 `openclaw-providers-registry` 中的 `RegistryProviderClient`；`ProviderRegistry` 保留 `lookup / markFailure / markSuccess / isAvailable / cooldown 配置`，由 `ModelFallbackRunner`（M3.4）调用；`AutoReplyPipeline → ProviderClient` 的依赖路径改为经 `ModelFallbackRunner`（顺带消除 M2.2 的循环依赖隐患）
- [ ] `openclaw-plugins` 扩展：`PluginContext` 新增具名能力 API（冲突硬拒绝 + 集中 diagnostics）：`registerHook` / `registerTool` / `registerGatewayMethod` / `registerHttpRoute` / `registerCommand`；`PluginRegistry.diagnostics` 集中输出 loader 错误 / manifest 冲突 / 注册冲突 / 配置 schema 错误
- [ ] 单测：
    - `SkillsWatcherTest`（debounce / version bump）× 3
    - `WorkspaceSkillSnapshotBuilderTest`（来源优先级 `EXTRA<BUNDLED<MANAGED<PERSONAL<PROJECT<WORKSPACE`）× 3
    - `SubagentRegistryTest`（防递归 / 恢复 / cleanup）× 5
    - `SubagentAnnounceFlowTest`（waitForSettled / NO_REPLY）× 3
    - `ExecApprovalManagerTest`：
        - 三态决策（`ALLOW_ONCE / ALLOW_ALWAYS / DENY`）× 3
        - 超时 `resolve(null)` 不 reject 不挂起 × 1
        - 同 id 未决 `register` 幂等返回同一 Future × 1
        - 同 id 已决再 resolve 返回 false × 1
        - `RESOLVED_ENTRY_GRACE_MS = 15000` 内仍可 `waitDecision` × 1
        - grace 过期后 entry 删除 × 1
    - `ExecApprovalPolicyTest`（`always` / `on-miss+allowlist` 缺 / 其他不审批）× 3
    - `HookRunnerTest`：
        - 按 priority 降序执行 × 1
        - `runVoidHook` 并行（`Promise.all` 等价） × 1
        - `runModifyingHook` 顺序串行 + delta merge × 2
        - `catchErrors=true` 单 hook 失败不传播 × 1
        - `catchErrors=false` 抛出 × 1
    - `PluginRegistryTest`（method/route/command 冲突硬拒绝 / diagnostics 聚合）× 5

**交付物**：
1. 带工具调用的完整对话（`clock.now`、子 Agent spawn、approval 触发三个场景）全通；
2. SKILL.md 修改触发下一轮 snapshot 刷新；
3. 插件注册能力名冲突明确失败并产出 diagnostics；
4. `HookRunner` 被 agents/tools 共用，`catchErrors` 隔离故障不中断主链路。

### M3 整体验收

- [ ] 并发压测：10 session × 20 round，p50/p95/p99 延迟 + 错误率报告（留存于 `docs/translation-notes/m3-concurrency.md`）
- [ ] 核心 Agent 模块单元测试覆盖率 ≥ 80%
- [ ] OpenClaw 文档 07–15 所有"自检清单"逐条在测试或集成测试中体现
- [ ] `docs/translation-notes/m3.md` 记录每个子节的"偏离 TS 原实现"取舍（例如 `FallbackMemoryManager` 的 cache eviction 策略）

**交付物**：跑通 `runEmbeddedPiAgent` 等价业务语义 + 四层分离骨架稳定 + 双层 Lane + FailoverError + 上下文卫生 + 记忆双后端 fallback + Skills snapshot + 子 Agent 编排 + 审批状态机 + Hook Runner 全部落位。

---

## M4 · Gateway 控制平面（对应学习 27–42 + OpenClaw 文档 05）

- [ ] `openclaw-gateway-api`：协议帧 POJO + JSON Schema 资源
- [ ] `openclaw-gateway-ws`：WS 握手、`connect`、心跳、关闭
- [ ] `openclaw-gateway-core`：`chat.*` / `send.*` / `agent.*` / `sessions.*` / `node.*` / `device.*` / `config.*` / `skills.*` / `approval.*` / 运维
- [ ] `openclaw-gateway-http`：CORS / 限流 / 审计
- [ ] `openclaw-gateway-openai-compat`：`/v1/chat/completions` + SSE（**仅文本**）
- [ ] `openclaw-gateway-hooks`：hooks + tools-invoke
- [ ] ⛔ `openclaw-gateway-openresponses`（多模态）暂不建立

### M4.0 · 配置热重载 + 重启分类 + 空闲守卫（对应文档 05）

- [ ] `openclaw-server-runtime-config` 升级：`ReloadKind` enum（`HOT_RELOAD / NEEDS_RESTART`）+ `GatewayReloadPlan`（`kind / changedKeys / diagnostics`）+ `buildGatewayReloadPlan(prev, next)`（逐字段白/黑名单，默认保守归为 `NEEDS_RESTART`）
- [ ] `openclaw-server-runtime-config`：`GatewayIdleGuard.deferGatewayRestartUntilIdle(plan)` —— 检查项（顺序短路）：
    1. `ActiveRunRegistry` 无 running run
    2. embedded runs 清空
    3. gateway 发送队列 empty
    4. pending replies empty
    5. auto-reply chunk queue empty
    ；任一不满足 → schedule 重试（退避 500ms → 2s → 10s，上限 60s） + 结构化日志 `gateway.restart.deferred reasons=[runs,queue]`；配置 `openclaw.gateway.restart.max-wait` 兜底
- [ ] `openclaw-server-restart-sentinel` **（新模块）**：`RestartSentinel`（文件哨兵：`oc_restart.sentinel`）+ `RestartLauncher`（读取上次意图，区分"主动重启 vs 崩溃重启"）；启动时 clear sentinel，关闭前 write sentinel；`RestartReasonExporter` 暴露给观测 tag
- [ ] `ConfigWatcher` 对接 `buildGatewayReloadPlan`：修改 → 分类 → `HOT_RELOAD` 直接 fan-out 到 `HotReloadable` beans / `NEEDS_RESTART` 交给 `GatewayIdleGuard` 择机触发
- [ ] 单测：`ReloadPlanClassifierTest`（白名单热重载 / 黑名单强重启 / 默认走重启）× 6，`GatewayIdleGuardTest`（空闲立即重启 / 非空闲等待 / 60s 兜底强重启 + 日志）× 5，`RestartSentinelTest`（正常路径 / 崩溃路径）× 3

### M4.1 · WS/HTTP/OpenAI 兼容

- [ ] `openclaw-gateway-ws`：WS 握手 + `connect` 鉴权 + 心跳（30s ping / 90s idle close）
- [ ] `openclaw-gateway-core`：方法组全家桶（见上表），每方法 Schema 校验失败返回结构化 `INVALID_PARAMS`
- [ ] `openclaw-gateway-openai-compat`：`/v1/chat/completions` 兼容（SSE 流式，对齐 OpenAI chunk 格式，`tool_calls.index` 增量正确）；`/v1/models` 列模型
- [ ] `openclaw-gateway-hooks`：hooks + tools-invoke HTTP 入口（调用 `openclaw-hooks-runtime`）
- [ ] Springdoc OpenAPI 文档全量覆盖

**交付物**：OpenAI 官方 SDK + `wscat` 端到端可用；热重载无感生效；需要重启的 key 在空闲窗口内优雅重启。

---

## M5 · 复刻与补齐（对应学习 43–59 + OpenClaw 文档 15）

- [ ] `openclaw-server-*` 全部子模块翻译完成
- [ ] `openclaw-security`：鉴权 / 审计 / 限流 / 密钥保护
- [ ] `openclaw-cron`：**ElasticJob-Lite + ZooKeeper**；至少 2 个示例作业（例如会话清理、cooldown 扫描）
- [ ] `openclaw-daemon`：子进程心跳 + 重启策略（仅 `ProcessBuilder` / `ProcessHandle`）
- [ ] `openclaw-browser`：Playwright 上下文管理 + 页面抓取 + 作为 Tool 暴露给 Agent（🔹 可选但优先实现）
- [ ] 通道落地：**Web (P0)** 完成 HTTP + WebSocket 双通道
- [ ] ⛔ 其他通道、TTS、媒体、PTY 不做
- [ ] 端到端对接测试报告

### M5.0 · 多插件冲突治理与优先级（对应文档 15）

- [ ] `openclaw-plugins` 升级：`PluginSource` enum（`CONFIG > WORKSPACE > GLOBAL > BUNDLED`）+ 按 `realpath` 去重，高优先级源命中后低优先级直接忽略并落 diagnostics
- [ ] `PluginRegistry.registerGatewayMethod/registerHttpRoute/registerCommand/registerHook/registerTool`：具名能力冲突**硬拒绝**（不是 last-write-wins）；`Hook` 按 priority 并行/串行但允许多注册；`MemorySlot` 独占（同一 slot 仅允许一个插件占用）
- [ ] `PluginRegistry.diagnostics`：集中存储 loader 错误 / manifest 冲突 / 配置 schema 错误 / 注册失败；`/actuator/plugins` 暴露只读查询；`plugins` 启动日志打印最终生效清单 + 冲突汇总
- [ ] `PluginContext` 冻结：加载完成后禁止再动态替换已有能力（只能通过重启 / 热重载走正常通道）
- [ ] 单测：`PluginSourcePriorityTest`（config > workspace > global > bundled）× 4，`ConflictGovernanceTest`（method/route/command 硬拒绝 / memory slot 独占 / hook 多注册）× 5，`DiagnosticsAggregatorTest` × 3

### M5.1 · 运行时支撑模块

- [ ] `openclaw-server-*`：lanes / discovery / model-catalog / session-key / startup-memory 全部翻译
- [ ] `openclaw-security`：鉴权 / 审计 / 限流 / 密钥保护
- [ ] `openclaw-cron`：ElasticJob-Lite + ZooKeeper
- [ ] `openclaw-daemon`：`ProcessBuilder` + `ProcessHandle`
- [ ] `openclaw-browser`：Playwright for Java（🔹）
- [ ] Web 通道补 WebSocket 双通道

**交付物**：准生产部署包 + Docker 镜像 + 运行手册 + 插件治理 diagnostics 可查询

---

## 最终验收清单

功能：
- [ ] `openclaw-java chat` CLI 可用
- [ ] WS / HTTP 控制面所有方法组可用
- [ ] `/v1/chat/completions` 流式兼容（仅文本）
- [ ] Web 通道（HTTP + WebSocket）端到端对话可用
- [ ] 工具调用 / skills / 子 Agent / 审批全部跑通（纯文本工具）
- [ ] 配置热重载可用
- [ ] 审计 / 限流 / 鉴权可用
- [ ] ElasticJob 作业运行并上报成功

非功能：
- [ ] 全模块 `mvn clean install` 通过
- [ ] JaCoCo：整体 ≥ 60%，核心 (`agents` / `gateway-core` / `providers-*`) ≥ 80%
- [ ] Spotless / Checkstyle 零告警
- [ ] OpenAPI 文档完整
- [ ] Docker 镜像大小 ≤ 400MB（非 native）/ ≤ 120MB（native）
- [ ] 启动时间 ≤ 5s（JVM）/ ≤ 500ms（native）
- [ ] README 含「快速开始 / 开发者指南 / 架构图 / 部署手册」

文档：
- [ ] `docs/architecture.md`
- [ ] `docs/modules/*.md`（每模块一份 README）
- [ ] `docs/translation-notes/` 记录所有 TS→Java 的显著取舍
- [ ] `CHANGELOG.md`
