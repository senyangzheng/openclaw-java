package com.openclaw.autoreply;

import com.openclaw.agents.core.PiAgentRunner;
import com.openclaw.sessions.SessionRepository;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers an {@link AutoReplyPipeline}.
 *
 * <h2>M3 / A1 final-state note</h2>
 * {@link AutoReplyPipeline} was reduced to a channel-edge adapter; the agent runtime logic (hook chain,
 * provider failover, lane scheduling, active-run registry) moved behind {@link PiAgentRunner}. This class now
 * only wires the pipeline's I/O boundary:
 * <ul>
 *   <li>{@link SessionRepository} — session persistence (load / append / save)</li>
 *   <li>{@link PiAgentRunner} — everything agent-side</li>
 * </ul>
 */
@AutoConfiguration
public class OpenClawAutoReplyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AutoReplyPipeline autoReplyPipeline(final SessionRepository sessions,
                                               final PiAgentRunner agentRunner) {
        return new AutoReplyPipeline(sessions, agentRunner);
    }
}
