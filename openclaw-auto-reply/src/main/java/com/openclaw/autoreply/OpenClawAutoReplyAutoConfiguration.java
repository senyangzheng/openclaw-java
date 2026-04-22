package com.openclaw.autoreply;

import com.openclaw.autoreply.command.ChatCommand;
import com.openclaw.autoreply.command.ChatCommandDispatcher;
import com.openclaw.providers.api.ProviderClient;
import com.openclaw.providers.api.mock.EchoMockProviderClient;
import com.openclaw.sessions.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers an {@link AutoReplyPipeline} and — as an M1 fallback — an
 * {@link EchoMockProviderClient} when no other {@link ProviderClient} bean is present.
 * Real provider autoconfigs (Qwen, Gemini, ...) declare
 * {@code @AutoConfiguration(before = OpenClawAutoReplyAutoConfiguration.class)} so they
 * get evaluated first and register their own {@link ProviderClient}, suppressing the mock.
 *
 * <p>M2.4: additionally collects every {@link ChatCommand} bean (e.g. those
 * registered by plugins in {@code onLoad}) into a {@link ChatCommandDispatcher}
 * that fronts the pipeline. Commands run before the LLM; the first match
 * produces the reply.
 */
@AutoConfiguration
public class OpenClawAutoReplyAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenClawAutoReplyAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ProviderClient.class)
    public ProviderClient echoMockProviderClient() {
        log.warn("provider.fallback.mock enabled=true — no real ProviderClient bean registered. "
            + "Set openclaw.providers.qwen.enabled=true + DASHSCOPE_API_KEY to use Qwen.");
        return new EchoMockProviderClient();
    }

    /**
     * Built with a <em>lazy</em> supplier: the command list is re-read on every
     * dispatch, so plugins that register {@link ChatCommand} beans inside their
     * {@code onLoad} hook (which fires on {@code ContextRefreshedEvent} — AFTER
     * this bean is instantiated) are still picked up. See
     * {@link ChatCommandDispatcher} for the rationale.
     */
    @Bean
    @ConditionalOnMissingBean(ChatCommandDispatcher.class)
    public ChatCommandDispatcher chatCommandDispatcher(final ObjectProvider<ChatCommand> commands) {
        return new ChatCommandDispatcher(() -> commands.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public AutoReplyPipeline autoReplyPipeline(final SessionRepository sessions,
                                               final ProviderClient provider,
                                               final ChatCommandDispatcher commandDispatcher) {
        return new AutoReplyPipeline(sessions, provider, commandDispatcher);
    }
}
