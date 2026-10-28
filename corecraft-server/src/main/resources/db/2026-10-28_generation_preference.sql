-- ============================================================
-- 2026-10-28 生成偏好：系统级的一条「口味」，与「补充说明」同一类东西、不同粒度
--
-- ## 这次定下来的三件事（用户拍板）
--
-- ① **做什么**：四项口味 —— ①代码方向给「简洁关键」的源码片段 ②示例用哪种语言/形态
--    ③术语带英文原名 ④图的倾向（适度）。
-- ② **放哪儿**：**系统级**（`user_id=0`），**不做个人覆盖、没有个人入口**。
--    理由：这套系统是"一个人的系统"，偏好不是"我的"，是"生成东西时的口味"，
--    整个系统通用 ⇒ 个人层就是多余的分支。
-- ③ **怎么进模型**：**前端拼进材料**（`utils/knowledgePoints.js` 的 `buildMaterial`，
--    紧跟「补充说明」之后）⇒ **零后端改动**。
--
-- ## 为什么是「另一条配置」而不是改统一提示词
--
-- 分界线：**统一提示词 = 对错与底线**（结构规范、硬约束、宁缺毋滥）；
-- **偏好 = 量与口味**（要多少、侧重什么、给不给某类东西）。
-- 口味会变、底线不会 ⇒ 混在一处，改口味时会连着动底线。
--
-- ## 为什么不需要新界面
--
-- 「系统管理 → 系统配置」那个页（`views/Configs.vue`）本来就是按**配置树**渲染的：
-- 树上每个叶子节点（`sys_config_node`，value_type='text'）都能在页面上改值。
-- ⇒ 只要在树上挂一个节点、在 `sys_config` 里放一行，**这个页上就有这一项**。
-- （注意：**只加 `sys_config` 行是看不见的** —— 页面上只列树上的叶子节点。）
--
-- 执行时机：一次性迁移（已执行）。
-- ============================================================

-- ------------------------------------------------------------
-- 0. 跑前自检
--    ① 父节点 `ai.prompt.knowledge` 必须**恰好一条** —— 本脚本靠它定位父 id，
--       父节点缺了会插 0 行、多了会插 2 行，**两种情况都不报错**
--       （症状：值进了库、页面上的那一项不存在）。
--    ② 目标叶子节点 / 值都还不存在（已存在的话下面两段会跳过，重跑安全）。
--    ③ `ai.prompt.knowledge.point` 里那处待改文本还在（不在就说明前一次已改过）。
-- ------------------------------------------------------------
SELECT '父节点(应为 1)' AS 检查项, COUNT(*) AS 数量
  FROM `corecraft`.`sys_config_node`
 WHERE `config_key` = 'ai.prompt.knowledge'
UNION ALL
SELECT '目标叶子(应为 0)', COUNT(*)
  FROM `corecraft`.`sys_config_node`
 WHERE `config_key` = 'ai.prompt.knowledge.pref'
UNION ALL
SELECT '目标值(应为 0)', COUNT(*)
  FROM `corecraft`.`sys_config`
 WHERE `user_id` = 0 AND `config_key` = 'ai.prompt.knowledge.pref'
UNION ALL
SELECT 'point待改处(应为 1)', COUNT(*)
  FROM `corecraft`.`sys_config`
 WHERE `user_id` = 0
   AND `config_key` = 'ai.prompt.knowledge.point'
   AND `config_value` LIKE '%可能还给了**补充说明**、%';

-- ------------------------------------------------------------
-- 1. 配置树：挂一个叶子节点（挂在「知识体系」ai.prompt.knowledge 下）
--    ⚠️ `COUNT(*) = 1` 那句是**守卫**：父节点多于一条时不插（插两条就是两个父 id）。
-- ------------------------------------------------------------
INSERT INTO `corecraft`.`sys_config_node`
  (`parent_id`, `name`, `code`, `config_key`, `value_type`, `sort`, `remark`, `create_time`, `update_time`)
SELECT p.`id`,
       '生成偏好',
       'pref',
       'ai.prompt.knowledge.pref',
       'text',
       4,
       '生成知识点时的默认口味（系统级）：代码片段怎么给、示例用什么语言、术语带不带英文、图的倾向',
       NOW(),
       NOW()
FROM `corecraft`.`sys_config_node` p
WHERE p.`config_key` = 'ai.prompt.knowledge'
  AND (SELECT COUNT(*) FROM `corecraft`.`sys_config_node` WHERE `config_key` = 'ai.prompt.knowledge') = 1
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT * FROM `corecraft`.`sys_config_node`) x
    WHERE x.`config_key` = 'ai.prompt.knowledge.pref'
  );

-- ------------------------------------------------------------
-- 2. 值：系统默认那一行（user_id=0）
--    ⚠️ 不写个人覆盖行 —— 这一项**只有系统级**。
-- ------------------------------------------------------------
INSERT INTO `corecraft`.`sys_config`
  (`user_id`, `config_key`, `config_value`, `remark`, `create_time`, `update_time`)
SELECT 0,
       'ai.prompt.knowledge.pref',
       '- **代码**：涉及代码、接口、配置、命令的知识点，给**简洁关键的源码片段** —— 只挑讲清机制的那几行（关键方法、关键调用），不要贴整个类的完整实现；片段要能看出「怎么用」，别删减到跑不起来
- **示例形态**：默认 Java；讲数据存取给 SQL；讲流程或伪算法给伪代码；**同一条知识不要同时给 Java 和伪代码**
- **术语**：专业术语第一次出现时带上英文原名（中文名后面括号里给英文）
- **图的倾向**：**适度** —— 关系强（结构 / 层次 / 流程 / 状态）的知识点优先考虑配一张图；**图要贴着这条知识实际讲的东西画**，别画覆盖整个领域的全景图；确实讲不清就别硬凑',
       '生成知识点时的默认口味；改这里 = 改整套系统生成东西时的口味',
       NOW(),
       NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `corecraft`.`sys_config`) x
  WHERE x.`user_id` = 0 AND x.`config_key` = 'ai.prompt.knowledge.pref'
);

-- ------------------------------------------------------------
-- 3. ai.prompt.knowledge.point：材料说明里点名「生成偏好」
--    （模型得知道材料里那一段是什么，不然会当成普通背景）
-- ------------------------------------------------------------
UPDATE `corecraft`.`sys_config`
SET `config_value` = REPLACE(
      `config_value`,
      '可能还给了**补充说明**、',
      '可能还给了**补充说明**、**生成偏好**、'
    )
WHERE `user_id` = 0
  AND `config_key` = 'ai.prompt.knowledge.point'
  AND `config_value` LIKE '%可能还给了**补充说明**、%';


-- ------------------------------------------------------------
-- 4. 跑后校验（前三项都要命中 1）
--    ⚠️ REPLACE 带 LIKE 守卫 ⇒ **"守卫没命中"和"改了"在输出上长得一样**，
--       所以必须显式看这三行：节点 1 行、值 1 行、point 里出现「生成偏好」。
-- ------------------------------------------------------------
SELECT '叶子节点(应为 1)' AS 检查项, COUNT(*) AS 数量
  FROM `corecraft`.`sys_config_node`
 WHERE `config_key` = 'ai.prompt.knowledge.pref'
UNION ALL
SELECT '系统级值(应为 1)', COUNT(*)
  FROM `corecraft`.`sys_config`
 WHERE `user_id` = 0 AND `config_key` = 'ai.prompt.knowledge.pref'
UNION ALL
SELECT 'point已点名生成偏好(应为 1)', COUNT(*)
  FROM `corecraft`.`sys_config`
 WHERE `user_id` = 0
   AND `config_key` = 'ai.prompt.knowledge.point'
   AND `config_value` LIKE '%可能还给了**补充说明**、**生成偏好**、%';


-- ============================================================
-- 回滚
-- ============================================================

-- R1. ai.prompt.knowledge.point：材料说明还原
UPDATE `corecraft`.`sys_config`
SET `config_value` = REPLACE(
      `config_value`,
      '可能还给了**补充说明**、**生成偏好**、',
      '可能还给了**补充说明**、'
    )
WHERE `user_id` = 0
  AND `config_key` = 'ai.prompt.knowledge.point'
  AND `config_value` LIKE '%可能还给了**补充说明**、**生成偏好**、%';

-- R2. 值：删掉那一行
DELETE FROM `corecraft`.`sys_config`
WHERE `user_id` = 0 AND `config_key` = 'ai.prompt.knowledge.pref';

-- R3. 配置树：删掉那个叶子节点
DELETE FROM `corecraft`.`sys_config_node`
WHERE `config_key` = 'ai.prompt.knowledge.pref';
