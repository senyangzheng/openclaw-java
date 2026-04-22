package com.openclaw.secrets.vault.jdbc;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Row mapping for {@code oc_auth_profile}. Every credential is stored as an
 * envelope: {@code data_ct/data_iv} hold the encrypted apiKey, {@code dek_ct/dek_iv}
 * hold the DEK sealed by the KEK. See
 * {@link com.openclaw.secrets.crypto.EnvelopeCipher} for the scheme.
 *
 * <p>{@code extras} is stored as a UTF-8 JSON blob — parsing is intentionally
 * delegated to {@link JdbcAuthProfileVault} so entity stays a dumb DTO.
 */
@TableName("oc_auth_profile")
public class AuthProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("provider_id")
    private String providerId;

    @TableField("profile_id")
    private String profileId;

    @TableField("data_ct")
    private byte[] dataCiphertext;

    @TableField("data_iv")
    private byte[] dataIv;

    @TableField("dek_ct")
    private byte[] dekCiphertext;

    @TableField("dek_iv")
    private byte[] dekIv;

    @TableField("extras_json")
    private String extrasJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(final String providerId) {
        this.providerId = providerId;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(final String profileId) {
        this.profileId = profileId;
    }

    public byte[] getDataCiphertext() {
        return dataCiphertext;
    }

    public void setDataCiphertext(final byte[] dataCiphertext) {
        this.dataCiphertext = dataCiphertext;
    }

    public byte[] getDataIv() {
        return dataIv;
    }

    public void setDataIv(final byte[] dataIv) {
        this.dataIv = dataIv;
    }

    public byte[] getDekCiphertext() {
        return dekCiphertext;
    }

    public void setDekCiphertext(final byte[] dekCiphertext) {
        this.dekCiphertext = dekCiphertext;
    }

    public byte[] getDekIv() {
        return dekIv;
    }

    public void setDekIv(final byte[] dekIv) {
        this.dekIv = dekIv;
    }

    public String getExtrasJson() {
        return extrasJson;
    }

    public void setExtrasJson(final String extrasJson) {
        this.extrasJson = extrasJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
