-- 提示词撤掉「要知识点」开关：骨架**永远有知识点那层**，勾选框只管"要不要写正文"
-- 2026-10-26
--
-- 背景：上一轮（`2026-10-26_ai_prompt_optional_points.sql`）给 outline 提示词加了
--   「材料里给了《要知识点》才这样；没给就只到章节层」这一套条件（**6 处**），
--   配套前端还有一道闸（按深度裁第 4 层）。
--
-- **这一轮把那条路整个撤掉**：弹窗里那个勾选框的语义不是"要不要知识点名"，
--   而是"**要不要连正文一起生成**" ⇒
--   - 知识点**名称总是要的**（骨架永远 4 层：主题 → 模块 → 章节 → 知识点）。
--     理由：没有标题，正文就没有挂的地方，知识点之间的链接也没有目标；
--     而且"先生成目录和知识点、正文另一次"正是上一轮解耦的成果，不该被抽掉。
--   - 勾选框 ⇒ 只管**正文**：勾上 = 骨架建完接着写正文（跟解耦前一样一次做完）；
--     不勾 = 只落结构（目录 + 知识点名），正文用目录「⋯ → 生成正文」另一次生成。
--
-- ⇒ 「只要目录」这个用法**没有了** ⇒ 提示词里那套条件句**永远走不到**
--   （材料里不再有《要知识点》这一项）⇒ 留着就是在教模型一件不成立的事 ⇒ 撤掉。
-- ⇒ 前端配套撤掉的：`outlineTree.js` 的 `noPoints`（markOutline / importPlan）
--   和 `stripPoints`（按深度裁第 4 层）—— 它们已经没有调用方了（明显死代码，不豁免）。
--
-- **顺带修一个上一轮的脚本缺陷**：`2026-10-26_ai_prompt_optional_points.sql`
--   写的是"改四处"，但**实际改了六处**（漏了"开头首句"和"结构例子"两处），
--   它的"跑后校验"也写着 1641 → 1769，而实际落到 **1856** ⇒
--   **那个脚本重放不出库里的现状**。本脚本按**实际库值**逐条写，撤完正好回到 **1641**
--   （= `2026-10-26_ai_prompt_outline_shape.sql` 之后的版本，那版的实测结论仍然有效）。
--
-- **这是配置不是表结构**：`sys_config.config_value` 一改即时生效，不用发版。
-- 改的是**系统级**提示词（user_id = 0）⇒ 所有人生效。
--
-- **天然幂等**：全部用 REPLACE 定点替换，old 串不存在时 0 行影响 ⇒ 重复跑安全。
--
-- 跑前建议（内容级还原）：
--   mysqldump -uroot -p<pwd> corecraft sys_config \
--     --where="user_id=0 AND config_key='ai.prompt.knowledge.outline'" \
--     > <工作区外>/corecraft_pre_points_always_20261026.sql
--
-- 执行时机：随时（只动 corecraft 库）。六条互不重叠，顺序任意。


-- ========== ① 跑前自检（长度应为 1851，六条都命中 1）============
SELECT config_key, CHAR_LENGTH(config_value) AS 长度,
       (config_value LIKE '%（**材料里给了「要知识点」才这样；没给就只到章节层**）%')                     AS 待撤1_首句,
       (config_value LIKE '%材料里给了**主题**，可能还有**补充说明**、**要知识点**和**现有目录清单**%')       AS 待撤2_材料清单,
       (config_value LIKE '%材料里给了「要知识点」时才有叶子那层%')                                        AS 待撤3_层级条件,
       (config_value LIKE '%（PCB / 进程上下文切换 / 时间片轮转）**（没给「要知识点」时这一层不存在）**%')    AS 待撤4_每层条件,
       (config_value LIKE '%总览知识点（**没给「要知识点」时没有这一层**）%')                              AS 待撤5_总览条件,
       (config_value LIKE '%没给「要知识点」时照这个层次的前三层%')                                        AS 待撤6_结构例子
  FROM corecraft.sys_config
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';


-- ========== ② 改（六条）============

-- ②.1 开头首句：去掉「材料里给了《要知识点》才这样」那半句
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '知识点（**材料里给了「要知识点」才这样；没给就只到章节层**）。',
         '知识点。'
       ),
       update_time = NOW()
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';

-- ②.2 材料清单：去掉「要知识点」这一项
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '材料里给了**主题**，可能还有**补充说明**、**要知识点**和**现有目录清单**。',
         '材料里给了**主题**，可能还有**补充说明**和**现有目录清单**。'
       ),
       update_time = NOW()
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';

-- ②.3 层级那段：删掉整条 ⚠️ 条件（它前后各有一个空行）
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         CONCAT(CHAR(10), CHAR(10),
                '⚠️ **材料里给了「要知识点」时才有叶子那层**；**没给就只到第 3 层**（主题 → 模块 → 章节）—— **不要知识点，也不要总览知识点**。'),
         ''
       ),
       update_time = NOW()
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';

-- ②.4 「每一层放什么」：去掉知识点那行的条件
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '（PCB / 进程上下文切换 / 时间片轮转）**（没给「要知识点」时这一层不存在）**',
         '（PCB / 进程上下文切换 / 时间片轮转）'
       ),
       update_time = NOW()
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';

-- ②.5 总览知识点：去掉条件
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '总览知识点（**没给「要知识点」时没有这一层**）：',
         '总览知识点：'
       ),
       update_time = NOW()
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';

-- ②.6 结构例子：去掉条件（后半句）
UPDATE corecraft.sys_config
   SET config_value = REPLACE(
         config_value,
         '结构例子（**照这个层次来，内容换成主题自己的**；⚠️ **没给「要知识点」时照这个层次的前三层**——主题 → 模块 → 章节，**不出现知识点、也不出现总览**）：',
         '结构例子（**照这个层次来，内容换成主题自己的**）：'
       ),
       update_time = NOW()
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';


-- ========== ③ 跑后校验（长度应为 1641，六条都要 0；总量上限仍在）============
SELECT config_key, CHAR_LENGTH(config_value) AS 长度,
       (config_value LIKE '%要知识点%')                                       AS 应为0_残留要知识点,
       (config_value LIKE '%没给%')                                           AS 应为0_残留没给,
       (config_value LIKE '%**知识点总数不超过 200 条**%')                     AS 仍_总量上限,
       (config_value LIKE '%**数量按内容重要性分配，不要平均分配**%')           AS 仍_分配原则,
       (config_value LIKE '%- 叶子：**知识点标题**%')                          AS 仍_叶子那层,
       (config_value LIKE '%总览知识点：%')                                    AS 仍_总览
  FROM corecraft.sys_config
 WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';


-- ========== 回滚（把 6 条条件再加回去）============
-- UPDATE corecraft.sys_config
--    SET config_value = REPLACE(
--          REPLACE(
--            REPLACE(
--              REPLACE(
--                REPLACE(
--                  REPLACE(
--                    config_value,
--                    '结构例子（**照这个层次来，内容换成主题自己的**）：',
--                    '结构例子（**照这个层次来，内容换成主题自己的**；⚠️ **没给「要知识点」时照这个层次的前三层**——主题 → 模块 → 章节，**不出现知识点、也不出现总览**）：'
--                  ),
--                  '总览知识点：',
--                  '总览知识点（**没给「要知识点」时没有这一层**）：'
--                ),
--                '（PCB / 进程上下文切换 / 时间片轮转）',
--                '（PCB / 进程上下文切换 / 时间片轮转）**（没给「要知识点」时这一层不存在）**'
--              ),
--              CONCAT(CHAR(10), CHAR(10),
--                     '⚠️ **材料里给了「要知识点」时才有叶子那层**；**没给就只到第 3 层**（主题 → 模块 → 章节）—— **不要知识点，也不要总览知识点**。'),
--              ''
--            ),
--            '材料里给了**主题**，可能还有**补充说明**和**现有目录清单**。',
--            '材料里给了**主题**，可能还有**补充说明**、**要知识点**和**现有目录清单**。'
--          ),
--          '知识点。',
--          '知识点（**材料里给了「要知识点」才这样；没给就只到章节层**）。'
--        ),
--        update_time = NOW()
--  WHERE user_id = 0 AND config_key = 'ai.prompt.knowledge.outline';
