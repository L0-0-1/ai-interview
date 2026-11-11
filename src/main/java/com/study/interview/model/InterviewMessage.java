package com.study.interview.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 数据库实体：interview_message 表的一行 = 面试里的一条消息。
 *
 * 与 InterviewSession 的一对多关系：本表是「多」方，
 * sessionId 列指向 interview_session.id（逻辑外键），一个会话对应多条消息。
 * 两类消息都写本表：INTERVIEWER 题目（开始面试写第 1 题、提交回答后写下一题），
 * CANDIDATE 回答（提交回答流程写入）。
 */
@TableName("interview_message")
public class InterviewMessage {

    /** 消息角色：面试官。CANDIDATE（候选人）由提交回答流程写入 */
    public static final String ROLE_INTERVIEWER = "INTERVIEWER";
    public static final String ROLE_CANDIDATE = "CANDIDATE";

    /** AUTO = 主键交给数据库 AUTO_INCREMENT（与 Resume/InterviewSession 同款选型） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话 id → 列 session_id。
     * 一对多里的「多」方靠这一列挂到会话表；索引 idx_session_id 支撑
     * 「按会话查全部消息」的查询路径（见 interview_schema.sql 注释）。
     */
    private Long sessionId;

    /** 消息角色 → 列 role：INTERVIEWER 面试官 / CANDIDATE 候选人 */
    private String role;

    /** 消息内容 → 列 content：面试官的题目，或候选人的回答 */
    private String content;

    /** 所属轮次 → 列 round_no：这条消息属于第几轮，从 1 开始 */
    private Integer roundNo;

    /**
     * 创建时间 → 列 create_time。
     * 不手动赋值：Plus 选择性插入，该列 DEFAULT CURRENT_TIMESTAMP 自动生效（与 Resume 同款）。
     */
    private LocalDateTime createTime;

    // ---------- getter / setter：MyBatis 反射灌值的通道，一个都不能省 ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "InterviewMessage{id=" + id
                + ", sessionId=" + sessionId
                + ", role='" + role + '\''
                + ", content='" + content + '\''
                + ", roundNo=" + roundNo
                + ", createTime=" + createTime + '}';
    }
}
