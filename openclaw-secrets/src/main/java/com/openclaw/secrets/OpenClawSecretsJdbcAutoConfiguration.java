package com.openclaw.secrets;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openclaw.secrets.crypto.EnvelopeCipher;
import com.openclaw.secrets.crypto.SecretsCryptoProperties;
import com.openclaw.secrets.vault.AuthProfileVault;
import com.openclaw.secrets.vault.InMemoryAuthProfileVault;
import com.openclaw.secrets.vault.jdbc.AuthProfileMapper;
import com.openclaw.secrets.vault.jdbc.JdbcAuthProfileVault;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * JDBC auth-profile vault. Activated when:
 * <ul>
 *   <li>{@code openclaw.secrets.store=jdbc}, <em>and</em></li>
 *   <li>MyBatis-Plus + a {@link DataSource} are on the classpath
 *       (optional deps declared in {@code openclaw-secrets/pom.xml}).</li>
 * </ul>
 *
 * <p>Requires {@code openclaw.secrets.crypto.kek-base64} (32 random bytes,
 * Base64-encoded) to derive the KEK for envelope encryption.
 *
 * <p>Ordered {@code @AutoConfigureAfter(DataSourceAutoConfiguration.class)} so
 * the {@link DataSource} is ready before we wire the mapper. No other ordering
 * is needed: {@link AuthProfileVault} is a read-path dependency only, consumers
 * (e.g. provider autoconfigs) collect it lazily via {@code ObjectProvider}.
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass({ BaseMapper.class, DataSource.class })
@ConditionalOnProperty(prefix = "openclaw.secrets", name = "store", havingValue = "jdbc")
@EnableTransactionManagement
@EnableConfigurationProperties(SecretsCryptoProperties.class)
@MapperScan("com.openclaw.secrets.vault.jdbc")
public class OpenClawSecretsJdbcAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenClawSecretsJdbcAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public EnvelopeCipher envelopeCipher(final SecretsCryptoProperties properties) {
        if (properties.getKekBase64() == null || properties.getKekBase64().isBlank()) {
            throw new IllegalStateException(
                "openclaw.secrets.store=jdbc but openclaw.secrets.crypto.kek-base64 is empty. "
                    + "Generate one with `openssl rand -base64 32` and export as "
                    + "OPENCLAW_SECRETS_CRYPTO_KEK_BASE64.");
        }
        log.info("secrets.crypto.envelope.enabled algo=AES-256-GCM");
        return EnvelopeCipher.fromBase64Kek(properties.getKekBase64());
    }

    /**
     * Registered as a {@link AuthProfileVault} bean. When present, overrides any
     * {@link InMemoryAuthProfileVault} bean via
     * {@link org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean}
     * semantics on downstream consumers (registry / provider autoconfigs).
     */
    @Bean
    @ConditionalOnMissingBean(AuthProfileVault.class)
    public AuthProfileVault jdbcAuthProfileVault(final AuthProfileMapper mapper,
                                                  final EnvelopeCipher envelopeCipher) {
        log.info("secrets.vault=jdbc backend=MyBatis-Plus");
        return new JdbcAuthProfileVault(mapper, envelopeCipher);
    }
}
