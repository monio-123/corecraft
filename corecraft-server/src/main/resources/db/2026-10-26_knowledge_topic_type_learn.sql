-- ============================================================
-- 2026-10-26 生成物类型改「学习」：两棵 MySQL 树的存量知识点 + 出卷分组的类型语义
--
-- ## 为什么改
--
-- AI 生成的知识点是**系统整理过的学习内容**（有定义、原理、对比、应用），
-- 落库时却全被标成「笔记」（随手记那一档）。
--
-- 根因不在数据，在**生成链路没传 typeId**：
-- `CategoryTreeDialog.vue` 调 `createTopic({title, content, categoryId, tags})` —— 不传 typeId
-- ⇒ store 落"字典里排第一的那条" = 「笔记」（`defaultTopicTypeId()`）。
-- ⇒ 两棵 MySQL 树 237 条全是「笔记」，界面上完全看不出来。
--
-- 改动分三处：
--
-- ① **前端生成链路**（不在本脚本里）：显式传「学习」档
--    （`topicTypeIdByValue('learn')` —— 按字典 **value** 认，不写死 id）。
--    字典里没有这一档就**直接报错停**，不静默落回默认档
--    （落回去 = 生成一批类型全错、还查不出哪错的知识点）。
--
-- ② **存量数据**（本脚本 1.1）：两棵 MySQL 树下的知识点 type_id 改成「学习」。
--
-- ③ **出卷规范**（本脚本 2.1 / 2.2）：`ai.prompt.exam.paper` 里【笔记】/【学习】
--    的语义同步翻过来。**不改就会对不上** —— 材料按类型分组后，
--    规范还写着"优先用【笔记】里的原文"，而具体结论现在都在【学习】那一组里
--    ⇒ 出卷会退到"拿随手记当依据"。
--
-- ## 影响面（执行前实测）
--
-- - 628「MySQL-abandoned」子树：117 条（id 634–750）
-- - 664「MySQL」子树：120 条（id 752–871）
-- - 两棵之外：44 条（42 条「笔记」+ 2 条「学习」）—— **手写的不动**
--
-- 后端 `AiChatServiceImpl.digestOf` **只按类型分组、不认语义**
-- （代码注释原话："代码只认'按类型分组'，不认'笔记=细节层'这种语义"）
-- ⇒ 分组那段代码一行不用改，语义全在提示词里。
--
-- 执行时机：一次性迁移（已执行）。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 存量：两棵 MySQL 树下的知识点 → 「学习」档
-- ------------------------------------------------------------

-- 1.1 正文类型
--
-- 「学习」档的 id 从**字典里取**（value = 'learn'），不写死数字：
-- 字典项 id 是自增的，换台库重建就可能不是 2。
-- 递归 CTE 圈出两棵树的全部目录（树有 3 层：主题 → 模块 → 章节）。
WITH RECURSIVE `sub` AS (
  SELECT `id`
  FROM `corecraft`.`kp_category`
  WHERE `id` IN (628, 664)
  UNION ALL
  SELECT c.`id`
  FROM `corecraft`.`kp_category` c
  JOIN `sub` s ON c.`parent_id` = s.`id`
)
UPDATE `corecraft`.`kp_topic` t
JOIN `sub` ON t.`category_id` = `sub`.`id`
JOIN `corecraft`.`sys_dict_item` i
  ON i.`value` = 'learn' AND i.`is_enabled` = 1 AND i.`is_delete` = 0
JOIN `corecraft`.`sys_dict_type` d
  ON d.`id` = i.`dict_type_id` AND d.`code` = 'kp_topic_type' AND d.`is_delete` = 0
SET t.`type_id` = i.`id`
WHERE t.`is_delete` = 0
  AND t.`type_id` <> i.`id`;


-- ------------------------------------------------------------
-- 2. 出卷规范：类型分组语义翻过来
-- ------------------------------------------------------------

-- 2.1 组头的类型说明
--
-- 翻过来之后：**【学习】= 系统整理过的知识点**（生成物，有定义/原理/应用），
-- **【笔记】= 随手记的零散内容**（人手写的）。
UPDATE `corecraft`.`sys_config`
SET `config_value` = REPLACE(
      `config_value`,
      '- 材料按类型分组，每组前面标着这一组的类型名：【笔记】是具体知识点，【学习】是这个领域的框架与体系',
      '- 材料按类型分组，每组前面标着这一组的类型名：【学习】是系统整理过的知识点（有定义、原理、应用），【笔记】是随手记的零散内容'
    )
WHERE `user_id` = 0
  AND `config_key` = 'ai.prompt.exam.paper'
  AND `config_value` LIKE '%【笔记】是具体知识点，【学习】是这个领域的框架与体系%';

-- 2.2 单选/多选/填空的取材优先序
--
-- 具体结论现在在【学习】那一组里 ⇒ 优先用【学习】的原文；
-- 只有【笔记】时才用【笔记】里写明的具体结论。
--
-- ⚠️ 出**概念题/面试题**那条**不用改**：它本来就写着"优先用【学习】的框架叙述"，
-- 翻过来之后这句依然成立（【学习】才是有结构叙述的那一组）。
UPDATE `corecraft`.`sys_config`
SET `config_value` = REPLACE(
      `config_value`,
      '- 出单选、多选、填空时，正确答案必须能在材料里找到明确依据，不能靠常识补：优先用【笔记】里的原文；材料里没有【笔记】、只有【学习】时，就用【学习】里写明的具体结论，只是罗列结构、没有具体结论的部分不当考点',
      '- 出单选、多选、填空时，正确答案必须能在材料里找到明确依据，不能靠常识补：优先用【学习】里的原文；材料里没有【学习】、只有【笔记】时，就用【笔记】里写明的具体结论，只是零散罗列、没有具体结论的部分不当考点'
    )
WHERE `user_id` = 0
  AND `config_key` = 'ai.prompt.exam.paper'
  AND `config_value` LIKE '%优先用【笔记】里的原文%';


-- ============================================================
-- 回滚
-- ============================================================

-- R1. 类型还原成「笔记」
WITH RECURSIVE `sub` AS (
  SELECT `id`
  FROM `corecraft`.`kp_category`
  WHERE `id` IN (628, 664)
  UNION ALL
  SELECT c.`id`
  FROM `corecraft`.`kp_category` c
  JOIN `sub` s ON c.`parent_id` = s.`id`
)
UPDATE `corecraft`.`kp_topic` t
JOIN `sub` ON t.`category_id` = `sub`.`id`
JOIN `corecraft`.`sys_dict_item` i
  ON i.`value` = 'note' AND i.`is_enabled` = 1 AND i.`is_delete` = 0
JOIN `corecraft`.`sys_dict_type` d
  ON d.`id` = i.`dict_type_id` AND d.`code` = 'kp_topic_type' AND d.`is_delete` = 0
SET t.`type_id` = i.`id`
WHERE t.`is_delete` = 0
  AND t.`type_id` <> i.`id`;

-- R2. 出卷规范：组头说明还原
UPDATE `corecraft`.`sys_config`
SET `config_value` = REPLACE(
      `config_value`,
      '- 材料按类型分组，每组前面标着这一组的类型名：【学习】是系统整理过的知识点（有定义、原理、应用），【笔记】是随手记的零散内容',
      '- 材料按类型分组，每组前面标着这一组的类型名：【笔记】是具体知识点，【学习】是这个领域的框架与体系'
    )
WHERE `user_id` = 0
  AND `config_key` = 'ai.prompt.exam.paper'
  AND `config_value` LIKE '%【学习】是系统整理过的知识点%';

-- R3. 出卷规范：取材优先序还原
UPDATE `corecraft`.`sys_config`
SET `config_value` = REPLACE(
      `config_value`,
      '- 出单选、多选、填空时，正确答案必须能在材料里找到明确依据，不能靠常识补：优先用【学习】里的原文；材料里没有【学习】、只有【笔记】时，就用【笔记】里写明的具体结论，只是零散罗列、没有具体结论的部分不当考点',
      '- 出单选、多选、填空时，正确答案必须能在材料里找到明确依据，不能靠常识补：优先用【笔记】里的原文；材料里没有【笔记】、只有【学习】时，就用【学习】里写明的具体结论，只是罗列结构、没有具体结论的部分不当考点'
    )
WHERE `user_id` = 0
  AND `config_key` = 'ai.prompt.exam.paper'
  AND `config_value` LIKE '%优先用【学习】里的原文%';
