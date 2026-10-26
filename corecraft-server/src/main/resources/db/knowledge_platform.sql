-- corecraft-web 业务表：单一 SQL 入口
--
-- 本文件建的表（共 7 张）：
--   kp_topic         = 知识点（标题、内容、类型、层级归属、所属目录）
--   kp_category      = 知识目录（树的骨架；知识点单选归属，标签不参与建树）
--   kp_tag           = 标签主数据（按 user 隔离；多选关联点，只做检索，不进树）
--   kp_topic_tag     = 知识点 ↔ 标签 纯关联表（无字段冗余）
--   kp_topic_relation = 知识点之间的关联
--   ai_config        = AI 总开关（每人一行）
--   ai_endpoint      = AI 接口（每人多个）
--
-- ⚠️ **这里的表结构 = 线上库的现状**（2026-10-26 逐列比对 information_schema 核过）。
--    以前这里缺 kp_topic.type_id、缺 ai_endpoint.params、kp_category 的唯一键还是老的
--    ⇒ 照着这个文件建出来的新库，代码一查就 Unknown column。
--    **结构只存这一份**：以后加列 = 改这里 + 另写一个一次性迁移脚本给"已存在的库"用。
--
-- 用法：
--   首次部署 / 完全重置 = 跑这一个文件（DROP + CREATE，**可重复跑**）
--   业务表清空在文件顶部，表按"被引用→引用方"顺序 DROP，再 CREATE
--
--   ⚠️ **跑完这个文件不要再按文件名重放其它一次性迁移脚本** —— 那些是给"已存在的库"用的
--      （各自带跑前自检），重放会撞 Duplicate column / Duplicate key。
--      唯一要跟着一块跑的：**2026-10-26_ai_vendor_catalog.sql**（灌厂商 + 模型的字典数据，
--      它的补列段是幂等的，见那个文件）。
--
-- 不在本文件内：
--   - 菜单 / 权限初始化（见 corecraft-web_menu.sql / menu_init.sql）
--   - 系统管理表（用户/角色/字典等）由 corecraft 脚手架自带 SQL 负责
--   - 厂商 / 模型字典数据（见 2026-10-26_ai_vendor_catalog.sql）

-- ============================================================
-- 清理
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `kp_topic_relation`;
-- kp_topic_quiz：旧自测题那套已经整块撤掉（2026-10-22），这里只做清理
-- —— 老库里可能还留着，重置时顺手删掉。
DROP TABLE IF EXISTS `kp_topic_quiz`;
DROP TABLE IF EXISTS `kp_topic_tag`;
DROP TABLE IF EXISTS `kp_tag`;
DROP TABLE IF EXISTS `kp_topic`;
DROP TABLE IF EXISTS `kp_category`;
DROP TABLE IF EXISTS `ai_endpoint`;
DROP TABLE IF EXISTS `ai_config`;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 知识点表（业务核心）
-- ============================================================

CREATE TABLE IF NOT EXISTS `kp_topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `title` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '知识点标题（未填写时前端展示"未命名"）',
  `content` TEXT COMMENT '知识点内容（Markdown）',
  `type_id` BIGINT DEFAULT NULL COMMENT '知识点类型（NULL=笔记，默认档）',
  `parent_topic_id` BIGINT DEFAULT NULL COMMENT '父知识点ID（自关联层级）',
  `category_id` BIGINT DEFAULT NULL COMMENT '所属知识目录ID（NULL=未归类；单选，互斥）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_topic` (`parent_topic_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_type_id` (`type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点';

-- ============================================================
-- 知识目录表（树的骨架）
-- ============================================================

-- 目录与标签是两个正交概念，别混用：
--   目录 = 归属（这条知识属于哪），单选互斥，树视图的节点
--   标签 = 关联（这条知识涉及什么），多选平铺，只做检索筛选
--
-- 刻意不带 is_delete：目录没有独立价值（删了就是没了），物理删更干净。
-- 若做软删，唯一键会让"删过的目录名"永久无法重建。
--
-- ⚠️ **唯一键带 parent_id**（uk_user_parent_name）：目录是有层级的，
--    同名目录可以出现在不同父目录下（"索引"可以在"MySQL"下也可以在"Redis"下）。
--    老键 uk_user_name(user_id, name) 会把这种情况挡掉。
CREATE TABLE IF NOT EXISTS `kp_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `name` VARCHAR(50) NOT NULL COMMENT '目录名',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父目录ID（NULL=顶层；实体 getParentId() 把历史 NULL 归一成 0）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_parent_name` (`user_id`, `parent_id`, `name`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识目录';

-- ============================================================
-- 附属表
-- ============================================================

-- 标签主表（独立实体：按 user 隔离；同 (user, name) 唯一）
-- 仅 name 一个字段；前端用"category::name"展示分组，但 category 不落库
-- ⚠️ 唯一键名跟线上库一致（`uk_user_category_name`）：键名是当年 category 还落库时留下的，
--    列没变（user_id + name）。这里跟线上库同名，两边才对得上。
CREATE TABLE IF NOT EXISTS `kp_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_category_name` (`user_id`, `name`),
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

-- ⚠️ **旧自测题那套（kp_topic_quiz）已经整块撤掉**（2026-10-22）：题存在库里 + 本地判分
--    + 每日复习。旧那套和新的「考试」（临时、AI 判分、题量/难度可配）是两种东西，
--    留着就一直是两套题型表、两份解析器。**出题现在由 `ai.prompt.exam.*` 那组提示词负责。**
--    ⇒ **这里不再建它**（以前建了、又让 2026-10-22 那个脚本删掉，纯属绕一圈；
--      而"跑这一个文件 = 当前结构"的前提也要求它不存在 —— 线上库里它早没了）。
--    撤表脚本：2026-10-22_drop_old_quiz_feature.sql（数据备份在 _archive/）。

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
CREATE TABLE IF NOT EXISTS `ai_config` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户ID',
  `enabled`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用AI功能：0关闭 1开启',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI设置：每人一行，只管开关。接口在 ai_endpoint';

CREATE TABLE IF NOT EXISTS `ai_endpoint` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户ID',
  `name`        VARCHAR(64)  NOT NULL COMMENT '自己起的名字，列表里靠它区分',
  `base_url`    VARCHAR(255)          DEFAULT NULL COMMENT 'OpenAI兼容接口地址，例：https://api.deepseek.com/v1',
  `model`       VARCHAR(100)          DEFAULT NULL COMMENT '模型名，例：deepseek-chat',
  `params`      TEXT                   DEFAULT NULL COMMENT '端点附加参数 JSON：{"vendor":"厂商code","custom":{...}}。custom 展开进 chat 请求体（不能覆盖 model/stream/messages）',
  `api_key`     VARCHAR(255)          DEFAULT NULL COMMENT 'API Key。只存后端，绝不返回给前端',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI接口：每人多个，提问时选一个用';