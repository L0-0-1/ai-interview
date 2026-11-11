-- =====================================================================
-- 模拟面试模块建表脚本（第一个小交付：开始面试并生成第一题）
-- 数据库：MySQL 8
--
-- 与 schema.sql 的分工：
--   schema.sql 管简历评审模块（Resume 表），本脚本只新增面试模块两张表，
--   不修改、不删除任何已有表和数据。两张脚本可分别执行。
--
-- 用法（数据库 ai_interview 已由 schema.sql 创建）：
--   mysql -u root -p ai_interview < sql/interview_schema.sql
-- 或在客户端里切到 ai_interview 库后整段执行。
-- =====================================================================

USE `ai_interview`;

-- ---------------------------------------------------------------------
-- 面试会话表（一对多里的"一"方）
--
-- 表名全小写蛇形：新表不再沿用 Resume 表首字母大写的历史命名，
-- 与 MySQL 常规命名一致；实体类 @TableName("interview_session") 显式写死，
-- Linux 下 lower_case_table_names=0 区分大小写也不怕（与 Resume 显式写死同款理由）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_session`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `resume_text`      LONGTEXT     NOT NULL COMMENT '简历原文快照：后续每一轮出题/追问都依据它，请求方只需在开始时传一次',
    `target_position`  VARCHAR(128) NOT NULL COMMENT '目标岗位：出题围绕岗位要求展开',
    `current_round`    INT          NOT NULL COMMENT '当前轮次，创建时为 1（每完成一轮回答 +1，留给后续回答流程）',
    `max_round`        INT          NOT NULL COMMENT '最大轮数，创建时为 8（到达后结束面试的兜底逻辑留给后续）',
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'ONGOING' COMMENT '会话状态：ONGOING 进行中 / ENDED 已结束（ENDED 留给后续结束流程）',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，由数据库时钟生成',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='模拟面试会话表';

-- ---------------------------------------------------------------------
-- 面试消息表（一对多里的"多"方）
--
-- 与 interview_session 的一对多关联是「逻辑外键」：
--   session_id 列的值指向 interview_session.id，一个会话对应多条消息。
-- 为什么只建索引、不建 FOREIGN KEY 物理外键：
--   1. 数据一致性由应用层事务保证（见 InterviewSessionService 的短事务）；
--   2. 物理外键带级联删除语义，误删会话会连带清空消息，学习期风险大于收益；
--   3. 索引 idx_session_id 支撑"按会话查全部消息"这条最频繁的查询路径
--      （后续回答/追问流程每次都要按会话回放历史，没有索引就是全表扫描）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interview_message`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`  BIGINT       NOT NULL COMMENT '所属会话 id，对应 interview_session.id（逻辑外键）',
    `role`        VARCHAR(16)  NOT NULL COMMENT '消息角色：INTERVIEWER 面试官 / CANDIDATE 候选人（CANDIDATE 留给后续回答流程）',
    `content`     TEXT         NOT NULL COMMENT '消息内容：面试官的题目，或候选人的回答',
    `round_no`    INT          NOT NULL COMMENT '所属轮次，从 1 开始',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，由数据库时钟生成',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='面试消息表';
