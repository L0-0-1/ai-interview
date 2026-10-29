-- =====================================================================
-- ai-interview 建表脚本
-- 数据库：MySQL 8
--
-- 用法：
--   mysql -u root -p < sql/schema.sql
-- 或在客户端里整段执行。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `ai_interview`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `ai_interview`;

-- ---------------------------------------------------------------------
-- 简历评审记录表
--
-- 表名与实体类 @TableName("Resume") 保持一致（首字母大写）。
-- 注意：MySQL 在 Linux 下默认 lower_case_table_names=0，表名区分大小写；
--      若部署到 Linux 后报 "table doesn't exist"，请检查这一点。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `Resume`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(64)  DEFAULT NULL COMMENT '候选人姓名，模型未识别时存「未知」',
    `resume_text` LONGTEXT     DEFAULT NULL COMMENT '简历原文（存用户传入的原始文本，非模型复述版本）',
    `summary`     VARCHAR(512) DEFAULT NULL COMMENT 'AI 一句话总评',
    `score`       INT          DEFAULT NULL COMMENT '综合评分 0-100',
    -- 三个维度分拆独立列：分数将来要进 WHERE / ORDER BY（按技术分排序、筛低于 60 分的），
    -- 与实体 Resume 的 techScore/projectScore/expressionScore 一一对应，缺了落库必报 Unknown column
    `tech_score`       INT      DEFAULT NULL COMMENT '技术能力评分 0-100',
    `project_score`    INT      DEFAULT NULL COMMENT '项目经验评分 0-100',
    `expression_score` INT      DEFAULT NULL COMMENT '表达与逻辑评分 0-100',
    `review_json` LONGTEXT     DEFAULT NULL COMMENT '结构化评审结果的 JSON 全量，可反序列化回 ResumeReview',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，由数据库时钟生成',
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='简历评审记录';
