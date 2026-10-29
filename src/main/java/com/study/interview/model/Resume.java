package com.study.interview.model;

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
 *   - 两者的桥：reviewJson 字段——落库时把 ResumeReview 序列化成 JSON 文本、原样封存进这一列
 * 本类必须是普通 class：MyBatis 灌值的流程是「反射调无参构造 new 空对象 → 逐列调 setter」，
 * record 没有无参构造、字段 final 没有 setter，当实体直接失败。
 */
@TableName("Resume")
// 显式表名的原因（源码 TableInfoHelper.initTableName）：
// 不标注解时默认规则 = 类名驼峰转下划线+首字母小写 → "resume"；
// Windows 表名大小写不敏感碰巧能用，Linux 生产敏感直接 Table doesn't exist——显式写死保命。
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
     * 简历原文 → 列 resume_text。
     * 这一对驼峰↔下划线的自动对应靠两条桥（Plus 都默认开了，无需 @TableField）：
     *   写路径：TableInfoHelper 建表档案时 camelToUnderline(属性名)
     *   读路径：MybatisConfiguration 构造里 mapUnderscoreToCamelCase = true（原生默认是 false）
     */
    private String resumeText;

    /** AI 一句话总评 → 列 summary */
    private String summary;

    /**
     * 综合评分 → 列 score。用 Integer 不用 int：
     * 库里 NULL 表示"还没评分"，基本类型 int 接不住 NULL（只能变 0），"没评分"和"0 分"就混了。
     */
    private Integer score;

    /**
     * 技术能力评分 → 列 tech_score。
     * 三个维度分落库只拆分数、不拆理由（理由留在 review_json 快照）的判据：
     *   分数将来要进 WHERE / ORDER BY（按技术分排序、筛低于 60 分的），必须是独立列；
     *   理由是给人看的展示文本，不参与查询，拆列就是列膨胀（以后每加一个维度都要 DDL）。
     * 同样用 Integer：NULL = 模型漏输或未评审，把「没评分」和「0 分」区分开。
     */
    private Integer techScore;

    /** 项目经验评分 → 列 project_score（为什么拆列、为什么用 Integer，同 techScore） */
    private Integer projectScore;

    /** 表达与逻辑评分 → 列 expression_score（为什么拆列、为什么用 Integer，同 techScore） */
    private Integer expressionScore;

    /**
     * 评审详情快照 → 列 review_json。
     * 驼峰 reviewJson ↔ 下划线 review_json 走的还是那两条自动桥（和 resumeText ↔ resume_text 同款），
     * 所以同样不需要 @TableField 注解。
     *
     * 为什么类型是 String，不是 List<String>：
     *   MyBatis 把「一行」变成「一个对象」的路子是「反射 new 空对象 → 逐列拿标量值调 setter」——
     *   一列对一个值。review_json 列里装的是「一段 JSON 文本」，Java 侧就原样用 String 接。
     *   等以后的进阶课：Plus 的 JacksonTypeHandler 能在读/写两侧自动做 JSON ↔ List 转换，
     *   那时才能把 List 直接挂到字段上。现在先「手动序列化」，亲眼看过这段文本长什么样，
     *   将来才知道自动转换替你干了什么、什么场景不该用它。
     */
    private String reviewJson;

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

    public Integer getTechScore() {
        return techScore;
    }

    public void setTechScore(Integer techScore) {
        this.techScore = techScore;
    }

    public Integer getProjectScore() {
        return projectScore;
    }

    public void setProjectScore(Integer projectScore) {
        this.projectScore = projectScore;
    }

    public Integer getExpressionScore() {
        return expressionScore;
    }

    public void setExpressionScore(Integer expressionScore) {
        this.expressionScore = expressionScore;
    }

    public String getReviewJson() {
        return reviewJson;
    }

    public void setReviewJson(String reviewJson) {
        this.reviewJson = reviewJson;
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
                + ", techScore=" + techScore
                + ", projectScore=" + projectScore
                + ", expressionScore=" + expressionScore
                + ", reviewJson='" + reviewJson + '\''
                + ", createTime=" + createTime + '}';
    }
}
