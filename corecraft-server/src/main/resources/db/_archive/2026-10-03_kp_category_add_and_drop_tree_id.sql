-- 2026-10-03 知识目录（category）落地 + 清理标签派生树残留
--
-- 背景：
--   之前"按树"视图由标签派生（getKnowledgeForest 频次 + 集合包含启发式），
--   一个 tag 集合被强制解读成层级，导致：
--     - 深度上限 2 层，无法表达 Java > 集合 > 数组
--     - 频次 1 的标签（"数组"）被当"内容描述"丢弃
--     - 层级随数据量漂移（多记几条 AI 知识，"数组"就从 Java 子节点翻转成父节点）
--   现在改为：目录（单选归属）建树，标签（多选关联）退化为检索筛选。
--
-- 本脚本做三件事：
--   1. 新建 kp_category（知识目录表）
--   2. kp_topic 加 category_id（NULL = 未归类）
--   3. kp_topic 删 tree_id（冗余列）
--
-- 关于第 3 步的数据影响：
--   tree_id 存的是 tagId.hashCode()（见旧 KpTopicServiceImpl.rebuildAutoTrees），
--   是纯派生垃圾值——前端 getKnowledgeForest 从来不读它，只有后端在每次增删改时
--   白费力气重算一遍。删除无信息损失。
--
-- 数据影响面（迁移前已在 corecraft 库核对）：
--   kp_topic 14 行（10 有效 + 4 软删），全部迁移后 category_id = NULL（未归类）
--   不删除任何知识点、不删除任何标签。
--
-- 触发时机：本次迁移跑一次即可。已合并进 knowledge_platform.sql（全新部署直接跑主脚本）。
-- 回滚：把 kp_topic.category_id 置 NULL 即回到"未归类"状态，无需回滚 DDL。

-- ============================================================
-- 1. 知识目录表
-- ============================================================

-- 刻意不带 is_delete：目录没有独立价值（删了就是没了），物理删更干净。
-- 若做软删，uk_user_name 唯一键会让"删过的目录名"永久无法重建（墓碑撞唯一键）。
-- 代价：KpCategory 实体不继承 BaseEntity。
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
-- 2. 知识点挂目录
-- ============================================================

ALTER TABLE `kp_topic`
  ADD COLUMN `category_id` BIGINT DEFAULT NULL COMMENT '所属知识目录ID（NULL=未归类；单选，互斥）' AFTER `parent_topic_id`,
  ADD KEY `idx_category_id` (`category_id`);

-- ============================================================
-- 3. 清理标签派生树残留
-- ============================================================

ALTER TABLE `kp_topic`
  DROP KEY `idx_tree_id`,
  DROP COLUMN `tree_id`;
