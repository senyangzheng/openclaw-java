# 全功能验证指引（M0 / M1 / M2 / M3 预备阶段）

> 本文档覆盖截至 **M3 / A1、A2、A4、A5、B6** 已落地能力的端到端验证流程。
> 按 **准备 → 编译 → 单元测试 → 集成验证 → 运行时验证（CLI / Web / 插件 / Provider / Failover / 热更新 / 凭据持久化）**
> 的顺序执行；每一步都给出 **操作命令 + 预期现象 + 失败排查线索**，方便逐项勾选。

---

## 0. 环境准备

| 项 | 要求 | 校验 |
|----|------|------|
| JDK | 21 (LTS) | `java -version` 输出 `21.x` |
| Maven | ≥ 3.9 | `mvn -v` |
| MySQL | 8.x（仅 M2.3 / M2.4 jdbc 场景需要） | 可连通 `localhost:3306` |
| 端口 | 8080（HTTP Web Gateway） | `lsof -i :8080` 无占用 |

```bash
# 建议固定 JDK（例如）：
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
java -version
```

> 如果没有 MySQL，本指引 **1~3 节**（CLI、Web Echo、Qwen）仍可全流程运行，内存态 fallback 会自动生效。

---

## 1. 全仓构建 + 单元测试

### 1.1 构建

```bash
cd /Users/admin/IdeaProjects/openclaw-java
mvn -T 1C -DskipTests clean package
```

**预期**：

- `BUILD SUCCESS`，27 个模块全部 SUCCESS（含 `openclaw-bootstrap` repackage）
- 产物：`openclaw-bootstrap/target/openclaw-bootstrap-0.1.0-SNAPSHOT.jar`
- 产物：`openclaw-cli/target/openclaw-cli-0.1.0-SNAPSHOT.jar`

### 1.2 单元测试

```bash
mvn -T 1C test
```

**预期**：

- `Tests run: 165, Failures: 0, Errors: 0, Skipped: 0`
- 全部 27 个模块 SUCCESS

> ⚠️ 如果 `openclaw-providers-qwen` / `openclaw-providers-google` 的 **WireMock** 测试出现 `Operation not permitted: localhost/...`，说明当前 shell 处于受限沙箱（如 Cursor 默认环境）。这不是代码问题；在普通 shell 中执行即可。

---

## 2. M0：脚手架与核心内核

> 目标模块：`openclaw-common` / `openclaw-logging` / `openclaw-routing` / `openclaw-channels-core` / `openclaw-gateway-core` / `openclaw-sessions` / `openclaw-providers-api`

### 2.1 Bootstrap 冒烟

```bash
java -jar openclaw-bootstrap/target/openclaw-bootstrap-0.1.0-SNAPSHOT.jar
```

**预期日志**（关键行，顺序不敏感）：

```
Started OpenClawApplication in X.X seconds
plugins.loaded id=hello version=0.1.0 source=bundled class=com.openclaw.plugins.demo.HelloPlugin
plugin.hello.hook.registered name=before_agent_start handler=plugin.hello.command
Tomcat started on port 8080
```

`Ctrl+C` 优雅退出，应看到：

```
plugins.unloaded id=hello
```

**失败排查**：

- `release version 21 not supported` → 未切到 JDK21。
- `Port 8080 in use` → `export OPENCLAW_PORT=19090` 后重试。

### 2.2 Actuator Health

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

**预期**：`{"status":"UP","groups":["liveness","readiness"]}`

---

## 3. M1：Web 通道 + Gateway 打通

> 目标模块：`openclaw-channels-web` / `openclaw-gateway-api` / `openclaw-gateway-core` / `openclaw-cli` / `openclaw-commands` / `openclaw-auto-reply`

### 3.1 Web Echo（Provider 未启用时）

在 **未导出 `OPENCLAW_QWEN_ENABLED`** 的情况下启动 bootstrap。

**非流式**：

```bash
curl -s -XPOST http://localhost:8080/api/channels/web/messages \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"u1","conversationId":"c1","text":"用一句话自我介绍"}' | jq .
```

**预期**：`text` 字段为 `[mock] 用一句话自我介绍`，说明 `EchoMockProviderClient` 生效。

**SSE 流式**：

```bash
curl -N -XPOST http://localhost:8080/api/channels/web/messages/stream \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"accountId":"u1","conversationId":"c1","text":"hi"}'
```

**预期**：看到 `event: delta` / `event: done` 顺序流出；浏览器形式也可用。

### 3.2 CLI（`openclaw-cli`）

```bash
# 1. 查看已加载通道
java -jar openclaw-cli/target/openclaw-cli-0.1.0-SNAPSHOT.jar channels

# 2. 一句话 chat（走 Web Gateway，要求 bootstrap 已在跑）
java -jar openclaw-cli/target/openclaw-cli-0.1.0-SNAPSHOT.jar chat --text "hi from cli"
```

**预期**：`channels` 输出包含 `web`；`chat` 输出 `[mock] hi from cli`。

### 3.3 ChatCommand 分发（保留）

> M3 已把 `ChatCommand` SPI 迁移到 `HookRunner#before_agent_start`，`HelloPlugin` 现在以 hook 形式注册。

```bash
curl -s -XPOST http://localhost:8080/api/channels/web/messages \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"u1","conversationId":"c1","text":"/hello alice"}' | jq .
```

**预期**：

- 响应 `text` 包含 `alice` 与 `hello plugin`
- 日志出现 `plugin.hello.shortcircuit replyLen=...`
- **不会**触达 `EchoMockProviderClient`（这是 hook short-circuit 的证明）

---

## 4. M2.2：Provider 插件体系（Qwen / Google） + Failover（M3 / A4）

> 目标模块：`openclaw-providers-registry` / `openclaw-providers-qwen` / `openclaw-providers-google` / `openclaw-providers-api` / `FailoverProviderDispatcher`

### 4.1 单 Provider（Qwen）联调

```bash
export OPENCLAW_QWEN_ENABLED=true
export DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx    # 真实 DashScope key
java -jar openclaw-bootstrap/target/openclaw-bootstrap-0.1.0-SNAPSHOT.jar
```

```bash
curl -s -XPOST http://localhost:8080/api/channels/web/messages \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"u1","conversationId":"c1","text":"用一句话自我介绍"}' | jq .
```

**预期**：`text` 是真实模型回复（非 `[mock]` 前缀）；日志出现 `provider.dispatch.success provider=qwen`。

### 4.2 Failover 验证（新增：M3 / A4）

> **两个常识先对齐**，否则会跟我一样踩坑：
> 1. `EchoMockProviderClient` 只在 **没有任何真实 provider** 注册时才会作为 fallback 进入 registry；
>    `qwen.enabled=true` 时 mock 不会在 registry 里，所以 qwen 失败 **不会**降级到 mock。
> 2. `FailoverReason` 的输出是 **短码**：`auth` / `timeout` / `rate_limit` / `server_error` /
>    `client_error` / `network` / `cooldown` / `aborted` / `unknown`。其中 `auth` 和 `client_error`
>    是 **非重试** 的——同一请求当即停止，不会再尝试下一家。

#### 4.2.a 场景 1：单 provider 401（非重试路径）

```bash
export OPENCLAW_QWEN_ENABLED=true
export DASHSCOPE_API_KEY=sk-invalid-force-fail
java -jar openclaw-bootstrap/target/openclaw-bootstrap-0.1.0-SNAPSHOT.jar
```

```bash
curl -s -XPOST http://localhost:8080/api/channels/web/messages \
  -H 'Content-Type: application/json' -d '{"accountId":"u1","conversationId":"c1","text":"ping"}' | jq .
```

**预期响应**：`text` 形如 `[error:E_PROVIDER] ...`；HTTP 仍 200（错误封装在业务协议里，由 `AutoReplyPipeline` 统一翻译为 `E_PROVIDER`）。

**预期日志**（关键行，按出现顺序；`reason=auth` 是小写短码）：

```
providers.registry.bootstrapping providers=[qwen] preferredOrder=[google, qwen] vault=mem mockFallback=false
qwen.stream.error
providers.registry.failure providerId=qwen consecutive=1 cooldownUntil=... reason=...401 Unauthorized...
providers.dispatch.stream.failure providerId=qwen reason=auth msg=Qwen stream failed: 401 Unauthorized ...
lane.task failed dispatcher=session lane=session:web:u1:c1 ... err=com.openclaw.providers.registry.FailoverError: all providers exhausted: qwen=auth
agent.run.lane.error handle=... err=com.openclaw.providers.registry.FailoverError: all providers exhausted: qwen=auth
agent.run.end handle=... state=FAILED cleared=true
auto-reply.outbound.error code=E_PROVIDER replyLen=...
```

> 注意：`FailoverError: all providers exhausted: qwen=auth` 末尾是 `=<reason.code()>`，不是中文。`auth` / `client_error` **非重试**是设计如此——鉴权问题在同一 key 上重试下家没意义。

#### 4.2.b 场景 2：多 provider + 中间故障真实降级

需要 ≥ 2 个 provider，且链路里至少 1 个 **retryable 失败**（`unknown` / `server_error` / `timeout` / `network` / `rate_limit`）+ 1 个能真实返回的 provider 垫底。`auth` / `client_error` 依然会直接停。

**推荐最简配置**（不用假 URL，直接让 google 鉴权 400）：

```bash
# ⚠️ qwen 用 4.1 验证过的真实 key；google 用任意假 key
export OPENCLAW_QWEN_ENABLED=true
export DASHSCOPE_API_KEY=sk-<真实可用的 qwen key>       # 必须真实可用！
export OPENCLAW_GOOGLE_ENABLED=true
export GEMINI_API_KEY=anything                          # 故意假 key → 400 → reason=unknown (retryable)
java -jar openclaw-bootstrap/target/openclaw-bootstrap-0.1.0-SNAPSHOT.jar
```

发一次请求，**预期日志**：

```
providers.registry.bootstrapping providers=[google, qwen] preferredOrder=[google, qwen] ...
google.stream.error ... 400 Bad Request ...
providers.registry.failure providerId=google consecutive=1 cooldownUntil=...
providers.dispatch.stream.failure providerId=google reason=unknown msg=...
# 自动走下一家，qwen 成功，不再有 qwen.stream.error / providers.dispatch.failure
auto-reply.outbound state=DONE replyLen=<非零>
```

响应 JSON 的 `text` 是 qwen 生成的真实回复（非 `[error:E_PROVIDER]`）。

> **最易踩的坑**：如果 qwen 还用着 4.2.a 的 `sk-invalid-force-fail`，会看到：
> ```
> providers.dispatch.stream.failure providerId=google reason=unknown ...
> providers.dispatch.stream.failure providerId=qwen   reason=auth ...
> FailoverError: all providers exhausted: google=unknown, qwen=auth
> ```
> 这其实 **已经证明 failover 走完了整条链**——`attempts=[google:unknown, qwen:auth]` 里
> 两个 provider 都在，说明 dispatcher 在 google 失败后确实尝试了 qwen，
> 只是 qwen 也失败了。想看到 "成功接手"，换真实 qwen key 即可。

之后 5s 内再发一次，google 会被跳过（DEBUG 级别需打开 `logging.level.com.openclaw=DEBUG` 才可见）：

```
providers.dispatch.stream.skip.cooldown providerId=google
# 直接从 qwen 开始
```

#### 4.2.c 场景 3：全部耗尽

把 4.2.b 里 qwen 也换成 bad key，**预期**：

```
providers.dispatch.stream.failure providerId=google reason=network ...
providers.dispatch.stream.failure providerId=qwen reason=auth ...
providers.dispatch.stream.exhausted attempts=[google:network,qwen:auth]
auto-reply.outbound.error code=E_PROVIDER ...
```

### 4.3 Registry 冷却

失败记账逻辑在 `DefaultProviderRegistry.reportFailure`：

- 首次失败：`consecutive=1`，`cooldownUntil = now + initial-delay`（默认 5s）
- 再失败：`cooldownUntil += initial-delay * multiplier^n`，封顶 `max-delay`（默认 5m）
- 成功一次：`consecutive` 清零，`cooldownUntil` 解除

冷却期间命中时的日志（DEBUG 级别）：

```
providers.dispatch.stream.skip.cooldown providerId=qwen
# 或非流式路径
providers.dispatch.skip.cooldown providerId=qwen
```

若所有候选都在冷却且没有 retryable 目标，会看到 `providers.dispatch.stream.exhausted attempts=[...:cooldown,...:cooldown]`。

---

## 5. M2.3：凭据持久化 + 加密

> 目标模块：`openclaw-secrets`

### 5.1 内存模式（默认）

无需任何额外配置，`AuthProfileVault` 使用 `InMemoryAuthProfileVault`。

### 5.2 JDBC + 信封加密

```bash
# 生成一次性 KEK（base64, 32 字节）
openssl rand -base64 32
# 示例输出：
#   2sK8rY0Vz2... (贴到下方 OPENCLAW_SECRETS_CRYPTO_KEK_BASE64)

export OPENCLAW_DB_URL='jdbc:mysql://localhost:3306/openclaw?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8&allowPublicKeyRetrieval=true'
export OPENCLAW_DB_USERNAME=root
export OPENCLAW_DB_PASSWORD=12345678
export OPENCLAW_SESSIONS_STORE=jdbc
export OPENCLAW_SECRETS_STORE=jdbc
export OPENCLAW_SECRETS_CRYPTO_KEK_BASE64='<paste-here>'
export OPENCLAW_FLYWAY_ENABLED=true

java -jar openclaw-bootstrap/target/openclaw-bootstrap-0.1.0-SNAPSHOT.jar
```

**预期**：

- Flyway 执行 `V1__init.sql`、`V2__auth_profile.sql` 等迁移
- `oc_auth_profile` 表存在，`payload` 为密文（`hex`/`base64` 格式看不出明文 key）

**入/读一次凭据**（SQL 层面不直观，建议跑 `com.openclaw.secrets.JdbcAuthProfileVaultTest`）：

```bash
mvn -pl openclaw-secrets test
```

---

## 6. M2.4：插件系统 + 配置热更新

### 6.1 默认 Demo 插件

3.3 节的 `/hello alice` 即是 demo 插件落地验证。额外校验：

```bash
# 禁用后应该回到 mock/LLM。
# ⚠️ openclaw.plugins.exclude 是 List<String>：-D 的 key=value 形式 Spring Boot 不会自动拆
# 成列表。必须用下面任一种：
#   1) 命令行 --openclaw.plugins.exclude=hello        （最简单，推荐）
#   2) -Dopenclaw.plugins.exclude[0]=hello            （索引语法）
#   3) 环境变量 OPENCLAW_PLUGINS_EXCLUDE=hello        （Spring 的 relaxed binding）
java -jar openclaw-bootstrap/target/openclaw-bootstrap-0.1.0-SNAPSHOT.jar \
  --openclaw.plugins.exclude=hello
```

启动日志会出现：

```
plugins.discover.filtered id=hello reason=in-exclude
plugins.discovered count=0
```

再调——**关键：换一个没用过的 conversationId**，避免 JDBC 里累积的历史消息让 LLM "背答案"：

```bash
curl -s -XPOST http://localhost:8080/api/channels/web/messages \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"u1","conversationId":"exclude-test-1","text":"/hello alice"}' | jq .
```

**判定标准（看日志，不看回复内容）**：

- ✅ 启动日志出现 `plugins.discover.filtered id=hello reason=in-exclude`
- ✅ 请求链路**没有** `plugin.hello.shortcircuit` 这行
- ✅ `agent.run.end state=COMPLETED`（由 provider 真实返回，而非 hook 拦截）

> 为什么不能用 `conversationId=c1`：sessions 用 JDBC 持久化，c1 里可能存着之前插件开启时 hook 留下的 assistant 消息 `"hello, alice — from the openclaw hello plugin"`，LLM 看到这种模式会原样续写，导致**响应文本看起来像 hook 生效**——其实是历史污染。要么换新的 conversationId，要么 `DELETE FROM oc_session WHERE session_id='web:u1:c1';` 清掉。

### 6.2 插件治理（新增：M3 / A5）

关键点：

| 能力类型 | `CapabilityType` | 冲突策略 |
|---------|-----------------|---------|
| Gateway 方法 | `GATEWAY_METHOD` | HARD_REJECT（启动即失败 / 记入 diagnostics） |
| HTTP 路由 | `HTTP_ROUTE` | HARD_REJECT |
| 聊天命令 | `COMMAND` | HARD_REJECT |
| 工具 | `TOOL` | HARD_REJECT |
| Hook | `HOOK` | **ALLOW_MULTIPLE** |

**治理单元测试**（已内置）：

```bash
mvn -pl openclaw-plugins test -Dtest=CapabilityRegistryTest,PluginDiagnosticsTest,PluginLoaderTest
```

**预期**：15 tests, 0 failures。

**活体证明**：启动后查看日志——`HelloPlugin` 通过新 API 登记了 HOOK：

```
plugin.capability.registered type=HOOK name=before_agent_start plugin=hello totalForName=1
```

### 6.3 配置热更新

```bash
# 1. 准备一个受监听文件（示例路径）
mkdir -p /tmp/openclaw-hot
echo 'openclaw.profile: prod-candidate' > /tmp/openclaw-hot/runtime.yml

export OPENCLAW_HOT_RELOAD_ENABLED=true
# 通过系统属性传列表，或写到 application-dev.yml 的 openclaw.config.hot-reload.paths
java -Dopenclaw.config.hot-reload.paths=/tmp/openclaw-hot/runtime.yml \
  -jar openclaw-bootstrap/target/openclaw-bootstrap-0.1.0-SNAPSHOT.jar
```

另一终端修改文件：

```bash
echo 'openclaw.profile: prod-hotfix' > /tmp/openclaw-hot/runtime.yml
```

**预期日志**（两条，`debounce=500ms` 之后才会出第二条）：

```
config.hotreload.event path=/tmp/openclaw-hot/runtime.yml kind=ENTRY_MODIFY
config.reload path=/tmp/openclaw-hot/runtime.yml kind=modify listeners=N
```

- 第 1 行来自 `ConfigWatcher`——WatchService 捕获到原始事件（每次保存都会有 1~2 条 `ENTRY_MODIFY`）。
- 第 2 行来自 `ConfigReloadPublisher`——debounce 窗口结束后，把事件广播给所有 `HotReloadable` Bean。

> **`listeners=0` 不是 bug**：当前工程里没有 `HotReloadable` SPI 实现，所以无消费者。只要看到这两行的时间差接近 `debounce` 值（500ms），热更新管道就是健康的。后续哪个模块真要订阅，把 Bean 实现 `HotReloadable` 注册进来，`listeners` 数就会 >0。

---

## 7. M3 预备阶段（A1 / A2 / A4 / A5 / B6）已落地能力

### 7.1 AutoReplyPipeline 瘦身（A1 + A2）

不再直接触达 `ProviderDispatcher` / `HookRunner`，改走 `PiAgentRunner.submit()`，内部由 `SessionLaneCoordinator` 双层队列串行化 **agent 执行段**。

#### 7.1.a 跨会话并发不互相阻塞（主验收点）

```bash
for i in $(seq 1 5); do
  curl -s -XPOST http://localhost:8080/api/channels/web/messages \
    -H 'Content-Type: application/json' \
    -d "{\"accountId\":\"u1\",\"conversationId\":\"parallel-sess-$i\",\"text\":\"hi\"}" &
done; wait
```

**预期**：5 个响应全部 200；日志里可见 5 条 `session-lane.enqueue` / `session-lane.run.start`，**没有**互相等待（5 个会话各占独立 session-lane，只共享一个 global-lane）。

#### 7.1.b 同会话并发：仅验证"不崩溃"

> ⚠️ **已知遗留竞态**：当前 `AutoReplyPipeline` 的 `sessions.loadOrCreate(...) → session.append(user) → agentRunner.submit(...) → session.append(assistant) → sessions.save(...)` 这一串 **session I/O 位于 session-lane 之外**，lane 只保护 `submit()` 内部的 attempt 执行。
>
> 这意味着同 `sessionKey` 并发时：
> - JDBC store 下多个线程同时 `loadOrCreate` 会撞 `uk_session_key` 唯一键——**已由 `JdbcSessionRepository.insertEntity` 捕获 `DuplicateKeyException` 软兜底修复**，不再硬崩溃；
> - 但多个线程各自持有独立 `Session` 对象并行 append，`save()` 用 `countBySession` 做 append-only 判断，仍可能在后到达的线程上出现 **消息静默丢失**（只 `touchUpdatedAt` 不写入）。
>
> 根治需要把整段 session I/O 搬进 session-lane，已登记为 **M3.3 遗留任务**（见 `04-milestones.md`）。在 M3 落地前，不建议在生产流量下针对同一 sessionKey 发起真正的并发请求——上层通道（CLI / Web 单会话 UI）本身也不会这么做。

```bash
# 同一会话 5 并发：只验证"5 个请求都拿到 200 响应，不出现 500 / 唯一键崩溃"
for i in $(seq 1 5); do
  curl -s -XPOST http://localhost:8080/api/channels/web/messages \
    -H 'Content-Type: application/json' \
    -d "{\"accountId\":\"u1\",\"conversationId\":\"same-session\",\"text\":\"msg-$i\"}" &
done; wait
```

**预期**：
- 5 个响应都返回 JSON（非 500）；
- 日志里**可以**看到 `session.insert.duplicate key=web:u1:same-session — re-selecting after concurrent create` —— 这恰好证明 A1 的并发保护生效：首次创建该 session 时的竞争被软兜底化解；
- 不再出现 `SQLIntegrityConstraintViolationException: Duplicate entry ... for key 'oc_session.uk_session_key'` 的硬崩溃栈。

**如果你想观察严格 FIFO**：请每次测试使用**新的** `conversationId`（第一次创建 session 后并发即可走上软兜底路径），或先手动在 MySQL 里预创建一行 `oc_session`（插入 `session_key='web:u1:same-session'`），让并发请求都命中 UPDATE 分支。这两种做法都能避开"首次创建竞争"，但依然无法修复上面提到的消息丢失问题——那是 M3.3 的正式交付项。

### 7.2 Provider Failover（A4）— 参见 4.2。

### 7.3 插件治理（A5）— 参见 6.2。

### 7.4 工具系统骨架（B6）

```bash
mvn -pl openclaw-tools-api,openclaw-tools-runtime test
```

**预期**：`Tests run: X, Failures: 0`（包含 `ToolRegistryTest` / `AdjustedParamsStoreTest` / `BeforeToolCallHookWrapperTest`）。

> 当前仅骨架，**运行时尚未对外暴露任何工具调用端点**；完整工具执行链路会在 M3 / B7+ 打通。

---

## 8. 一键验收脚本（可选）

```bash
#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home

echo "== build =="
mvn -T 1C -DskipTests clean package

echo "== unit test =="
mvn -T 1C test

echo "== launch =="
java -jar openclaw-bootstrap/target/openclaw-bootstrap-0.1.0-SNAPSHOT.jar &
APP_PID=$!
trap "kill $APP_PID || true" EXIT
sleep 6

echo "== health =="
curl -fs http://localhost:8080/actuator/health

echo "== echo =="
curl -fs -XPOST http://localhost:8080/api/channels/web/messages \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"u1","conversationId":"c1","text":"hi"}'

echo "== /hello plugin =="
curl -fs -XPOST http://localhost:8080/api/channels/web/messages \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"u1","conversationId":"c1","text":"/hello alice"}'

echo "✓ all green"
```

---

## 9. 清单（勾选版）

- [ ] 1.1 全仓 `mvn clean package` 成功（27 模块）
- [ ] 1.2 `mvn test` 165 tests，0 failures
- [ ] 2.1 bootstrap 正常启动、优雅退出
- [ ] 2.2 `/actuator/health` 返回 UP
- [ ] 3.1 Web Echo 非流式 / SSE 流式可用
- [ ] 3.2 CLI `channels` + `chat` 可用
- [ ] 3.3 `/hello alice` hook short-circuit 生效
- [ ] 4.1 Qwen 联调成功
- [ ] 4.2.a 单 provider 401：日志出现 `providers.dispatch.stream.failure providerId=qwen reason=auth` + `FailoverError: all providers exhausted: qwen=auth`，响应 `[error:E_PROVIDER] ...`
- [ ] 4.2.b 多 provider retryable 失败：看到下一家接手成功
- [ ] 4.2.c 全部耗尽：`providers.dispatch.stream.exhausted attempts=[...]`
- [ ] 4.3 冷却：再次调用出现 `providers.dispatch.stream.skip.cooldown`（需开启 `logging.level.com.openclaw=DEBUG`）
- [ ] 5.2 JDBC + 信封加密的 `oc_auth_profile` payload 为密文
- [ ] 6.1 `exclude=hello` 后 `/hello` 回落到 mock
- [ ] 6.2 `CapabilityRegistryTest` 5 类冲突全部通过
- [ ] 6.3 修改受监听文件，日志出现 `config.reload.dispatched`
- [ ] 7.1 同会话 5 并发 FIFO / 跨会话并发无阻塞
- [ ] 7.4 `openclaw-tools-*` 测试通过

全部勾选后即可继续 M3 的剩余任务（A3 / B7+ / 状态机 / 记忆系统 / 审批等）。
