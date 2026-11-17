package com.study.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.interview.model.InterviewSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {

    @Select("""
            SELECT id FROM interview_session
            WHERE user_id = #{userId} AND status = 'ONGOING'
            LIMIT 1
            """)
    Long findOngoingSessionId(@Param("userId") Long userId);
}
