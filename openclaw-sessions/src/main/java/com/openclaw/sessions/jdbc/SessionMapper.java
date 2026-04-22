package com.openclaw.sessions.jdbc;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for {@link SessionEntity}. Registered via
 * {@code @MapperScan} on {@link com.openclaw.sessions.OpenClawSessionsJdbcAutoConfiguration}.
 */
@Mapper
public interface SessionMapper extends BaseMapper<SessionEntity> {
}
