-- ============================================================
-- 2026-11-12 掌握度「参与开关」（类型默认 + 单条覆盖）
--
-- ## 为什么
--
-- 掌握度只对**学习型内容**有意义：随手记、备忘、服务器地址这类东西本来就不是
-- "要学会的东西"，在它们身上挂「掌握度 / 标记已学」只会让界面变脏。
--
-- 但"哪些算学习内容"**不能写死在代码里**：
--   ① 类型是**字典**（sys_dict_item / kp_topic_type），管理员能增删；
--      代码里写死 "typeId == 学习" 等于把字典的语义抄一份，字典一变就漂。
--   ② 用户说的原话是"**这个忽略还可以用于学习那边一些不重要的，比如总览**"
--      ⇒ 判据必须是**可配置**的，而不是按类型硬编码。
--
-- ## 口径（解析顺序只有这一条，后端一处判据）
--
--   单条显式值（kp_topic.mastery_enabled）
--     > 类型默认值（sys_dict_item.mastery_enabled）
--     > 安全兜底（不参与）
--
-- 三值语义：NULL = 「没配置，继承上一层」/ TRUE = 参与 / FALSE = 不参与。
-- ⚠️ **NULL 和 FALSE 完全是两回事**，不能把 NULL 当成 FALSE：
--    NULL = 继承类型默认（笔记类型默认关、学习类型默认开）；
--    FALSE = 这条明确不参与（哪怕它属于"学习"类型）。
--
-- ## 执行
--
-- 幂等，可重复跑（列用 information_schema 判断，UPDATE 只在当前为 NULL 时落值）。
-- 只动 corecraft 库。
-- ============================================================


-- ========== ① 跑前自检 ==========
-- 期望：0（列还不存在）。
SELECT COUNT(*) AS 已存在的 mastery_enabled 列 FROM information_schema.columns
 WHERE table_schema = 'corecraft'
   AND ((table_name = 'kp_topic' AND column_name = 'mastery_enabled')
     OR (table_name = 'sys_dict_item' AND column_name = 'mastery_enabled'));


-- ========== ② kp_topic：单条覆盖 ==========
-- NULL = 继承类型默认（**不是**"不参与"）。存量行全部留 NULL ⇒ 自动继承类型。
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'corecraft' AND table_name = 'kp_topic' AND column_name = 'mastery_enabled') > 0,
  'SELECT ''kp_topic.mastery_enabled 列已存在，跳过'' AS notice',
  'ALTER TABLE `corecraft`.`kp_topic` ADD COLUMN `mastery_enabled` TINYINT(1) DEFAULT NULL COMMENT ''是否参与掌握度：NULL=继承类型默认；1=参与；0=不参与'' AFTER `type_id`');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ========== ③ sys_dict_item：类型默认 ==========
-- 这是通用字典表，**只有 kp_topic_type 这一套字典项真的消费它**；
-- 其它字典的项留 NULL（= 没配置这条规则）。
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'corecraft' AND table_name = 'sys_dict_item' AND column_name = 'mastery_enabled') > 0,
  'SELECT ''sys_dict_item.mastery_enabled 列已存在，跳过'' AS notice',
  'ALTER TABLE `corecraft`.`sys_dict_item` ADD COLUMN `mastery_enabled` TINYINT(1) DEFAULT NULL COMMENT ''该类型默认是否参与掌握度：NULL=未配置；1=参与；0=不参与'' AFTER `remark`');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ========== ④ 类型默认值落库 ==========
-- 笔记(note) 默认不参与、学习(learn) 默认参与。
-- ⚠️ **只给当前是 NULL 的行落值**（WHERE mastery_enabled IS NULL）：
--    管理员之后手工改过的值不能被这条重跑覆盖掉。
UPDATE `corecraft`.`sys_dict_item`
   SET `mastery_enabled` = CASE `value`
         WHEN 'note' THEN 0
         WHEN 'learn' THEN 1
         ELSE `mastery_enabled`
       END
 WHERE `dict_type_id` = (SELECT `id` FROM `corecraft`.`sys_dict_type` WHERE `code` = 'kp_topic_type')
   AND `mastery_enabled` IS NULL;


-- ========== ⑤ 回读校验 ==========
-- 期望：note → 0，learn → 1。
SELECT i.`id`, i.`label`, i.`value`, i.`mastery_enabled`
  FROM `corecraft`.`sys_dict_item` i
  JOIN `corecraft`.`sys_dict_type` t ON t.`id` = i.`dict_type_id`
 WHERE t.`code` = 'kp_topic_type'
 ORDER BY i.`id`;

-- 期望：kp_topic 的存量行 mastery_enabled 全是 NULL（= 继承类型）。
SELECT COUNT(*) AS 显式关闭的条数 FROM `corecraft`.`kp_topic`
 WHERE `mastery_enabled` = 0;


-- ============================================================
-- 回滚（确认不要之后手动执行）
-- UPDATE `corecraft`.`sys_dict_item` SET `mastery_enabled` = NULL
--  WHERE `dict_type_id` = (SELECT `id` FROM `corecraft`.`sys_dict_type` WHERE `code` = 'kp_topic_type');
-- UPDATE `corecraft`.`kp_topic` SET `mastery_enabled` = NULL;
-- ALTER TABLE `corecraft`.`kp_topic` DROP COLUMN `mastery_enabled`;
-- ALTER TABLE `corecraft`.`sys_dict_item` DROP COLUMN `mastery_enabled`;
-- ============================================================
