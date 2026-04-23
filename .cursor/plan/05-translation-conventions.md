# 05 · TS → Java 翻译约定

> 本文是动手写代码时的 **最小共识**。所有翻译产出必须遵守。出现新场景时，补充进来再继续写。

---

## 1. 类型系统映射

| TypeScript | Java | 备注 |
|---|---|---|
| `type X = { ... }` 纯数据 | `record X(...)` | 不变的数据类首选 record |
| 有方法的类 | `final class X` / `class X implements Y` | 默认 `final` |
| `interface X` | `interface X`（可 `sealed`） | 多态契约 |
| Union 类型 `A \| B` | `sealed interface` + `record` 实现 + `switch pattern` | JDK 21 支持 |
| Tagged union（带 `kind`） | `sealed interface` + 实例类 | 用 `switch` 枚举式处理 |
| `string \| undefined` | `String`（允许 null）或 `Optional<String>`（表达语义时） | 内部传参优先 `@Nullable`，API 返回可用 `Optional` |
| `number` | `long` / `int` / `double` / `BigDecimal` | 明确语义，避免默认 `double` |
| `Date` / `Date.now()` | `Instant` / `OffsetDateTime` | 不要用 `java.util.Date` |
| `Map<string, X>` | `Map<String, X>` 或 `record` | 已知字段全集时用 record |
| `Array<X>` / `X[]` | `List<X>` | 默认 `List`，特殊场景才 `X[]` |
| `Set<X>` | `Set<X>` | |
| `Promise<X>` | `CompletableFuture<X>` / `Mono<X>` / 虚拟线程 + 同步 | 见 §3 |
| `AsyncIterable<X>` / stream | `Flux<X>` | Reactor |
| `Buffer` / `Uint8Array` | `byte[]` / `ByteBuffer` | 持久化走 byte[] |
| `null` / `undefined` | 统一 `null`，禁止两种空值语义 | API 入参标注 `@NonNull`/`@Nullable` |
| `any` | `Object` 或 `JsonNode` | 尽量消灭 |
| `unknown` | `Object` + 显式校验 | |

---

## 2. 命名约定

| 元素 | TS 风格 | Java 约定 |
|---|---|---|
| 类 / record / interface | `fooBar` 文件名 | `FooBar` PascalCase |
| 方法 / 字段 / 局部变量 | camelCase | camelCase（保持） |
| 常量 | `SCREAMING_SNAKE` / `camelCase` | `ALL_CAPS` |
| 包名 | `kebab-case` 目录 | `com.openclaw.<module>.<子包>`（全小写，无连字符） |
| 测试类 | `foo.test.ts` | `FooTest` / `FooIT`（集成测试） |
| 事件 / DTO | `FooEvent` / `FooRequest` | 保留此后缀 |
| SPI / 策略 | 经常以 `XxxClient` / `XxxAdapter` / `XxxStrategy` 结尾 | 一致保留 |

---

## 3. 异步 / 并发

原 TS 基于单线程事件循环 + `async/await`，Java 有以下三条路径，**按场景选用**：

| 场景 | Java 做法 |
|---|---|
| 绝大多数业务代码（IO 阻塞但不流式） | **虚拟线程**：直接写同步代码，`spring.threads.virtual.enabled=true` + `Executors.newVirtualThreadPerTaskExecutor()` |
| Provider 流式 / SSE / WebSocket / Agent 事件流 | **Reactor** `Mono` / `Flux`（Spring WebFlux） |
| 跨线程 future 组合（非流式、需要回调链） | `CompletableFuture` |
| 简单定时 / 延迟 | `@Scheduled` / `Executors.newScheduledThreadPool()` |
| 锁 / 协程同步原语 | `ReentrantLock`、`Semaphore`、`StampedLock`、`ConcurrentHashMap` |

**禁止**：
- 在业务层混用 `Mono.block()` 和虚拟线程同步（容易死锁）
- 大量使用 `Thread.sleep`；请用 `LockSupport.parkNanos` 或调度器

**并发队列（Session-Lane）**：
- 每个 session 对应一个 FIFO 队列（用 `LinkedBlockingQueue`）
- 单 worker 虚拟线程消费该队列（保证 session 内串行）
- 跨 session 并行

---

## 4. 错误处理

| TS | Java |
|---|---|
| `throw new Error('...')` | `throw new OpenClawException(code, msg)`（自定义受检/非受检按场景） |
| `try/catch` | `try/catch`（保持） |
| `Result<T, E>` 模式 | `sealed interface Result<T> { record Ok<T>(T v); record Err<T>(ErrorInfo e); }` |
| 错误码 | 统一 `enum OpenClawErrorCode`，放 `openclaw-common` |
| 全局处理 | `@ControllerAdvice` + `@ExceptionHandler` → 结构化 JSON 响应 |
| 日志 | 捕获 → `log.error("msg key={}", key, ex)`，**携带异常栈** |

**禁止**：
- 吞异常：`catch (Exception e) {}` 空块
- 在 catch 里包装再抛但丢弃原因（必须 `new X("...", e)`）

---

## 5. 依赖注入

- 一律 **构造器注入**（final 字段，Lombok `@RequiredArgsConstructor` 可选）
- 禁止 `@Autowired` 字段注入
- SPI 扩展：优先 `List<XxxStrategy>` 注入 + `@Order`/自定义优先级；必要时走 `ApplicationContext#getBeansOfType`

---

## 6. 配置

- 所有可配置项用 `@ConfigurationProperties(prefix = "openclaw.xxx")` + `@Validated`
- 配置类 **record 化** 优先（Spring Boot 3 支持 record 配置）
- `application.yml` 分 profile：`dev` / `test` / `prod`
- 敏感配置放环境变量 + `openclaw-secrets`

示例：

```java
@ConfigurationProperties("openclaw.gateway")
@Validated
public record GatewayProperties(
    @NotBlank String host,
    @Min(1) int port,
    Duration heartbeatInterval
) {}
```

---

## 7. 日志

- 统一 `org.slf4j.Logger`，用 `LoggerFactory.getLogger(getClass())`（或 Lombok `@Slf4j`）
- 日志 key-value 风格：`log.info("session.started sessionId={} user={}", sid, user)`
- MDC 注入 `requestId`/`sessionId`/`channel`/`tenant`，在网关入口 + Channel 入口设置
- 生产输出 JSON（`logstash-logback-encoder`）

级别策略：
- `ERROR`：用户可见故障 / 需要报警
- `WARN`：异常但自愈
- `INFO`：生命周期事件（启动、加载、连接建立、会话创建）
- `DEBUG`：流程细节
- `TRACE`：数据级细节（默认关闭）

---

## 8. 测试

| 层 | 框架 | 规则 |
|---|---|---|
| 单元 | JUnit 5 + Mockito + AssertJ | 默认，一个类 → 一个测试类 |
| Web 层 | `MockMvc`（MVC） / `WebTestClient`（WebFlux） | `@WebMvcTest` / 显式构造 |
| Mapper 层 | **MyBatis-Plus + Testcontainers-MySQL**（禁用 H2/SQLite 替身） | `@SpringBootTest(classes = {数据源+Mapper配置})` 或自建基类 `AbstractMapperIT`；Flyway 在测试容器中自动迁移 |
| 集成 | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers（MySQL / ZK） | 命名后缀 `IT` |
| 外部 HTTP | **WireMock** | Provider / Web 通道都走 WireMock |
| 合约 | JSON Schema 对照 | Gateway 协议帧必须 schema-validate 所有样例 |

命名：`should_<预期>_when_<条件>`，或者 `<methodName>_<场景>` 二选一统一。

---

## 9. 代码风格

- Lombok 可以用：`@Slf4j` / `@RequiredArgsConstructor` / `@Builder`；**禁用** `@Data` 于 JPA 实体（用 explicit equals/hashCode）
- 行长 ≤ 120
- import 顺序：`java.*` → `javax.*` / `jakarta.*` → 第三方 → `com.openclaw.*`
- 一个 `.java` 文件一个顶层 class（record 除外）
- `final` 优先：字段、局部变量、参数默认都加 `final`（除非 reassignment）
- 注释：**只写意图/约束/权衡**，不写「做了什么」的废话
- TODO 必带负责人或日期：`// TODO(@admin 2026-05): xxx`

---

## 10. 翻译动作原则（重要）

每次翻译一个 TS 文件 / 模块时，按如下顺序：

1. **读源**：阅读 `openclaw/src/<path>/*.ts` 的全部 export + 对应 `*.test.ts`
2. **抽契约**：先在 `openclaw-<module>/src/main/java` 写出接口/record
3. **实现 + 单测同步**：`*.ts` 的每个核心分支都要有对应 JUnit 5 case
4. **对齐文档**：在 `docs/translation-notes/<module>.md` 写下：
   - 该文件的「核心行为」1-3 句话
   - 保留 / 移除 / 合并 / 拆分的函数列表
   - 与 Java 惯用法不同的有意偏离
5. **刷新计划**：在 `.cursor/plan/04-milestones.md` 勾选对应项

---

## 11. 禁忌

- ❌ 照搬 TS 的工具函数文件到 `Utils` 静态类（请合并到合适模块的职责对象里）
- ❌ 业务代码直接使用 Jackson（`ObjectMapper` / `@JsonProperty` / `JsonNode`）—— **统一 Fastjson2**：`JSON.toJSONString` / `JSON.parseObject` / `@JSONField`；`JsonNode` 仅限 `openclaw-gateway-api` 内部的 Schema 校验器使用
- ❌ 自行 `new ObjectMapper()` / `new JSON()`（Fastjson2 直接用静态方法即可，无需创建实例）
- ❌ 直接用 `System.out.println`（只允许 SLF4J）
- ❌ 把所有错误都包成 `RuntimeException`（请用 `OpenClawException` 体系）
- ❌ 在 Controller 里写业务（必须走 Service 层）
- ❌ Service 直接注入 `JdbcTemplate` / `EntityManager`（必须走 **MyBatis-Plus Mapper（`BaseMapper<T>`）或自定义 `IService<T>`**）
- ❌ 使用 JPA 注解（`@Entity` / `@Table` / `@Column` / `@Repository extends JpaRepository`）
- ❌ 在 API 边界暴露实体类（必须 DTO）
- ❌ 时间用 `long timestamp` 除非性能敏感（优先 `Instant`）
- ❌ 使用 H2 / SQLite 作为测试替身（测试一律走 **Testcontainers-MySQL**）
- ❌ 在模块内随意引入媒体 / PTY / 音频 相关依赖（当前范围仅文本；**浏览器自动化除外**，只允许 `openclaw-browser` 引入 Playwright）
- ❌ CLI 顶层命令 / 启动脚本命名为 `openclaw`（必须用 **`openclaw-java`**，以免与本机已安装的 TS 版原生 CLI 冲突）；Picocli `@Command(name = ...)`、`bin/` 下的启动脚本、Docker `ENTRYPOINT`、`--help` 输出中的 usage 程序名必须一致为 `openclaw-java`

---

## 12. Agent 执行不变量（对齐 OpenClaw 文档 02 / 07 + 实战 17）

M3 起，`openclaw-agents-*` 范围内的实现必须满足以下**硬不变量**，违反即视为 bug：

1. **四层分离**：`PiAgentRunner`（调度） / `AgentAttemptExecutor`（单事务） / `SubscribeState`（事件聚合） / `ActiveRunRegistry`（run 注册表）四层职责**不得互相下钻**。
   - `PiAgentRunner` **禁止**直接调用 `provider.chat(...)`。
   - `AgentAttemptExecutor` **禁止**持有 lane / failover 逻辑；单次调用 = 单次"卫生 + 订阅 + prompt + 等压缩 + 收尾"。
   - `SubscribeState` **禁止**主动发起新的 provider 调用。
2. **状态机总图**：任何运行时状态必须能对应到以下之一，禁止游离态：
   `idle → queued(session lane) → queued(global lane) → attempting → streaming → compacting(optional) → completed|failed → idle`；
   控制态：`aborting` / `waiting_compaction_retry`。
3. **subscribe 生命周期**：`subscribe` 必须和 `attempt` 同生命周期（attempt 开始前挂 / attempt finally 解挂）；`SubscribeState.unsubscribed=true` 后所有后续 event **一律视为 `AbortError`**。
4. **active run 互斥**：同一 `sessionId` 在 `ActiveRunRegistry` 内**同时只能有一个** run；`clearIfMatches` 必须用 `==` 引用比较 handle，不匹配**禁止删除**（防旧 finally 误删新 run）。
5. **`queueMessage` 三种静默失败场景**：`no_active_run` / `not_streaming` / `compacting`，返回 `false` 而不抛异常。
6. **压缩重试挂起**：`compactionInFlight=true` 期间的新 run 必须等待 `pendingCompactionRetry` 解决（通过 `waitForCompactionRetry()`）后再继续；禁止直接丢弃未完成的压缩。
7. **`waitForEmbeddedPiRunEnd` 超时契约**：默认 15000ms，最小 `Math.max(100, timeoutMs)`；**超时返回 `false` 不 reject**。
8. **AbortError 优先级最高**：任一阶段抛 `AbortError`（用户 abort / attempt 切换）**立即停止 fallback**，不得触发 `FailoverError` 路径。
9. **FailoverReason 必须分类**：`ModelFallbackRunner` 捕获异常时，**必须**经 `FailoverReasonClassifier` 归类到 `{BILLING, RATE_LIMIT, AUTH, TIMEOUT, FORMAT, UNKNOWN}` 之一；`UNKNOWN` 允许但必须带 `message/status/code` 原始字段用于排障。
10. **上下文卫生五步顺序固定**（对应 `runEmbeddedAttempt` 实际顺序）：
    `sanitizeSessionHistory → validateProviderTurns(gemini/anthropic) → limitHistoryTurns → sanitizeToolUseResultPairing → replaceMessages`；
    `before_agent_start hook` 挂在此链之后，`prompt` 之前。**顺序不得调换**。
11. **ContextWindowGuard 前置**：attempt 开始前必须过 `ContextWindowGuard`，`HARD_MIN_TOKENS=16000` 命中时**不得进入 attempt**，直接抛可读错误；`defaultTokens=32000` 为回落常量。
12. **Overflow 恢复链固定**：`compactEmbeddedPiSessionDirect`（**最多 3 次**）→ `truncateOversizedToolResultsInSession` → `ContextOverflowUnresolvedException`；不得崩溃。
13. **`maxInjectedChars` 限制**：`memory_search` 工具注入的结果总字符数**必须** clamp 到 `maxInjectedChars`（默认 4000），防止记忆挤爆对话上下文。
14. **Memory scope guard**：qmd 内 `isScopeAllowed(sessionKey)` 不匹配时返回 `[]` 且**不报错**；scope 不匹配的跨 session 读取**禁止**泄漏。
15. **Memory fallback 自愈**：primary `search` 抛错 → `primaryFailed=true` + `primary.close()` + `evictCacheEntry()`（从 `QMD_MANAGER_CACHE.remove(cacheKey)` + 置 `cacheEvicted=true`） → `ensureFallback()` 懒加载 builtin；**`evictCacheEntry` 漏掉会导致永远复用失效实例**。

---

## 13. 工具策略 Pipeline 顺序（对齐 OpenClaw 文档 04 / 08 + 实战 18）

`openclaw-tools` 范围内的装配必须区分**外层 5 步**与**内层 9 步**，二者顺序均**不可配置**：

### 13.1 外层 5 步（`createOpenClawCodingTools` 装配顺序）

| Step | 作用 | 硬约束 |
|---|---|---|
| 1 | `OwnerOnlyToolPolicy.apply(tools, senderIsOwner)` | `senderIsOwner` 默认 `false`（安全 opt-in，不是 opt-out）；非 owner 工具**直接剥离**，后续 step **不得重新引入** |
| 2 | `ToolPolicyPipeline.apply(tools, innerSteps)` | 见 §13.2 内层 9 步 |
| 3 | `ToolParameterNormalizer.normalize(tool)` | **必须在 Step 4 之前**；否则 `wrapToolWithBeforeToolCallHook` 包裹后 execute 是旧的，normalize 无效 |
| 4 | `BeforeToolCallHookWrapper.wrap(tool, hookRunner)` | 写入 `BEFORE_TOOL_CALL_WRAPPED` marker（不可枚举 property），防止 `pi-tool-definition-adapter` 重复包裹 |
| 5 | `AbortSignalWrapper.wrap(tool, abortSignal)`（仅当非空） | execute 前后检查 abort，优先中断 |

### 13.2 内层 Policy Pipeline 9 步（`buildDefaultToolPolicyPipelineSteps` + `pi-tools.ts` 追加）

顺序与 `stripPluginOnlyAllowlist` 开关**精确固定**：

```
1. tools.profile (${profile})          stripPluginOnlyAllowlist=true
2. tools.provider-profile (${profile}) stripPluginOnlyAllowlist=true
3. group tools.allow                   stripPluginOnlyAllowlist=true
4. tools.global                        stripPluginOnlyAllowlist=false
5. tools.global-provider               stripPluginOnlyAllowlist=false
6. tools.agent (${agentId})            仅 agentId 非空时执行
7. tools.agent-provider (${agentId})   仅 agentId 非空时执行
8. sandbox tools.allow                 (pipeline 外追加)
9. subagent tools.allow                (pipeline 外追加)
```

- `stripPluginOnlyAllowlist=true` 的 3 步用于保护：如果 allowlist **只含插件工具名**且插件未启用，自动剥离该 allowlist，防止"配置写了但不生效导致核心工具全消失"。
- 每步输入是上一步的输出，**禁止**绕过前序 step 从"原始工具集"重新起算。
- 每一步必须携带 `PolicyProvenance.stepLabel`，用于 diagnostics 输出 `tools.profile (balanced) stripped 3 unknown entries` 这样的可读信息。

### 13.3 Hook 注入精确规则

1. **before_tool_call**（可修改 hook）：多插件按 `priority` **降序**串行；`outcome.block=true` 立即抛断；改写参数通过 `AdjustedParamsStore` 按 `toolCallId` 暂存（**必须**带 LRU 驱逐上限 `MAX_TRACKED_ADJUSTED_PARAMS`，建议 4096）；单插件参数合并 `{...originalParams, ...hookResult.params}`；多插件 merge `{params: next.params ?? acc.params, block: next.block ?? acc.block, blockReason: next.blockReason ?? acc.blockReason}`（last-write-wins）。
2. **after_tool_call**（观察 hook）：成功 + 失败两条路径都**必须** fire-and-forget 触发，携带 `durationMs`；after hook 抛错只 `log.debug`，不传播。
3. **after hook 取参**：必须通过 `AdjustedParamsStore.consumeForToolCall(toolCallId)` 取"hook 改写后参数"，不是原始 params。
4. **Schema 归一化**：所有 tool `parameters` 必须经 `ToolParameterNormalizer` 处理；根级 `anyOf / oneOf` **必须**展开为 concrete schema，兼容 OpenAI 拒绝根级 union 的行为。
5. **`apply_patch` provider gating**：对**非 OpenAI** provider 默认关闭。
6. **Subagent 默认 deny 清单**：子 Agent 工具策略默认 deny：`sessions_spawn / sessions_send / sessions_list / sessions_history / gateway / agents_list / cron / memory_search / memory_get`，防止子 Agent 拿到主控面编排能力。

### 13.4 审批状态机精确规则（`ExecApprovalManager`）

1. **Decision 四态**：`ALLOW_ONCE / ALLOW_ALWAYS / DENY / null(=timeout)`；**`null` 不是 reject**，调用方必须判 null。
2. **`RESOLVED_ENTRY_GRACE_MS = 15000`**：已决条目保留 15 秒，保证两阶段调用里 `request` 先回 `accepted` → `waitDecision` 后来仍能拿到 decision。
3. **幂等注册**：同 id 未决的 `register` 返回**同一** Future；同 id 已决再 `register` 抛 `ILLEGAL_STATE`。
4. **Race 防御**：`exec.approval.request` 时序必须**先 `register`，再响应 accepted**；顺序反了会导致客户端 `waitDecision` 时 entry 尚未注册。
5. **双决幂等**：同 id 已决再 `resolve` 返回 `false`，不抛。
6. **审批判定**：`ExecApprovalPolicy.requiresApproval(ctx)` 精确等价于 `ask=="always" || (ask=="on-miss" && security=="allowlist" && (!analysisOk || !allowlistSatisfied))`。

---

## 14. 并发 Lane 状态机精确规则（对齐 OpenClaw 文档 03 + 实战 21）

`openclaw-session-lanes` 必须满足：

1. **`LaneState` 6 字段必须齐全**：`lane / queue / activeTaskIds / maxConcurrent / draining / generation`；缺一视为错误实现。
2. **`QueueEntry.warnAfterMs` 默认 2000ms**：`waitedMs >= warnAfterMs` 触发 `onWait` + `log.warn`；**只告警不取消任务**。
3. **双层排队**：`enqueueInSessionLane` **外层** + `enqueueInGlobalLane` **内层**；局部有序 + 全局限流是本质。
4. **Lane 名称规则**：
   - `resolveSessionLane(key)`：空回落 `MAIN`；已含 `session:` 前缀保持（幂等），否则拼 `session:${cleaned}`
   - `resolveGlobalLane(lane)`：空回落 `CommandLane.MAIN`
5. **`drainLane` 防重入**：`if (draining) return`；`draining` 必须在 `finally` 置 `false`，否则永远卡住。
6. **失败也要 pump**：任务失败 reject 后必须 `pump()` 继续，否则队列饿死。
7. **Probe Lane 静默**：`lane.startsWith("auth-probe:") || lane.startsWith("session:probe-")` 时任务失败**不打错误日志**，只内部诊断。
8. **`resetAllLanes` 三步严格**：`generation++` → `activeTaskIds.clear()` → 保留 queue 重新 `drainLane`；generation 机制防止旧 finally 回写污染新状态。
9. **`waitForActiveTasks`**：`POLL_INTERVAL_MS=50`；**只等进入时的 active 快照**，不等新进来的任务；超时 `drained=false` 不 reject。
10. **`clearCommandLane`**：只取消 queue 里未开始的，抛 `CommandLaneClearedException`；**不影响** 已 active 的任务。
11. **`setCommandLaneConcurrency` 实时生效**：写完 `maxConcurrent` 立刻 `drainLane`，不等下条消息；热重载再次调用同一方法实时对齐。

---

## 15. 插件具名能力冲突治理（对齐 OpenClaw 文档 15）

`openclaw-plugins` 注册能力时必须遵守以下硬规则：

1. **源优先级**：`CONFIG > WORKSPACE > GLOBAL > BUNDLED`；高优先级源命中后，低优先级源以相同 `realpath` 命中则**整体忽略**并落 diagnostics，**禁止**部分合并。
2. **具名能力冲突硬拒绝**：`registerGatewayMethod / registerHttpRoute / registerCommand / registerTool` 重名必须抛出，**禁止** last-write-wins。
3. **Hook 可多注册**：`registerHook` 允许多个插件挂同一个 hook，按 priority 执行；这是唯一允许"同名"的注册点。
4. **MemorySlot 独占**：`memory.slot` 同一 slot 仅允许一个插件占用，冲突硬拒绝。
5. **Diagnostics 强制**：所有 loader 错误 / manifest 冲突 / schema 错误 / 注册冲突**必须**进 `PluginRegistry.diagnostics`，并通过 `/actuator/plugins` 暴露只读查询。
6. **加载完成后冻结**：`PluginContext` 在 `ContextRefreshedEvent` 后**禁止**再动态替换已有能力；动态变更必须通过重启 / 热重载通道。

---

## 16. Hook Runner 调度精确规则（对齐 OpenClaw 文档 13 + 实战 20）

`openclaw-hooks-runtime` 的 `HookRunner` 必须：

1. **排序方向**：按 `priority` **降序**（高优先级先执行），同 priority 按注册时间稳定排序；**不得**升序。
2. **两种执行模型**：
    - `runVoidHook(name, evt, ctx)` → **并行**（`CompletableFuture.allOf`）；用于 `agent_end / gateway_start / after_tool_call` 等观察型 hook。
    - `runModifyingHook(name, evt, ctx, merge)` → **顺序串行**；用于 `before_agent_start / before_tool_call` 等可修改 hook；每 handler 返回 delta，`acc = merge(acc, delta)`。
3. **默认 `catchErrors=true`**：单个 hook 抛错只 `log.error` **不中断主链路**；`catchErrors=false` 仅调试用途，生产禁用。
4. **注入点固定位置**：
    - `before_agent_start`：`runEmbeddedAttempt` 在 session prompt **之前**调用，可 prepend 额外上下文。
    - `before_tool_call`：tool `execute` **之前**调用，可改参 / 阻断。
    - `after_tool_call`：tool `execute` **之后**（含失败 catch）调用，fire-and-forget。
    - `run_agent_end`：attempt 退出时调用，上报 `success/error/duration`。
5. **禁止"执行后 before 检查"**：不要把 before 检查放到 execute 之后；注入点位置一旦错位等同失效。
6. **冲突注册产出 diagnostics**：不做静默覆盖；被拒绝的注册必须进 `PluginRegistry.diagnostics`。
