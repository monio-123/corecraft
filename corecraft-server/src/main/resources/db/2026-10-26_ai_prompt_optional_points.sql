-- 提示词加「要知识点」开关：生成骨架时**可以只要目录，不要知识点**
-- 2026-10-26
--
-- 背景：生成知识包弹窗第一步右下角多一个勾选「**生成知识点**」。
--   勾上 = 目录 + 知识点名（原来的样子）；不勾 = **只要目录**（主题 → 模块 → 章节，三层）。
--
-- 为什么提示词也要改：**提示词逐条列了材料里有什么，不点名模型就当闲话**。
--   这里用"材料里给了《要知识点》"这个信号 ⇒ 勾上时材料里多一段，模型输出到知识点那层；
--   不勾时材料里没有这一项 ⇒ 提示词让它**只到第 3 层**（省掉整层知识点的 token）。
--
-- 改四处（都只动一段，其它一个字不动）：
--   ① 材料清单里加上「要知识点」这一项
--   ② 层级那段加条件：**给了才有叶子那层**，没给就只到第 3 层（也不要总览知识点）
--   ③ 「总览知识点」那段加条件：没给「要知识点」时没有这一层
--   ④ 「每一层放什么」的知识点那行加条件：没给时这一层不存在
--
-- **前端还有第二道闸**：骨架出来后会**按深度裁掉第 4 层**
--   （`CategoryTreeDialog.stripPoints`）—— 模型不听材料里的信号时兜住。
--   ⚠️ 必须**按深度**裁，不能按"叶子"裁：裁掉知识点后章节就成了叶子，
--   而"叶子 = 知识点"是解析器的规则 ⇒ 按叶子裁会把**章节误当成知识点**。
--
-- **这是配置不是表结构**：`sys_config.config_value` 一改即时生效，不用发版。
-- 改的是**系统级**提示词（user_id = 0）⇒ 所有人生效。
--
-- **天然幂等**：全部用 REPLACE 定点替换，old 串不存在时 0 行影响 ⇒ 重复跑安全。
--
-- 跑前建议（内容级还原）：
--   mysqldump -uroot -p<pwd> corecraft sys_config \
--     --where="user_id=0 AND config_key='ai.prompt.knowledge.outline'" \
--     > <工作区外>/corecraft_pre_optional_points_20261026.sql
--
-- 执行时机：随时（只动 corecraft 库）。执行顺序：④①③② 任意，反正是不同片段。


-- ========== ① 跑前自检（四条都要命中 1）============
SELECT config_key, CHAR_LENGTH(config_value) AS 长度,
       (config_value LIKE '%材料里给了**主题**，可能还有**补充说明**和**现有目录清单**。%') AS 待改1,
       (config_value LIKE '%- 叶子：**知识点标题**（书里的"节"，挂在章节目录下面）%')          AS 待改2,
       (config_value LIKE '%总览知识点：%')                                                    AS 待改3,
       (config_value LIKE '%（PCB / 进程上下文切换 / 时间片轮转）%')                            AS 待改4
  FROM corecraft.sys_config
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';


-- ========== ② 改 ==========

-- ②.1 材料清单：加上「要知识点」这一项
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '材料里给了**主题**，可能还有**补充说明**和**现有目录清单**。',
         '材料里给了**主题**，可能还有**补充说明**、**要知识点**和**现有目录清单**。'
       ),
       update_time = NOW()
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';

-- ②.2 层级：给了才有叶子那层
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '- 叶子：**知识点标题**（书里的"节"，挂在章节目录下面）',
         '- 叶子：**知识点标题**（书里的"节"，挂在章节目录下面）\n\n⚠️ **材料里给了「要知识点」时才有叶子那层**；**没给就只到第 3 层**（主题 → 模块 → 章节）—— **不要知识点，也不要总览知识点**。'
       ),
       update_time = NOW()
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';

-- ②.3 总览知识点：没给「要知识点」时没有这一层
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '总览知识点：',
         '总览知识点（**没给「要知识点」时没有这一层**）：'
       ),
       update_time = NOW()
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';

-- ②.4 每一层放什么：知识点那行加条件
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '- 知识点 = 该问题域里的**一个具体概念或机制**（PCB / 进程上下文切换 / 时间片轮转）',
         '- 知识点 = 该问题域里的**一个具体概念或机制**（PCB / 进程上下文切换 / 时间片轮转）**（没给「要知识点」时这一层不存在）**'
       ),
       update_time = NOW()
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';


-- ========== ③ 跑后校验（五条都要命中 1；长度 1641 → 1769）============
SELECT config_key, CHAR_LENGTH(config_value) AS 长度,
       (config_value LIKE '%**要知识点**%')                        AS 新_材料清单,
       (config_value LIKE '%材料里给了「要知识点」时才有叶子那层%')   AS 新_层级条件,
       (config_value LIKE '%没给「要知识点」时没有这一层%')           AS 新_总览条件,
       (config_value LIKE '%没给「要知识点」时这一层不存在%')         AS 新_每层条件,
       (config_value LIKE '%**知识点总数不超过 200 条**%')           AS 仍_总量上限
  FROM corecraft.sys_config
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';


-- ========== 回滚 ==========
-- UPDATE corecraft.sys_config
--    SET config_value = REPLACE(
--          REPLACE(
--            REPLACE(
--              REPLACE(
--                config_value,
--                '- 知识点 = 该问题域里的**一个具体概念或机制**（PCB / 进程上下文切换 / 时间片轮转）**（没给「要知识点」时这一层不存在）**',
--                '- 知识点 = 该问题域里的**一个具体概念或机制**（PCB / 进程上下文切换 / 时间片轮转）'
--              ),
--              '总览知识点（**没给「要知识点」时没有这一层**）：',
--              '总览知识点：'
--            ),
--            '- 叶子：**知识点标题**（书里的"节"，挂在章节目录下面）\n\n⚠️ **材料里给了「要知识点」时才有叶子那层**；**没给就只到第 3 层**（主题 → 模块 → 章节）—— **不要知识点，也不要总览知识点**。',
--            '- 叶子：**知识点标题**（书里的"节"，挂在章节目录下面）'
--          ),
--          '材料里给了**主题**，可能还有**补充说明**、**要知识点**和**现有目录清单**。',
--          '材料里给了**主题**，可能还有**补充说明**和**现有目录清单**。'
--        ),
--        update_time = NOW()
--  WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';
