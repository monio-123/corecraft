-- 2026-11-17 清理「待归类」中历史删树遗留的「学习」类脏数据（type_id = 2），保留原有笔记（type_id = 1）
-- 用途：一次性数据清理（已于 2026-11-17 在 corecraft 库执行，影响 665 行）
-- 回滚：UPDATE corecraft.kp_topic SET is_delete = 0 WHERE category_id IS NULL AND is_delete = 1 AND type_id = 2;

UPDATE corecraft.kp_topic
SET is_delete = 1
WHERE category_id IS NULL
  AND is_delete = 0
  AND type_id = 2;
