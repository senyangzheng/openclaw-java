package com.openclaw.secrets.vault.jdbc;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.openclaw.providers.api.AuthProfile;
import com.openclaw.secrets.crypto.EnvelopeCipher;
import com.openclaw.secrets.vault.AuthProfileVault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis-Plus + envelope-encryption backed {@link AuthProfileVault}.
 *
 * <p>Every {@link AuthProfile#apiKey()} passed into {@link #save(AuthProfile)} is
 * sealed with a per-row DEK via {@link EnvelopeCipher} before touching MySQL; the
 * DEK itself is encrypted by the long-lived KEK and stored alongside. Only
 * ciphertexts + IVs land on disk.
 *
 * <p>{@link AuthProfile#extras()} ships as plain UTF-8 JSON: it is conventionally
 * metadata (region, quota tier, endpoint override) — NOT secret. If you have
 * sensitive extras, embed them in {@code apiKey} instead (e.g. {@code apiKey}
 * holds a JSON blob) so the envelope protects them.
 *
 * <p>Reads decrypt on demand. Consumers should cache the resolved
 * {@link AuthProfile} for the duration of a provider call rather than hitting
 * the vault per SSE chunk.
 */
public class JdbcAuthProfileVault implements AuthProfileVault {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuthProfileVault.class);

    private final AuthProfileMapper mapper;
    private final EnvelopeCipher cipher;

    public JdbcAuthProfileVault(final AuthProfileMapper mapper, final EnvelopeCipher cipher) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
    }

    @Override
    public String source() {
        return "jdbc";
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthProfile> find(final String providerId, final String profileId) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(profileId, "profileId");
        final AuthProfileEntity entity = mapper.selectOne(new LambdaQueryWrapper<AuthProfileEntity>()
            .eq(AuthProfileEntity::getProviderId, providerId)
            .eq(AuthProfileEntity::getProfileId, profileId));
        return Optional.ofNullable(entity).map(this::hydrate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuthProfile> listByProvider(final String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        final List<AuthProfileEntity> rows = mapper.selectList(new LambdaQueryWrapper<AuthProfileEntity>()
            .eq(AuthProfileEntity::getProviderId, providerId)
            .orderByAsc(AuthProfileEntity::getId));
        return rows.stream().map(this::hydrate).toList();
    }

    @Override
    public boolean supportsWrites() {
        return true;
    }

    @Override
    @Transactional
    public void save(final AuthProfile profile) {
        Objects.requireNonNull(profile, "profile");
        final EnvelopeCipher.Envelope sealed = cipher.encryptString(profile.apiKey());
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final AuthProfileEntity existing = mapper.selectOne(new LambdaQueryWrapper<AuthProfileEntity>()
            .eq(AuthProfileEntity::getProviderId, profile.providerId())
            .eq(AuthProfileEntity::getProfileId, profile.profileId()));

        final AuthProfileEntity entity = existing != null ? existing : new AuthProfileEntity();
        entity.setProviderId(profile.providerId());
        entity.setProfileId(profile.profileId());
        entity.setDataCiphertext(sealed.dataCiphertext());
        entity.setDataIv(sealed.dataIv());
        entity.setDekCiphertext(sealed.dekCiphertext());
        entity.setDekIv(sealed.dekIv());
        entity.setExtrasJson(profile.extras().isEmpty() ? null : JSON.toJSONString(profile.extras()));
        entity.setUpdatedAt(now);
        if (existing == null) {
            entity.setCreatedAt(now);
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        log.debug("vault.save providerId={} profileId={} new={}",
            profile.providerId(), profile.profileId(), existing == null);
    }

    @Override
    @Transactional
    public void delete(final String providerId, final String profileId) {
        final AuthProfileEntity entity = mapper.selectOne(new LambdaQueryWrapper<AuthProfileEntity>()
            .eq(AuthProfileEntity::getProviderId, providerId)
            .eq(AuthProfileEntity::getProfileId, profileId));
        if (entity != null) {
            mapper.deleteById(entity.getId());
        }
    }

    private AuthProfile hydrate(final AuthProfileEntity entity) {
        final String apiKey = cipher.decryptString(new EnvelopeCipher.Envelope(
            entity.getDataCiphertext(), entity.getDataIv(),
            entity.getDekCiphertext(), entity.getDekIv()));
        final Map<String, String> extras = entity.getExtrasJson() == null || entity.getExtrasJson().isBlank()
            ? Map.of()
            : JSON.parseObject(entity.getExtrasJson(), new TypeReference<Map<String, String>>() { });
        return new AuthProfile(entity.getProfileId(), entity.getProviderId(), apiKey, extras);
    }
}
