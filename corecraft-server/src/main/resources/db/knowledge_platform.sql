-- corecraft-web 业务表：单一 SQL 入口
--
-- 业务表共 5 张：
--   kp_topic         = 知识点（标题、内容、层级归属、所属树）
--   kp_tag           = 标签主数据（按 user 隔离；标签是独立实体，不是 topic 的衍生字段）
--   kp_topic_tag     = 知识点 ↔ 标签 纯关联表（无字段冗余）
--   kp_topic_quiz    = 自测题（多对多）
--   kp_topic_relation = 知识点之间的关联
--
-- 用法：
--   首次部署 / 完全重置 = 跑这一个文件即可（DROP + CREATE 幂等）
--   业务表清空在文件顶部，5 张表按"被引用→引用方"顺序 DROP，再 CREATE
--
-- 不在本文件内：
--   - 菜单 / 权限初始化（见 corecraft-web_menu.sql / menu_init.sql）
--   - 系统管理表（用户/角色/字典等）由 corecraft 脚手架自带 SQL 负责

-- ============================================================
-- 清理
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `kp_topic_relation`;
DROP TABLE IF EXISTS `kp_topic_quiz`;
DROP TABLE IF EXISTS `kp_topic_tag`;
DROP TABLE IF EXISTS `kp_tag`;
DROP TABLE IF EXISTS `kp_topic`;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 知识点表（业务核心）
-- ============================================================

CREATE TABLE IF NOT EXISTS `kp_topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `title` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '知识点标题（未填写时前端展示"未命名"）',
  `content` TEXT COMMENT '知识点内容（Markdown）',
  `parent_topic_id` BIGINT DEFAULT NULL COMMENT '父知识点ID（自关联层级）',
  `tree_id` BIGINT DEFAULT NULL COMMENT '所属知识树ID（按 tag 自动分组回填）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_topic` (`parent_topic_id`),
  KEY `idx_tree_id` (`tree_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点';

-- ============================================================
-- 附属表
-- ============================================================

-- 标签主表（独立实体：按 user 隔离；同 (user, name) 唯一）
-- 仅 name 一个字段；前端用"category::name"展示分组，但 category 不落库
CREATE TABLE IF NOT EXISTS `kp_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_name` (`user_id`, `name`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签主数据';

-- 知识点 ↔ 标签 纯关联表（无字段冗余；改 tag 名 = 改 kp_tag 一行）
CREATE TABLE IF NOT EXISTS `kp_topic_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `topic_id` BIGINT NOT NULL COMMENT '知识点ID',
  `tag_id` BIGINT NOT NULL COMMENT '标签ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_topic_tag` (`topic_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点-标签关联';

-- 关联关系
CREATE TABLE IF NOT EXISTS `kp_topic_relation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `topic_id` BIGINT NOT NULL COMMENT '知识点ID',
  `related_topic_id` BIGINT NOT NULL COMMENT '关联知识点ID',
  `relation_type` VARCHAR(20) NOT NULL DEFAULT 'related' COMMENT '关联类型：prerequisite/extends/contrast/related',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_topic_relation` (`topic_id`, `related_topic_id`),
  KEY `idx_related_topic` (`related_topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点关联';

-- 自测题
CREATE TABLE IF NOT EXISTS `kp_topic_quiz` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `topic_id` BIGINT NOT NULL COMMENT '知识点ID',
  `question_type` VARCHAR(20) NOT NULL DEFAULT 'single' COMMENT '题目类型：single/multiple',
  `question` VARCHAR(500) NOT NULL COMMENT '题目内容',
  `options` JSON NOT NULL COMMENT '选项数组',
  `answer` INT NOT NULL COMMENT '正确答案索引',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_topic_id` (`topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点自测题';
