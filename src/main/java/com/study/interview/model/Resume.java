package com.study.interview.model;
//用来作为数据库的映射类，对应数据库中的resume表

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 数据库实体：ai_interview.Resume 表的一行 = 一个 Resume 对象。
 *
 * 和 ResumeReview 的分工（别混）：
 *   - ResumeReview（record）：AI 结构化输出的形状，走 Jackson 反序列化
 *   - Resume（本类，class）：数据库行的形状，走 MyBatis 反射填充
 * 本类必须是普通 class：MyBatis 灌值的流程是「反射调无参构造 new 空对象 → 逐列调 setter」，
 * record 没有无参构造、字段 final 没有 setter，当实体直接失败。
 */
@TableName("Resume")
// 显式表名
public class Resume {

    /**
     * AUTO = 主键交给数据库 AUTO_INCREMENT。
     * 不写 type 则 Plus 默认 ASSIGN_ID（雪花算法）：插入时塞 19 位长整数、跳过自增序列，
     * 和建表语义冲突——所有 AUTO_INCREMENT 表都要显式标 AUTO。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 候选人姓名 → 列 name */
    private String name;

    /**
     * plus自动进行驼峰命名转换
     */
    private String resumeText;

    /** AI 一句话总评 → 列 summary */
    private String summary;

    private Integer score;

    /**
     * 创建时间 → 列 create_time。
     * 不手动赋值：Plus 的插入是"选择性插入"（null 字段不进 INSERT 列清单，源码 INSERT_ONE 模板），
     * 该列的 DEFAULT CURRENT_TIMESTAMP 自动生效，时间以数据库时钟为准。
     */
    private LocalDateTime createTime;

    // ---------- getter / setter：MyBatis 反射灌值的通道，一个都不能省 ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResumeText() {
        return resumeText;
    }

    public void setResumeText(String resumeText) {
        this.resumeText = resumeText;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    // toString：调试打印一行实体时看得清（record 免费送的，class 要自己写——这就是实体不能图省事的原因之一）
    @Override
    public String toString() {
        return "Resume{id=" + id
                + ", name='" + name + '\''
                + ", resumeText='" + resumeText + '\''
                + ", summary='" + summary + '\''
                + ", score=" + score
                + ", createTime=" + createTime + '}';
    }
}
