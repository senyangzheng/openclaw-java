package com.openclaw.sessions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Default session repository wiring: in-memory store. Disabled (via
 * {@code @ConditionalOnMissingBean}) as soon as a JDBC-backed repository is
 * registered by {@code OpenClawSessionsJdbcAutoConfiguration}, which runs first
 * when {@code openclaw.sessions.store=jdbc}.
 */
@AutoConfiguration
public class OpenClawSessionsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenClawSessionsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(SessionRepository.class)
    public SessionRepository inMemorySessionRepository() {
        log.warn("session.store=memory — history will be lost on restart. "
            + "Set openclaw.sessions.store=jdbc + datasource env vars for persistence.");
        return new InMemorySessionRepository();
    }
}
