USE `ai_interview`;

CREATE TABLE IF NOT EXISTS `user_account`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `username`      VARCHAR(64)  NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_account_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户账号表';
