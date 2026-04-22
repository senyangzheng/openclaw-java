# openclaw-java

A Java / Spring Boot translation of [openclaw](https://github.com/) (TypeScript). Actively under construction; see `.cursor/plan/` for the authoritative development plan.

## Status

| Milestone | Learning chapters | Status |
|---|---|---|
| M0 Scaffolding | — | ✅ done |
| **M1** Global cognition (CLI / channels skeleton) | 00–06 | ✅ done |
| M2 Providers & extensions | 07–11 | ✅ done (M2.1–M2.4) |
| M3 Agent framework | 12–26 | ⏳ |
| M4 Gateway control plane | 27–42 | ⏳ |
| M5 Reproduction & completion | 43–59 | ⏳ |

## Requirements

- **JDK 21** (LTS, virtual threads required)
- **Maven 3.9+**
- **MySQL 8.x** (from M2 onwards)
- **ZooKeeper 3.8+** (from M5 onwards, for ElasticJob)

## Quick start

```bash
mvn clean package -DskipTests=false
java -jar openclaw-bootstrap/target/openclaw-bootstrap-*.jar
```

Then visit:

- `http://localhost:8080/actuator/health` &rarr; `{"status":"UP"}`
- `http://localhost:8080/hello` &rarr; smoke info

### Web channel (M1)

```bash
# Mock reply via the Web channel
curl -s -X POST http://localhost:8080/api/channels/web/messages \
  -H 'Content-Type: application/json' \
  -d '{"text":"hello"}'
# → {"text":"[mock] hello", ...}

# Same path, via the gateway envelope
curl -s -X POST http://localhost:8080/api/gateway \
  -H 'Content-Type: application/json' \
  -d '{"id":"r1","method":"chat.send","params":{"text":"hi gateway"}}'
```

### CLI (M1)

```bash
mvn -pl openclaw-bootstrap -am -DskipTests package
./bin/openclaw-java --help
./bin/openclaw-java chat --text "hi from cli"
./bin/openclaw-java channels
```

The shell command is deliberately `openclaw-java` (never plain `openclaw`) to avoid
clashing with the upstream TS openclaw CLI —
see [`.cursor/plan/05-translation-conventions.md`](./.cursor/plan/05-translation-conventions.md) §11.

### Encrypted credential vault (M2.3)

When `openclaw.secrets.store=jdbc` the provider api-keys are persisted to
`oc_auth_profile` using AES-256-GCM envelope encryption (per-row DEK sealed by
the long-lived KEK). Qwen / Google auto-configurations then consult the vault
first and fall back to `openclaw.providers.<id>.api-key` only when no entry
exists.

```bash
# 1) mint a 32-byte master KEK (only once per deployment)
export OPENCLAW_SECRETS_CRYPTO_KEK_BASE64="$(openssl rand -base64 32)"
# 2) switch stores + point at MySQL
export OPENCLAW_SECRETS_STORE=jdbc
export OPENCLAW_SESSIONS_STORE=jdbc
export OPENCLAW_DB_URL='jdbc:mysql://localhost:3306/openclaw?useSSL=false&characterEncoding=UTF-8&allowPublicKeyRetrieval=true'
export OPENCLAW_DB_USERNAME=openclaw
export OPENCLAW_DB_PASSWORD=openclaw
export OPENCLAW_FLYWAY_ENABLED=true
# 3) upsert an AuthProfile (any tool that @Autowires AuthProfileVault works;
#    Testcontainers-based JdbcAuthProfileVaultIT shows the canonical write path).
```

Losing the KEK renders every stored credential unrecoverable — back it up
outside the database.

### Plugin runtime (M2.4)

Plugins are JAR-addressed extensions discovered via the standard
`java.util.ServiceLoader` mechanism. Author a class that implements
`com.openclaw.plugin.OpenClawPlugin`, declare it in
`META-INF/services/com.openclaw.plugin.OpenClawPlugin`, and drop the jar onto
the classpath — the loader picks it up on the next boot and invokes `onLoad`
after the Spring context finishes refresh.

The built-in `hello` demo plugin registers two beans and a chat command:

| Bean | Type | How to verify |
|---|---|---|
| `plugin.hello.greeter` | `HelloGreeter` | actuator `/beans` endpoint |
| `plugin.hello.chatCommand` | `ChatCommand` (`/hello` prefix) | Web dialog — see below |

#### End-to-end: call the plugin via Web chat

The auto-reply pipeline fronts every inbound message with a `ChatCommandDispatcher`
that scans every registered `ChatCommand`. A match short-circuits the LLM path
entirely — no Qwen / Gemini call, no token cost, deterministic reply. Try it:

```bash
# plugin handles this (provider is NEVER called)
curl -s -XPOST http://localhost:8080/api/channels/web/messages \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"u1","conversationId":"c1","text":"/hello alice"}'
# → {"text":"hello, alice — from the openclaw hello plugin", ...}

# any non-matching text still flows to the LLM / mock
curl -s -XPOST http://localhost:8080/api/channels/web/messages \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"u1","conversationId":"c1","text":"anything else"}'
# → {"text":"[mock] anything else", ...}   (or real LLM reply if Qwen/Gemini enabled)
```

In the server log you'll see the decisive line:

```
chat-command.dispatched name=hello replyLen=42
auto-reply.outbound.command command=hello replyLen=42
```

Same short-circuit applies to the SSE stream endpoint — the command's reply is
emitted as a single `Delta` + `Done`, so clients don't need to branch:

```bash
curl -Ns -XPOST http://localhost:8080/api/channels/web/messages/stream \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"u1","conversationId":"c1","text":"/hello bob"}'
```

#### Opt-out / disable

```bash
# disable the loader entirely
export OPENCLAW_PLUGINS_ENABLED=false

# or drop just the demo plugin
#   (application.yml)
#   openclaw.plugins.exclude: [hello]
```

The loader is idempotent: only `ContextRefreshedEvent` from the root context
triggers discovery, and a single broken plugin produces a WARN log rather than
crashing startup (flip `openclaw.plugins.fail-fast=true` for the opposite).

#### Writing your own plugin

```java
public class WeatherPlugin implements OpenClawPlugin {
    public String id()          { return "weather"; }
    public String version()     { return "0.1.0"; }
    public String description() { return "/weather <city> shortcut"; }

    public void onLoad(PluginContext ctx) {
        ctx.registerSingleton("chatCommand", () -> new ChatCommand() {
            public String name() { return "weather"; }
            public boolean matches(InboundMessage m) { return m.text().startsWith("/weather "); }
            public String handle(InboundMessage m)   { return lookup(m.text().substring(9).trim()); }
        });
    }
}
```

Declare it in `META-INF/services/com.openclaw.plugin.OpenClawPlugin`, package
as a jar, drop onto the classpath, restart — `/weather beijing` now bypasses
the LLM.

### Config hot reload (M2.4)

When `openclaw.config.hot-reload.enabled=true` a single daemon thread watches
the parent directory of every file in `openclaw.config.hot-reload.paths` via
`java.nio.file.WatchService`. Bursts of modify events are collapsed into a
debounced `ConfigChangeEvent`, then fanned out to every `HotReloadable` bean
and re-published as an `ApplicationEventPublisher` event.

```yaml
openclaw:
  config:
    hot-reload:
      enabled: true
      debounce: 500ms
      paths:
        - /etc/openclaw/runtime.yml
```

Consumers implement `com.openclaw.config.hotreload.HotReloadable`:

```java
@Component
public class MyService implements HotReloadable {
    public void onConfigReloaded(ConfigChangeEvent event) {
        // re-read /etc/openclaw/runtime.yml and rebind your own state
    }
}
```

The runtime does NOT rebind `@ConfigurationProperties` beans automatically
(that would pull in spring-cloud-context). Consumers control exactly what gets
reloaded — strictly opt-in, no Spring Cloud dependency.

### Activate a profile

```bash
# Default (no env var set) activates 'dev' via spring.profiles.default
java -jar openclaw-bootstrap/target/openclaw-bootstrap-*.jar

SPRING_PROFILES_ACTIVE=prod java -jar openclaw-bootstrap/target/openclaw-bootstrap-*.jar
SPRING_PROFILES_ACTIVE=test java -jar openclaw-bootstrap/target/openclaw-bootstrap-*.jar
```

## Module layout (M0 + M1)

```
openclaw-java/
├── openclaw-common/         base utilities, error codes, Fastjson2 facade
├── openclaw-logging/        MDC keys, scoped MDC, shared logback fragment
├── openclaw-config/         @ConfigurationProperties + auto configuration
├── openclaw-secrets/        SecretResolver SPI + AuthProfileVault (in-memory + JDBC envelope-encrypted)
│
├── openclaw-providers-api/  ProviderClient SPI + EchoMockProviderClient
├── openclaw-sessions/       SessionKey + InMemorySessionRepository (M2: MyBatis-Plus)
├── openclaw-routing/        RoutingKey + ChannelAccount
├── openclaw-channels-core/  ChannelAdapter SPI + ChannelRegistry + AccountLifecycle
├── openclaw-auto-reply/     AutoReplyPipeline: Inbound → Provider → Outbound
├── openclaw-gateway-api/    GatewayRequest / GatewayResponse + Methods constants
├── openclaw-gateway-core/   MethodDispatcher + MockAuthGuard + built-in method handlers
├── openclaw-channels-web/   HTTP endpoints: /api/channels/web/messages, /api/gateway
├── openclaw-commands/       ChatCommandService (shared by CLI + gateway)
├── openclaw-cli/            Picocli: openclaw-java {chat,channels}
│
├── openclaw-plugin-sdk/     OpenClawPlugin SPI + PluginContext (pure API, no Spring Boot)
├── openclaw-plugins/        PluginLoader (ServiceLoader) + PluginRegistry + built-in "hello" demo
│
└── openclaw-bootstrap/      @SpringBootApplication + bean wiring
```

Full module roadmap: [`.cursor/plan/02-maven-modules.md`](./.cursor/plan/02-maven-modules.md).

## Documentation

All planning documents live under [`.cursor/plan/`](./.cursor/plan/):

- [`README.md`](./.cursor/plan/README.md) — overview
- [`00-architecture-mapping.md`](./.cursor/plan/00-architecture-mapping.md) — TS → Java mapping
- [`01-tech-stack.md`](./.cursor/plan/01-tech-stack.md) — tech choices
- [`02-maven-modules.md`](./.cursor/plan/02-maven-modules.md) — module topology
- [`03-roadmap.md`](./.cursor/plan/03-roadmap.md) — six-phase roadmap
- [`04-milestones.md`](./.cursor/plan/04-milestones.md) — acceptance checklist
- [`05-translation-conventions.md`](./.cursor/plan/05-translation-conventions.md) — translation rules

Contribution rules are in [`CODESTYLE.md`](./CODESTYLE.md).
