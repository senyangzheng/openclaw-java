# 02 · Maven 多模块划分与依赖拓扑

---

## 1. 聚合根布局

```
openclaw-java/
├── pom.xml                         聚合 + 父 POM（dependencyManagement / plugins 锁版本）
├── bom/pom.xml                     （可选）对外发布用 BOM
│
├── openclaw-common/                基础工具、Fastjson2 封装、错误码
├── openclaw-logging/               SLF4J + Logback + MDC
├── openclaw-config/                @ConfigurationProperties + 热重载
├── openclaw-secrets/               密钥/凭据
├── openclaw-sessions/              会话存储（MyBatis-Plus + MySQL + Caffeine）
├── openclaw-routing/               路由表 + SessionKey
│
├── openclaw-providers-api/         Provider SPI
├── openclaw-providers-google/      Google Gemini（首期必做）
├── openclaw-providers-qwen/        Qwen（首期必做）
├── openclaw-providers-registry/    注册 / 选路 / cooldown / auth-profile
│
├── openclaw-plugin-sdk/
├── openclaw-plugins/
│
├── openclaw-memory/                记忆 / 上下文快照
├── openclaw-context-engine/        上下文引擎（压缩/截断）
│
├── openclaw-tools/                 工具系统（Tool / ToolRegistry / ToolPolicyPipeline / before&after hook）
├── openclaw-skills/                skills 快照（workspace 合并 + SKILL.md watcher + env overrides + remote cache）
├── openclaw-agents-core/           Agent 调度骨架（PiAgentRunner 只调度 / AttemptExecutor 单事务 / SubscribeState / ActiveRunRegistry）
├── openclaw-agents-fallback/       FailoverError + 分类器 + resolveFallbackCandidates + runWithModelFallback + AuthProfileRotator
├── openclaw-agents-subagent/       SessionsSpawnTool / SessionsSendTool / SubagentRegistry / SubagentAnnounceFlow
├── openclaw-hooks-runtime/         统一 Hook Runner（priority / void 并行 / modifying 串行 merge / catchErrors），供 agents/tools/approval/gateway 复用
├── openclaw-auto-reply/            自动回复流水线（M3 起内部走 PiAgentRunner）
├── openclaw-session-lanes/         双层 Lane 并发队列（sessionLane × globalLane × Cron/Main/Subagent）
├── openclaw-stream/                流式订阅（AgentEvent sealed 类型 + ProviderChunk→AgentEvent 翻译器）
├── openclaw-approval/              审批状态机（allow-once / allow-always / deny + timeout=null + 15s grace）
│
├── openclaw-gateway-api/
├── openclaw-gateway-core/
├── openclaw-gateway-ws/
├── openclaw-gateway-http/
├── openclaw-gateway-openai-compat/ /v1/chat/completions（仅文本 + 流式）
├── openclaw-gateway-hooks/         hooks + tools-invoke HTTP 入口（底层调用 openclaw-hooks-runtime）
│
├── openclaw-cli/
├── openclaw-commands/
│
├── openclaw-channels-core/         ChannelAdapter SPI
├── openclaw-channels-web/          Web/HTTP + WebSocket 通道（唯一实现）
│
├── openclaw-security/              Spring Security + 审计
├── openclaw-cron/                  ElasticJob-Lite 定时
├── openclaw-daemon/                守护进程
├── openclaw-browser/               浏览器自动化（Playwright for Java）
├── openclaw-hooks/                 事件钩子
├── openclaw-i18n/                  国际化
│
├── openclaw-server-runtime-config/ 运行时热重载（reload 分类 HOT_RELOAD/NEEDS_RESTART + GatewayIdleGuard + buildGatewayReloadPlan）
├── openclaw-server-restart-sentinel/ 重启哨兵文件（区分主动重启 vs 崩溃重启）
├── openclaw-server-tailscale/      Tailscale 侧车
├── openclaw-server-discovery/      节点发现
├── openclaw-server-lanes/          Lane 运行时治理
├── openclaw-server-model-catalog/  模型目录
├── openclaw-server-session-key/    会话密钥
├── openclaw-server-startup-memory/ 启动期记忆预热
│
└── openclaw-bootstrap/             最终可启动模块（@SpringBootApplication）

# ⛔ 当前范围内 **不建立** 的模块（延后或永久收敛）：
#   openclaw-providers-openai / anthropic / github-copilot
#   openclaw-media / openclaw-media-understanding / openclaw-link-understanding
#   openclaw-tts / openclaw-process
#   openclaw-gateway-openresponses
#   openclaw-channels-telegram / discord / slack / whatsapp / line / signal / imessage
#   openclaw-agents（已拆分为 agents-core / agents-fallback / agents-subagent；无单体 agents 模块）
#   openclaw-hooks（已并入 openclaw-hooks-runtime）
```

---

## 2. 依赖层级（高层 → 底层）

```
                        openclaw-bootstrap
                               │
         ┌────────┬────────────┼─────────────┬──────────┬──────────┐
         │        │            │             │          │          │
   gateway-*   channels-*    auto-reply     cli      cron      daemon
         │        │            │             │          
         └────┬───┴──────┬─────┘             │          
              │          │                   │
   agents-subagent  tools / skills      commands
         │              │                   │
    agents-core ───── hooks-runtime         │
         │              │                   │
  agents-fallback  session-lanes / stream / approval
         │              │
         └──── context-engine / memory ────┘
                   │
   ┌───────────────┼────────────────┐
   │               │                │
providers-registry  media          plugins
   │               │                │
providers-*   media-*          plugin-sdk
   │
providers-api
   │
   ├───────────────┬────────────────┬──────────────┬────────────┐
   │               │                │              │            │
 sessions        routing         config         secrets      security
                                      │
                                   logging
                                      │
                                   common
```

---

## 3. 各模块依赖细则（节选）

> 原则：**层只能向下依赖，绝不反向**；跨层访问必须通过 SPI 接口。

### 3.1 基础层

| 模块 | 主要依赖 |
|---|---|
| `openclaw-common` | **`com.alibaba.fastjson2:fastjson2`** + `slf4j-api` + `jakarta.validation-api` |
| `openclaw-logging` | `common` + `logback-classic` + `logstash-logback-encoder` |
| `openclaw-config` | `common` + `spring-boot-starter` + `snakeyaml` |
| `openclaw-secrets` | `common` + `bouncycastle`（AES-GCM） |
| `openclaw-sessions` | `common` + `mybatis-plus-spring-boot3-starter` + `mysql-connector-j` + `caffeine` + `flyway-core` + `flyway-mysql` |
| `openclaw-routing` | `common` + `sessions` |

### 3.2 Provider 层

| 模块 | 主要依赖 |
|---|---|
| `openclaw-providers-api` | `common` |
| `openclaw-providers-google` | `providers-api` + `spring-boot-starter-webflux`（Gemini 流式 SSE） |
| `openclaw-providers-qwen` | `providers-api` + `spring-boot-starter-webflux` |
| `openclaw-providers-registry` | `providers-api` + `config` + `sessions`（cooldown / lastUsed 持久化） |

### 3.3 Agent / 运行时层

| 模块 | 主要依赖 | 说明 |
|---|---|---|
| `openclaw-hooks-runtime` | `common` | Hook Runner（priority + void 并行 + modifying 串行 merge + catchErrors）；被 agents/tools/approval/gateway 复用 |
| `openclaw-tools` | `common` + `providers-api` + `hooks-runtime` + `json-schema-validator` | Tool SPI + ToolPolicyPipeline（顺序固定）+ before/after hook + AdjustedParamsStore |
| `openclaw-skills` | `common` + `hooks-runtime` | snapshot + SKILL.md watcher + env overrides + remote cache |
| `openclaw-context-engine` | `common` + `memory` | ContextWindowGuard + pairing sanitizer + history limiter + overflow recovery chain |
| `openclaw-session-lanes` | `common` + `sessions` | LaneDispatcher（sessionLane + globalLane）+ 虚拟线程 worker + resetAllLanes |
| `openclaw-stream` | `common` + `reactor-core` | AgentEvent sealed 类型 + `ProviderChunkToAgentEvent` 翻译器 + `AgentEventSink` |
| `openclaw-agents-core` | `hooks-runtime` + `tools` + `skills` + `context-engine` + `memory` + `session-lanes` + `stream` + `approval` + `providers-registry` | PiAgentRunner 只调度 / AttemptExecutor 单事务 / SubscribeState / ActiveRunRegistry |
| `openclaw-agents-fallback` | `agents-core` + `providers-registry` | FailoverError + 分类器 + resolveFallbackCandidates + runWithModelFallback + AuthProfileRotator |
| `openclaw-agents-subagent` | `agents-core` + `session-lanes` + `sessions` + `gateway-api` | SessionsSpawnTool / SessionsSendTool / SubagentRegistry / SubagentAnnounceFlow；防递归 + allowAgents + nested lane |
| `openclaw-auto-reply` | `agents-core` + `agents-fallback` + `channels-core` | 入口 pipeline；内部走 PiAgentRunner |
| `openclaw-approval` | `common` + `hooks-runtime` + `sessions` | ExecApprovalManager（timeout=null + 15s grace + 幂等 register）+ ExecApprovalPolicy |

### 3.4 Gateway 层

| 模块 | 主要依赖 |
|---|---|
| `openclaw-gateway-api` | `common` + `json-schema-validator` |
| `openclaw-gateway-core` | `gateway-api` + `agents-core` + `agents-fallback` + `sessions` + `security` |
| `openclaw-gateway-ws` | `gateway-core` + `spring-boot-starter-webflux` |
| `openclaw-gateway-http` | `gateway-core` + `spring-boot-starter-web` + `bucket4j` |
| `openclaw-gateway-openai-compat` | `gateway-core` + `agents-core` + `providers-registry` |
| `openclaw-gateway-hooks` | `gateway-core` + `hooks-runtime` + `tools` |
| ~~`openclaw-gateway-openresponses`~~ | ⛔ 暂不建立（多模态范围收敛） |

### 3.5 通道层

| 模块 | 主要依赖 |
|---|---|
| `openclaw-channels-core` | `common` + `sessions` + `routing` + `auto-reply` |
| `openclaw-channels-web` | `channels-core` + `spring-boot-starter-web` + `spring-boot-starter-webflux`（WS） |
| ~~其他通道~~ | ⛔ 暂不建立 |

### 3.6 CLI / 运维

| 模块 | 主要依赖 |
|---|---|
| `openclaw-cli` | `commands` + `picocli` + `picocli-spring-boot-starter` |
| `openclaw-commands` | `agents-core` + `gateway-core`（调用控制面） |
| `openclaw-cron` | `common` + `elasticjob-lite-spring-boot-starter` + `curator-framework`（ZK 客户端由 ElasticJob 传递） |
| `openclaw-daemon` | `common`（仅 `ProcessBuilder` / `ProcessHandle`，不引入 pty4j） |
| `openclaw-browser` | `common` + `tools` + **`com.microsoft.playwright:playwright`** |
| ~~`openclaw-hooks`~~ | 已并入 `openclaw-hooks-runtime`（M3 重命名） |
| `openclaw-i18n` | `spring-boot-starter` |
| `openclaw-security` | `sessions` + `spring-boot-starter-security` + `jjwt` + `bucket4j` |
| ~~`openclaw-tts` / `openclaw-process`~~ | ⛔ 暂不建立（仅文本范围，不做音频 / PTY） |

### 3.7 Server Runtime 子模块

这些是 `openclaw` 教程阶段 5 中 `server-*` 的对应翻译（控制面运行时支撑）：

| 模块 | 主要依赖 | 职责 |
|---|---|---|
| `openclaw-server-runtime-config` | `config` + `session-lanes` + `agents-core` | `buildGatewayReloadPlan`（HOT_RELOAD / NEEDS_RESTART 分类）+ `GatewayIdleGuard`（空闲窗口 + 退避重试 + 60s 兜底） |
| `openclaw-server-restart-sentinel` | `common` | 重启哨兵文件（区分主动重启 vs 崩溃重启） + 启动时导出 `RestartReason` 观测标签 |
| `openclaw-server-tailscale` | `daemon`（`ProcessBuilder`） | Tailscale 侧车管理 |
| `openclaw-server-discovery` | `common` | 节点发现 |
| `openclaw-server-lanes` | `session-lanes` | Lane 运行时治理 |
| `openclaw-server-model-catalog` | `providers-registry` | 模型目录 |
| `openclaw-server-session-key` | `secrets` + `sessions` | 会话密钥 |
| `openclaw-server-startup-memory` | `memory` | 启动期记忆预热 |

### 3.8 聚合启动

| 模块 | 依赖 |
|---|---|
| `openclaw-bootstrap` | 聚合上述全部核心模块（按 profile 可选装载通道/浏览器等），附带 `application.yml`、Docker、启动脚本 |

---

## 4. 父 POM 骨架

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.openclaw</groupId>
  <artifactId>openclaw-java-parent</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <properties>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <spring-boot.version>3.3.4</spring-boot.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>${spring-boot.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>

      <!-- MyBatis-Plus -->
      <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        <version>${mybatis-plus.version}</version>
      </dependency>

      <!-- Fastjson2 + Spring Web 支持 -->
      <dependency>
        <groupId>com.alibaba.fastjson2</groupId>
        <artifactId>fastjson2</artifactId>
        <version>${fastjson2.version}</version>
      </dependency>
      <dependency>
        <groupId>com.alibaba.fastjson2</groupId>
        <artifactId>fastjson2-extension-spring6</artifactId>
        <version>${fastjson2.version}</version>
      </dependency>

      <!-- ElasticJob -->
      <dependency>
        <groupId>org.apache.shardingsphere.elasticjob</groupId>
        <artifactId>elasticjob-lite-spring-boot-starter</artifactId>
        <version>${elasticjob.version}</version>
      </dependency>

      <!-- API 文档 -->
      <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>${springdoc.version}</version>
      </dependency>
      <dependency>
        <groupId>com.github.xiaoymin</groupId>
        <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
        <version>${knife4j.version}</version>
      </dependency>

      <!-- 浏览器自动化 -->
      <dependency>
        <groupId>com.microsoft.playwright</groupId>
        <artifactId>playwright</artifactId>
        <version>${playwright.version}</version>
      </dependency>

      <!-- openclaw 内部 BOM -->
      <dependency>
        <groupId>com.openclaw</groupId>
        <artifactId>openclaw-bom</artifactId>
        <version>${project.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <modules>
    <module>openclaw-common</module>
    <module>openclaw-logging</module>
    <!-- 其余按 03-roadmap.md 顺序逐阶段加入 -->
    <module>openclaw-bootstrap</module>
  </modules>
</project>
```

---

## 5. 渐进式装配

不一次性把 60 个模块都建出来，**按 [`03-roadmap.md`](./03-roadmap.md) 的阶段分批创建**，每阶段结束后：

1. `mvn clean install -DskipTests=false` 必须通过
2. `openclaw-bootstrap` 能启动（即使某些通道/Provider 是 mock）
3. 新增模块必须被父 `<modules>` 列表收录
