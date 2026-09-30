-- 2026-09-14 清理脚本
-- 目标：drop 三张 0 行死表（kp_node / kp_node_topic / kp_tree）+ drop kp_tag.category 死列
--
-- 前置：已确认
--   - kp_node / kp_node_topic / kp_tree 库里 0 行；Java 代码 0 引用（只在 FEATURES.md / KNOWLEDGE_TREE_DESIGN.md 提及）
--   - kp_tag.category 库里虽有数（2/3 行非空），但 KpTag entity 无 category 字段，
--     Java 0 处 setCategory/getCategory 引用——纯 schema 残留
--
-- 风险：3 张死表 0 数据，category 列 2 行非空但 Java 不读——可逆成本低
-- 跑前建议：mysqldump -uroot -p corecraft > /tmp/corecraft_pre_drop_20260914.sql

-- ============================================================
-- 1. drop 死表（按"被引用→引用方"顺序；本组实际无 FK，但仍按惯例）
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `kp_node_topic`;
DROP TABLE IF EXISTS `kp_node`;
DROP TABLE IF EXISTS `kp_tree`;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 2. drop kp_tag.category 死列
-- ============================================================

ALTER TABLE `kp_tag` DROP COLUMN `category`;

-- ============================================================
-- 验证（SELECT 出来应当：0 行 + 12 列 kp_tag 无 category + 5 张业务表 + 8 张系统表）
-- ============================================================

-- SELECT COUNT(*) AS kp_node_cnt FROM kp_node;          -- 期望报错：table doesn't exist
-- SELECT COUNT(*) AS kp_tree_cnt FROM kp_tree;          -- 期望报错：table doesn't exist
-- SHOW COLUMNS FROM kp_tag;                              -- 期望无 category
-- SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='corecraft' ORDER BY TABLE_NAME;
