-- 已建过 interview_session 的数据库执行一次。新库直接使用 interview_schema.sql。
USE `ai_interview`;

ALTER TABLE `interview_session`
    ADD COLUMN `user_id` BIGINT DEFAULT NULL COMMENT '所属账号 ID，旧演示记录可为空' AFTER `id`;
SELECT id, user_id FROM ai_interview.interview_session LIMIT 5;
SELECT id, user_id, current_round, status
FROM ai_interview.interview_session
ORDER BY id DESC
LIMIT 5;