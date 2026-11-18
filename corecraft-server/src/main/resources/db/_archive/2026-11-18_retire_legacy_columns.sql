-- ============================================================
-- 2026-11-18 退役已下线列与字典（与代码清理同批收口）：
--   1. kp_topic.type_id (+ idx_type_id)：知识点类型轴已于 2026-11-16 D1 退役
--   2. sys_dict_item.mastery_enabled 与 kp_topic_type 字典：类型默认掌握度已于 2026-11-16 D10 退役
--   3. ai_config.enabled：AI 总开关已于 2026-11-16 D6 退役（仅看端点 ready）
--   4. kp_exam_paper.gen_task_id / kp_exam_attempt.grade_task_id：从未使用的冗余外键列
--
-- 执行时机：先重启运行新版后端（不再读写上述列），再在 corecraft 库执行本脚本（幂等）。
-- ============================================================

-- ---------- 1. 跑前自检 ----------
SELECT TABLE_NAME, COLUMN_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'corecraft'
   AND ((TABLE_NAME = 'kp_topic' AND COLUMN_NAME = 'type_id')
     OR (TABLE_NAME = 'sys_dict_item' AND COLUMN_NAME = 'mastery_enabled')
     OR (TABLE_NAME = 'ai_config' AND COLUMN_NAME = 'enabled')
     OR (TABLE_NAME = 'kp_exam_paper' AND COLUMN_NAME = 'gen_task_id')
     OR (TABLE_NAME = 'kp_exam_attempt' AND COLUMN_NAME = 'grade_task_id'));

-- ---------- 2. 幂等删索引与列（MySQL 8 经 information_schema 动态 PREPARE 守卫） ----------
DROP PROCEDURE IF EXISTS corecraft._drop_col_if_exists;
DROP PROCEDURE IF EXISTS corecraft._drop_idx_if_exists;
DELIMITER $$
CREATE PROCEDURE corecraft._drop_idx_if_exists(IN tbl VARCHAR(64), IN idx VARCHAR(64))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = 'corecraft' AND TABLE_NAME = tbl AND INDEX_NAME = idx) THEN
    SET @sql = CONCAT('ALTER TABLE corecraft.`', tbl, '` DROP INDEX `', idx, '`');
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$

CREATE PROCEDURE corecraft._drop_col_if_exists(IN tbl VARCHAR(64), IN col VARCHAR(64))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'corecraft' AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
    SET @sql = CONCAT('ALTER TABLE corecraft.`', tbl, '` DROP COLUMN `', col, '`');
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL corecraft._drop_idx_if_exists('kp_topic', 'idx_type_id');
CALL corecraft._drop_col_if_exists('kp_topic', 'type_id');
CALL corecraft._drop_col_if_exists('sys_dict_item', 'mastery_enabled');
CALL corecraft._drop_col_if_exists('ai_config', 'enabled');
CALL corecraft._drop_col_if_exists('kp_exam_paper', 'gen_task_id');
CALL corecraft._drop_col_if_exists('kp_exam_attempt', 'grade_task_id');

DROP PROCEDURE IF EXISTS corecraft._drop_col_if_exists;
DROP PROCEDURE IF EXISTS corecraft._drop_idx_if_exists;

-- 软删退役字典 kp_topic_type（保留审计可查）
UPDATE corecraft.sys_dict_item i
JOIN corecraft.sys_dict_type t ON i.dict_type_id = t.id
SET i.is_delete = 1
WHERE t.code = 'kp_topic_type' AND i.is_delete = 0;

UPDATE corecraft.sys_dict_type
SET is_delete = 1
WHERE code = 'kp_topic_type' AND is_delete = 0;

-- ---------- 3. 跑后校验（期望返回 0 行） ----------
SELECT TABLE_NAME, COLUMN_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'corecraft'
   AND ((TABLE_NAME = 'kp_topic' AND COLUMN_NAME = 'type_id')
     OR (TABLE_NAME = 'sys_dict_item' AND COLUMN_NAME = 'mastery_enabled')
     OR (TABLE_NAME = 'ai_config' AND COLUMN_NAME = 'enabled')
     OR (TABLE_NAME = 'kp_exam_paper' AND COLUMN_NAME = 'gen_task_id')
     OR (TABLE_NAME = 'kp_exam_attempt' AND COLUMN_NAME = 'grade_task_id'));
