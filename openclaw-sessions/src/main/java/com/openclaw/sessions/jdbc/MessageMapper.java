package com.openclaw.sessions.jdbc;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus mapper for {@link MessageEntity} with a couple of narrow queries
 * the generic {@link BaseMapper} does not cover cleanly.
 */
@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {

    /**
     * @return all messages for a session, ordered by seq ASC.
     */
    @Select("SELECT id, session_id, seq, role, content, created_at "
        + "FROM oc_message WHERE session_id = #{sessionId} ORDER BY seq ASC")
    List<MessageEntity> selectBySessionOrderBySeq(@Param("sessionId") Long sessionId);

    /**
     * @return current message count for a session, used to determine the next seq.
     */
    @Select("SELECT COUNT(*) FROM oc_message WHERE session_id = #{sessionId}")
    int countBySession(@Param("sessionId") Long sessionId);
}
