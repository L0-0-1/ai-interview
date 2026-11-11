package com.study.interview.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 数据库实体：interview_session 表的一行 = 一场模拟面试。
 *
 * 为什么必须是普通 class 而不是 record（与 Resume 同款原因）：
 * MyBatis 灌值流程是「反射调无参构造 new 空对象 → 逐列调 setter」，
 * record 没有无参构造、字段 final 没有 setter，当实体直接失败。
 *
 * 与 InterviewMessage 的一对多关系：本表是「一」方，消息表是「多」方，
 * 通过消息表的 session_id 列关联（逻辑外键，见 interview_schema.sql 注释）。
 */
@TableName("interview_session")
public class InterviewSession {

    /** 会话状态：进行中。创建时写死 ONGOING；提交最后一轮回答时改成 ENDED（见 InterviewSessionService） */
    public static final String STATUS_ONGOING = "ONGOING";
    public static final String STATUS_ENDED = "ENDED";

    /** 一场面试的默认最大轮数：本版写死 8，后续可改成配置项 */
    public static final int DEFAULT_MAX_ROUND = 8;

    /**
     * AUTO = 主键交给数据库 AUTO_INCREMENT（与 Resume 同款选型）。
     * 不写 type 则 Plus 默认 ASSIGN_ID 雪花算法，和建表自增语义冲突。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 简历原文快照 → 列 resume_text。
     * 为什么把整份简历存进会话：后续每一轮出题/追问都依据简历内容，
     * 只要求请求方开始时传一次，之后凭 sessionId 就能推进整场面试。
     */
    private String resumeText;

    /** 目标岗位 → 列 target_position：出题围绕岗位要求展开 */
    private String targetPosition;

    /** 当前轮次 → 列 current_round：创建时为 1；每完成一轮回答 +1（提交最后一轮时不推进） */
    private Integer currentRound;

    /** 最大轮数 → 列 max_round：创建时为 8；当前轮等于它时提交回答直接结束会话 */
    private Integer maxRound;

    /** 状态 → 列 status：ONGOING 进行中 / ENDED 已结束 */
    private String status;

    /**
     * 创建时间 → 列 create_time。
     * 不手动赋值：Plus 选择性插入（null 字段不进 INSERT 列清单），
     * 该列 DEFAULT CURRENT_TIMESTAMP 自动生效，时间以数据库时钟为准。
     */
    private LocalDateTime createTime;

    // ---------- getter / setter：MyBatis 反射灌值的通道，一个都不能省 ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getResumeText() {
        return resumeText;
    }

    public void setResumeText(String resumeText) {
        this.resumeText = resumeText;
    }

    public String getTargetPosition() {
        return targetPosition;
    }

    public void setTargetPosition(String targetPosition) {
        this.targetPosition = targetPosition;
    }

    public Integer getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(Integer currentRound) {
        this.currentRound = currentRound;
    }

    public Integer getMaxRound() {
        return maxRound;
    }

    public void setMaxRound(Integer maxRound) {
        this.maxRound = maxRound;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "InterviewSession{id=" + id
                + ", resumeText='" + resumeText + '\''
                + ", targetPosition='" + targetPosition + '\''
                + ", currentRound=" + currentRound
                + ", maxRound=" + maxRound
                + ", status='" + status + '\''
                + ", createTime=" + createTime + '}';
    }
}
