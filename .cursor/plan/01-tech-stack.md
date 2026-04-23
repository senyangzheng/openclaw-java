# 01 · Java 技术栈选型

面向 `openclaw-java` 的核心技术决策与理由，所有选型以「**业务语义等价 + Java 惯用法 + 生态稳定**」为第一原则。

---

## 1. 语言与运行时

| 项 | 选型 | 理由 |
|---|---|---|
| JDK | **JDK 21 (LTS)** | 虚拟线程 (`Thread.ofVirtual()`) 替代 Node 事件循环；record、pattern matching、sealed classes 降低翻译成本 |
| 语言特性 | record / sealed / switch pattern / text blocks / var | 对应 TS 的 `type`/union/tagged union |
| 构建工具 | **Maven 3.9+** | 用户规则要求；多模块聚合成熟 |
| 主框架 | **Spring Boot 3.3.x** | 用户规则要求；生态齐全 |

---

## 2. Web / 异步 / 流式

| 场景 | 选型 | 备注 |
|---|---|---|
| 同步 HTTP 控制面（`gateway-http` 大部分方法） | **Spring MVC (Servlet) + 虚拟线程** | `spring.threads.virtual.enabled=true`，写法简单，天然支持虚拟线程 |
| 流式 / SSE / 长连接响应 | **Spring WebFlux / Reactor** | 用于 OpenAI 兼容 `chat/completions` 流式、Agent 事件订阅 |
| WebSocket 控制面 | **Spring WebFlux WebSocket**（或 `@ServerEndpoint`） | Gateway WS 握手 + `connect` + 鉴权 + 方法分发 |
| HTTP 客户端 | **Spring `RestClient` + `WebClient`** | `RestClient` 同步调用 Provider API，流式响应用 `WebClient` SSE |
| JSON | **Fastjson2**（`com.alibaba.fastjson2:fastjson2`） | 替代 Jackson 作为首选 JSON 库；Spring Boot 层需注册 Fastjson2 的 `HttpMessageConverter`（`com.alibaba.fastjson2:fastjson2-extension-spring6`） |
| JSON Schema 校验 | **`com.networknt:json-schema-validator`** | 对应 TS 端 `zod`/`ajv`，用于协议帧 Schema 验证；底层仍可复用 Jackson `JsonNode`，**只在校验器内部使用**，不对外暴露 |

> **策略**：
> 1. 默认 MVC + 虚拟线程；**只有涉及流/SSE/WebSocket 的模块**才引入 Reactor，避免整库反应式化。
> 2. Fastjson2 作为**全局默认序列化器**。Jackson 只作为 `json-schema-validator` 的传递依赖存在，**业务代码禁止直接使用**（见 `05-translation-conventions.md`）。
> 3. Web 层统一 `FastJsonHttpMessageConverter`（`JSONWriter.Feature.WriteNulls`、`PrettyFormat` 按 profile 切换）；`WebClient` 流式 SSE 解析走自定义 `BodyExtractor`，内部用 `JSON.parseObject`。

---

## 3. 数据与缓存

| 用途 | 选型 |
|---|---|
| 关系型数据库（**统一使用**） | **MySQL 8.x**（开发/测试/生产一致，**不再使用 H2 / SQLite / PostgreSQL**） |
| 持久化框架 | **MyBatis-Plus 3.5.x**（`com.baomidou:mybatis-plus-spring-boot3-starter`） + 轻量 **Spring Data** 抽象（仅用 `Page`/`Pageable` 等通用分页与跨仓库组合场景） |
| 连接池 | **HikariCP**（Spring Boot 3 默认，显式锁定并在 `application.yml` 调优） |
| JDBC 驱动 | **`com.mysql:mysql-connector-j`** |
| SQL 方言 / 代码生成 | **MyBatis-Plus Generator**（可选，开发期生成 Mapper/Entity 模板） |
| 数据库迁移 | **Flyway + MySQL** |
| 本地缓存 | **Caffeine + Spring Cache** |
| 分布式缓存（可选） | **Redis + Spring Data Redis** |
| 文本/文件存储（仅文本用于会话持久化） | 直接入 MySQL（`TEXT` / `LONGTEXT`）；不做媒体存储 |

**约定**：
- 所有实体类用 `record` 难以与 MyBatis-Plus 配合（需要无参构造 + setter 才能被其填充），因此 **DO（`@TableName` 修饰的实体类）使用普通 `class` + Lombok**；对外 DTO 仍优先 `record`。
- 复杂查询优先走 **Mapper XML**；简单 CRUD / 条件构造器走 **`LambdaQueryWrapper`**。
- **不允许** Service 直接 `@Autowired` `JdbcTemplate` / `EntityManager`；必须通过 Mapper 接口（`BaseMapper<T>`）或自定义 Service (`IService<T>`) 访问。
- 每个需要持久化的业务模块自带 `src/main/resources/db/migration/` Flyway 脚本，命名 `V{yyyymmdd}__{desc}.sql`。

---

## 4. AI / Provider 集成

**最终方案（已锁定）**：**自研 Provider SPI 主导 + 借鉴 Spring AI 的 DTO 模型**。

- 在 `openclaw-providers-api` 定义 SPI：`ProviderClient`、`ChatRequest`、`ChatChunkEvent`、`ToolCallChunk`、`AuthProfile`、`CooldownPolicy`。
- 每家 Provider 独立 Maven 模块（`openclaw-providers-<name>`），按需装载。
- **不直接依赖** `spring-ai-*`，避免被其抽象限制；但可以参考其 `Message` / `Prompt` / `ChatResponse` 的字段组织。
- Provider 原始 HTTP 调用统一走 `WebClient`（流式）+ `RestClient`（非流式），JSON 序列化走 **Fastjson2**。
- 配套：auth-profile 池、cooldown、payload log、token 刷新、限流全部做到 `openclaw-providers-registry`，而非散落到各家 Provider。

关键 Provider（首期必做）：**Google Gemini、Qwen**。其他（OpenAI、Anthropic、Copilot 等）按 `03-roadmap.md` 的优先级延后。

---

## 5. CLI

| 项 | 选型 |
|---|---|
| 命令框架 | **Picocli 4.7+** |
| 与 Spring Boot 集成 | `picocli-spring-boot-starter` + `CommandLineRunner` |
| 原因 | 原 `openclaw` CLI 支持子命令 + 参数 + 帮助；Picocli 是 Java 生态事实标准 |
| **命令名** | **`openclaw-java`**（**不要**用 `openclaw`，避免与本机已安装的 TS 版原生 `openclaw` CLI 冲突） |

> 命名约束（强制）：
>
> - Picocli 顶层命令：`@Command(name = "openclaw-java", ...)`
> - 启动脚本 / 可执行封装：`bin/openclaw-java`、Docker `ENTRYPOINT ["openclaw-java"]`
> - `--help` / 错误提示里出现的程序名统一为 `openclaw-java`
> - Maven 模块名保持 `openclaw-cli`（与上游概念对齐，不影响 shell 命令名）
> - 本仓库根 artifact 已命名为 `openclaw-java-parent`，与命令名一致

---

## 6. 通道适配器生态

**当前范围**：**仅实现 Web 通道**，其余通道（Telegram/Discord/Slack/WhatsApp/Line/Signal/iMessage）**暂不翻译**，仅保留 SPI 扩展点，待主干稳定后按需补。

| 通道 | 选型 | 状态 |
|---|---|---|
| Web | 自建 HTTP (Spring MVC) + WebSocket (Spring WebFlux) 端点 | ✅ 首期必做 |
| Telegram / Discord / Slack / Line / WhatsApp / Signal / iMessage | — | ⛔ 暂不做 |

- 所有通道仍以 **`ChannelAdapter` SPI** 的形式实现，放在 `openclaw-channels-core`；Web 通道的实际实现放 `openclaw-channels-web`。
- 父 POM 的 `<modules>` 中**只收录 `openclaw-channels-core` 与 `openclaw-channels-web`**，其余通道模块不建立空壳，避免维护成本。
- 未来新增通道时，只需新建 `openclaw-channels-<name>` 模块并实现 `ChannelAdapter`，无需改动主干。

---

## 7. 浏览器 / 媒体 / TTS / 终端

**当前范围：支持文本 + 浏览器自动化**。图像、音频、视频、PTY、TTS **暂不翻译**（浏览器已恢复）。

| 功能 | 状态 | 说明 |
|---|---|---|
| 浏览器自动化（Playwright） | ✅ 保留 | **`com.microsoft.playwright:playwright`**，对应模块 `openclaw-browser`；用于工具执行、网页抓取、登录态维护等文本侧强需求 |
| 图像 / OCR | ⛔ 暂不做 | 对应模块 `openclaw-media`、`openclaw-media-understanding` 不建立 |
| 音视频 / STT | ⛔ 暂不做 | 同上 |
| TTS | ⛔ 暂不做 | 对应模块 `openclaw-tts` 不建立 |
| 终端 / PTY | ⛔ 暂不做 | 对应模块 `openclaw-process` 不建立（`openclaw-daemon` 需要时改用纯 `ProcessBuilder` + `ProcessHandle`） |
| Markdown（纯文本场景） | ✅ 保留 | `org.commonmark:commonmark`，放入 `openclaw-common` 子包 |
| 链接理解（纯文本） | 🔹 视需要 | 先不做，后续若出现文本对话中粘链接需解析再加 `openclaw-link-understanding`；如仅需抓页面文本可直接走 `openclaw-browser` |

**对架构的影响**：
- `openclaw-browser` **恢复建立**，放入 M5 阶段（与 `openclaw-cron`、`openclaw-daemon` 同期），首期实现：启动 / 关闭 Playwright 浏览器上下文、页面导航、选择器抓取文本、截图（文件落盘但不走媒体管线）、作为 `Tool` 暴露给 Agent。
- Playwright 浏览器二进制默认不打进 jar，**由 `openclaw-bootstrap` 启动时按需拉取**（支持 `PLAYWRIGHT_BROWSERS_PATH` 环境变量），Docker 镜像使用官方 `mcr.microsoft.com/playwright/java` 作为 base 以避免冷启动下载。
- `openclaw-agents` 的多模态分支（图/音输入、图片工具结果）**暂时以 `UnsupportedOperationException` 占位**，保留接口但不实现。
- `openclaw-gateway-openresponses`（多模态端点）**推迟到阶段 6 以后**；阶段 4 只实现 OpenAI 兼容 `/v1/chat/completions`（纯文本 + 流式）。
- Agent 的 `ToolCall` 输入/输出类型收敛到 `text/json`，不处理 `image_url` / `audio` 等（浏览器截图仅作为工具副产物存盘，不回流给 LLM 作为输入）。

---

## 8. 定时 / 调度 / 守护

| 用途 | 选型 |
|---|---|
| 分布式 / 可视化调度 | **Apache ShardingSphere ElasticJob**（`org.apache.shardingsphere.elasticjob:elasticjob-lite-spring-boot-starter`） |
| 注册中心 | **ZooKeeper 3.8+**（ElasticJob-Lite 依赖） |
| 作业类型 | `SimpleJob` / `DataflowJob` / `ScriptJob`；业务优先 `SimpleJob` |
| 极轻量一次性定时（不需要分布式） | `Spring @Scheduled`（限内部 worker 使用，不做业务主循环） |
| 异步 | **`@Async` + 虚拟线程 executor**（Spring Boot 3 `spring.task.execution.thread-name-prefix` + `AsyncConfigurer` 注册 `VirtualThreadTaskExecutor`） |
| 守护进程 | 自研 `DaemonManager`（进程心跳 + 重启策略），底层用 `ProcessBuilder` + `ProcessHandle`（**不引入 pty4j**，见 §7） |

**ElasticJob 约定**：
- 所有作业实现放在 `openclaw-cron` 模块；用 `@Component` + `ElasticJobBootstrap` 注入。
- 作业配置（cron、分片、覆盖策略）走 `@ConfigurationProperties("openclaw.cron.jobs.<name>")`，不写死。
- 作业追踪存储使用 MySQL（与主库同集群，独立 schema `openclaw_elasticjob_tracing`）。

---

## 9. 安全 / 鉴权 / 审计

| 项 | 选型 |
|---|---|
| 鉴权 | **Spring Security 6.x** + JWT（`io.jsonwebtoken:jjwt`） |
| 密码散列 | **BCrypt / Argon2**（`PasswordEncoder`） |
| 密钥存储 | 本地加密文件（AES-GCM） + 环境变量 + 可扩展 Vault |
| 审计日志 | `openclaw-security` 独立模块：AOP 切面 + 结构化日志（JSON） |
| 速率限制 | **Bucket4j** |

---

## 10. 可观测

| 项 | 选型 |
|---|---|
| 日志 | **SLF4J + Logback**（JSON 输出用 `logstash-logback-encoder`） |
| 指标 | **Micrometer + Prometheus** |
| 链路追踪 | **Micrometer Tracing + OpenTelemetry**（可选） |
| Actuator | **`spring-boot-starter-actuator`**（健康检查、metrics 端点） |

---

## 11. API 文档 / 测试

| 项 | 选型 |
|---|---|
| OpenAPI 规范 | **`org.springdoc:springdoc-openapi-starter-webmvc-ui`**（Spring MVC 端点自动扫描） |
| 增强 UI / 聚合 / 导出 | **`com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter`**（基于 springdoc，提供更友好的中文文档与离线导出） |
| WebFlux 流式端点 | 如需文档化，另加 `springdoc-openapi-starter-webflux-ui`；Knife4j 不强制 |
| 单元测试 | **JUnit 5 + Mockito + AssertJ** |
| Web 层测试 | **MockMvc**（MVC）/ **WebTestClient**（WebFlux） |
| Mapper 层测试 | **MyBatis-Plus + MyBatis 自带 `@SpringBootTest` 方式 + Testcontainers-MySQL**（不再使用 H2 替身） |
| 集成测试 | **`@SpringBootTest` + Testcontainers**（MySQL / ZooKeeper / Redis） |
| 断言辅助 | **AssertJ / JsonPath**（JsonPath 依赖 Jackson，限测试 scope） |
| 外部 HTTP 模拟 | **WireMock**（Provider / Web 通道都用它） |
| 覆盖率 | **JaCoCo** |

**说明**：
- `springdoc` 与 `knife4j` 同时引入时，版本要选兼容的（见 §13 BOM）；访问路径：`/v3/api-docs`（规范）、`/swagger-ui.html`（springdoc UI）、`/doc.html`（Knife4j UI）。
- Knife4j 的接口权限 / 离线导出在 `application-prod.yml` 中按需关闭（`knife4j.production=true`）。

---

## 12. 打包 / 部署

| 项 | 选型 |
|---|---|
| 可执行 jar | `spring-boot-maven-plugin` |
| 原生镜像（可选） | **GraalVM Native Image + Spring AOT** |
| 容器 | **Buildpacks** (`mvn spring-boot:build-image`) 或 Dockerfile 多阶段构建 |
| 版本治理 | 语义化版本 + Maven Release Plugin |

---

## 13. 依赖速查（父 POM BOM 片段）

```xml
<properties>
  <java.version>21</java.version>
  <spring-boot.version>3.3.4</spring-boot.version>

  <!-- 持久化 -->
  <mybatis-plus.version>3.5.9</mybatis-plus.version>
  <mysql-connector.version>9.0.0</mysql-connector.version>
  <flyway.version>10.20.0</flyway.version>

  <!-- JSON -->
  <fastjson2.version>2.0.53</fastjson2.version>

  <!-- 调度 -->
  <elasticjob.version>3.0.4</elasticjob.version>

  <!-- API 文档 -->
  <springdoc.version>2.6.0</springdoc.version>
  <knife4j.version>4.5.0</knife4j.version>

  <!-- 浏览器自动化 -->
  <playwright.version>1.48.0</playwright.version>

  <!-- CLI / 校验 / 缓存 / 限流 / 安全 / 日志 -->
  <picocli.version>4.7.6</picocli.version>
  <json-schema-validator.version>1.5.2</json-schema-validator.version>
  <caffeine.version>3.1.8</caffeine.version>
  <bucket4j.version>8.10.1</bucket4j.version>
  <jjwt.version>0.12.6</jjwt.version>
  <commonmark.version>0.22.0</commonmark.version>
  <logstash-logback.version>8.0</logstash-logback.version>
</properties>
```

**已移除**（因范围收敛）：`pty4j`、`javacv` 等多媒体 / 终端相关依赖不再进入 BOM。`playwright` **保留**。

> 实际版本以执行 `mvn` 时能解析到的最新稳定版为准；父 POM 锁定后不得在子模块再写版本号。
