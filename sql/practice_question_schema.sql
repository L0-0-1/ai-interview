-- 在 ai_interview 中创建公共题表，并导入首批两道题。
USE `ai_interview`;

CREATE TABLE IF NOT EXISTS `practice_question`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '公共题 ID',
    `skill_tag`      VARCHAR(64)  NOT NULL COMMENT '题目练习的知识点标签',
    `question_text`  TEXT         NOT NULL COMMENT '题干',
    `scoring_points` TEXT         NOT NULL COMMENT '作答后使用的参考评分点',
    `source_url`     VARCHAR(255) NOT NULL COMMENT '参考资料链接',
    PRIMARY KEY (`id`),
    KEY `idx_practice_question_skill_tag` (`skill_tag`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '公共专项练习题';

-- 首批题使用固定 ID。重复导入时保留已存在的题目内容。
INSERT INTO `practice_question` (`id`, `skill_tag`, `question_text`, `scoring_points`, `source_url`)
VALUES
    (1, 'REDIS_STOCK_ATOMICITY',
     '同一商品只剩 1 件，两个请求同时购买。假设可售库存存于 Redis，每个请求购买 1 件，如何做到库存足够才扣减并且只有一个请求成功？说明库存不足时返回什么。',
     '校验库存足够并扣减在同一次原子操作或同一个有效的临界区完成。先执行成功的请求把库存扣至 0，另一个请求看到库存不足并返回失败。',
     'https://redis.io/docs/latest/develop/programmability/eval-intro/'),
    (2, 'REDIS_LOCK_RELEASE',
     'A 的 Redis 锁过期后，B 获取了同一把锁。A 执行释放逻辑时，怎么避免误删 B 的锁？',
     '核对本次抢锁生成的 token，并将比较与删除组成一个原子操作。token 不匹配时保留当前锁。',
     'https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/')
ON DUPLICATE KEY UPDATE `id` = `id`;
SELECT id, question_text
FROM practice_question
WHERE skill_tag = 'REDIS_STOCK';