-- 为 kp_topic 表添加 user_id 字段
-- 执行此脚本前请备份数据库

-- 添加 user_id 字段
ALTER TABLE `kp_topic` ADD COLUMN `user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属用户ID' AFTER `id`;

-- 添加索引
ALTER TABLE `kp_topic` ADD KEY `idx_user_id` (`user_id`);

-- 更新已有数据的 user_id（如果有数据的话）
-- 这里假设所有已有数据属于第一个用户，实际使用时需要根据业务情况调整
-- UPDATE `kp_topic` SET `user_id` = 1 WHERE `user_id` = 0;
