package com.openclaw.secrets.vault.jdbc;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for {@link AuthProfileEntity}. Registered via
 * {@code @MapperScan} on
 * {@link com.openclaw.secrets.OpenClawSecretsJdbcAutoConfiguration}.
 */
@Mapper
public interface AuthProfileMapper extends BaseMapper<AuthProfileEntity> {
}
