package com.openclaw.sessions;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openclaw.sessions.jdbc.JdbcSessionRepository;
import com.openclaw.sessions.jdbc.MessageMapper;
import com.openclaw.sessions.jdbc.SessionMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * JDBC session store. Activated when:
 * <ul>
 *   <li>{@code openclaw.sessions.store=jdbc} is set, <em>and</em></li>
 *   <li>MyBatis-Plus + a {@link DataSource} are on the classpath (optional deps
 *       declared in {@code openclaw-sessions/pom.xml}).</li>
 * </ul>
 * Registered <em>before</em> {@link OpenClawSessionsAutoConfiguration} so the
 * in-memory fallback stays out of the context.
 */
@AutoConfiguration
@AutoConfigureBefore(OpenClawSessionsAutoConfiguration.class)
@ConditionalOnClass({ BaseMapper.class, DataSource.class })
@ConditionalOnProperty(prefix = "openclaw.sessions", name = "store", havingValue = "jdbc")
@EnableTransactionManagement
@MapperScan("com.openclaw.sessions.jdbc")
public class OpenClawSessionsJdbcAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenClawSessionsJdbcAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(name = "sessionKeyToIdCache")
    public Cache<String, Long> sessionKeyToIdCache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();
    }

    @Bean
    @ConditionalOnMissingBean(SessionRepository.class)
    public SessionRepository jdbcSessionRepository(final SessionMapper sessionMapper,
                                                   final MessageMapper messageMapper,
                                                   final Cache<String, Long> sessionKeyToIdCache) {
        log.info("session.store=jdbc backend=MyBatis-Plus cache=Caffeine<10000, 30m>");
        return new JdbcSessionRepository(sessionMapper, messageMapper, sessionKeyToIdCache);
    }
}
