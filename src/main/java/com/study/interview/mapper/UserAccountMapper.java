package com.study.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.interview.model.UserAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserAccountMapper extends BaseMapper<UserAccount> {

    @Select("SELECT id FROM user_account WHERE id = #{userId} FOR UPDATE")
    Long lockById(@Param("userId") Long userId);
}
