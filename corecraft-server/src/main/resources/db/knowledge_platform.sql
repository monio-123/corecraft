-- corecraft-web 业务表：单一 SQL 入口
--
-- 业务表共 6 张：
--   kp_topic         = 知识点（标题、内容、层级归属、所属目录）
--   kp_category      = 知识目录（树的骨架；知识点单选归属，标签不参与建树）
--   kp_tag           = 标签主数据（按 user 隔离；多选关联点，只做检索，不进树）
--   kp_topic_tag     = 知识点 ↔ 标签 纯关联表（无字段冗余）
--   kp_topic_quiz    = 自测题（多对多）
--   kp_topic_relation = 知识点之间的关联
--
-- 用法：
--   首次部署 / 完全重置 = 跑这一个文件即可（DROP + CREATE 幂等）
--   业务表清空在文件顶部，6 张表按"被引用→引用方"顺序 DROP，再 CREATE
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
DROP TABLE IF EXISTS `kp_category`;

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
  `category_id` BIGINT DEFAULT NULL COMMENT '所属知识目录ID（NULL=未归类；单选，互斥）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_topic` (`parent_topic_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点';

-- ============================================================
-- 知识目录表（树的骨架）
-- ============================================================

-- 目录与标签是两个正交概念，别混用：
--   目录 = 归属（这条知识属于哪），单选互斥，树视图的节点
--   标签 = 关联（这条知识涉及什么），多选平铺，只做检索筛选
--
-- 刻意不带 is_delete：目录没有独立价值（删了就是没了），物理删更干净。
-- 若做软删，uk_user_name 唯一键会让"删过的目录名"永久无法重建。
CREATE TABLE IF NOT EXISTS `kp_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `name` VARCHAR(50) NOT NULL COMMENT '目录名',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父目录ID（预留：后续目录树拖拽改层级用，当前恒为 NULL）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_name` (`user_id`, `name`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识目录';

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

-- ============================================================
-- AI 设置 + AI 接口（2026-10-06 新增，2026-10-07 拆成两张表）
-- 详细取舍见同目录 2026-10-07_ai_endpoint_multi.sql 的头部注释。
--
-- **两张表各管一件事**：
--   ai_config   —— 一人一行，只有一个总开关。enabled 默认 0：没开启就不出现任何 AI 入口。
--   ai_endpoint —— 一人多行，每个 base_url + model + api_key 一行，提问时选一个用。
--   name 是自己起的别名，列表里靠它区分（同一个厂商可能配两个不同模型）。
--
-- 不设 provider 字段（厂商靠 base_url 决定）；
-- 不放 sys_dict（字典表无 user_id，放进去就是全局一份，与"每人一份"矛盾）。
-- 「当前选中哪个接口」不落库，存前端 localStorage——那是当下的临时选择，不是用户资料。
-- ============================================================
CREATE TABLE `ai_config` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户ID',
  `enabled`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用AI功能：0关闭 1开启',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI设置：每人一行，只管开关。接口在 ai_endpoint';

CREATE TABLE `ai_endpoint` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户ID',
  `name`        VARCHAR(64)  NOT NULL COMMENT '自己起的名字，列表里靠它区分',
  `base_url`    VARCHAR(255)          DEFAULT NULL COMMENT 'OpenAI兼容接口地址，例：https://api.deepseek.com/v1',
  `model`       VARCHAR(100)          DEFAULT NULL COMMENT '模型名，例：deepseek-chat',
  `api_key`     VARCHAR(255)          DEFAULT NULL COMMENT 'API Key。只存后端，绝不返回给前端',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI接口：每人多个，提问时选一个用';