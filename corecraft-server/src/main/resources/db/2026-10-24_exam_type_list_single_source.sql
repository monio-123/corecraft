-- ============================================================
-- 2026-10-24 考试：题型清单收敛到一处 + 修一个配置树孤儿节点
--
-- ## 为什么改
--
-- **题型清单写了三处**，而只有一处是权威：
--   ① 代码 `examParse.js` 的 `EXAM_TYPES`（权威，题型是**契约**不能配）
--   ② 提示词 `ai.prompt.exam.paper` 正文里的枚举「单选 / 多选 / 填空 / 概念 / 面试」
--   ③ 配置树节点备注里的同一份枚举（`ai.prompt.exam.types` / `ai.prompt.exam.paper`）
--
-- ②③ 各改各的 ⇒ 管理员在提示词里加一种题型、并同步加进 `ai.prompt.exam.types`，
-- 前端 `typeKeyOf` 认不出 ⇒ 那一行被计入 `bad` 忽略 ⇒ 考试少一种题型，
-- 界面只提示一句"配置里有 N 行题型没认出来，已忽略"。
--
-- **这次把 ②③ 里那份"清单"去掉**，题型清单只留在代码里。
-- 提示词改成"括号里写中文题型（就用题量结构里那些词）"——
-- 题量结构本来就是前端从 `EXAM_TYPES` 生成的（「第 1-3 题：单选」），模型照着写即可。
--
-- ⚠️ **没动的是"每种题型的答案写法"那几行**（选项怎么写、答案怎么写、要点怎么写）：
--   那是**契约**（解析器认的东西），按 skill §3.3「不能拆的是契约」，
--   理想状态是也挪进代码、由前端拼进 prompt。本轮只清"清单"，那几行留着。
--
-- ## 顺带修一个孤儿节点
--
-- `sys_config_node` id=50（`ai.prompt.exam.types`）的 `parent_id` 是 **0**，
-- 而它的兄弟（paper / grade / level / extra）都在 id=46「考试」下面。
-- 它自己是**配置项**（value_type='text'）⇒ 配置页的分类树只收分类，
-- 它又不在任何分类下 ⇒ **管理员在「系统配置 → 考试」里根本看不到「题型与道数」这一项**。
-- 改成 parent_id=46（sort 本来就是 3，插在 paper/grade 和 level/extra 中间，正好）。
--
-- ============================================================

-- ------------------------------------------------------------
-- 1. 提示词：去掉题型枚举
-- ------------------------------------------------------------
-- 只替换那一行里的枚举那半句，提示词其余部分（通用规则、场景特调）一个字不动。
-- REPLACE 找不到就什么都不改（不会把整条提示词写坏）。

UPDATE `sys_config`
SET `config_value` = REPLACE(
      `config_value`,
      '括号里写中文题型：单选 / 多选 / 填空 / 概念 / 面试',
      '括号里写中文题型（就用题量结构里那些词）'
    )
WHERE `user_id` = 0
  AND `config_key` = 'ai.prompt.exam.paper'
  AND `config_value` LIKE '%括号里写中文题型：单选 / 多选 / 填空 / 概念 / 面试%';

-- ------------------------------------------------------------
-- 2. 配置树备注：去掉第二份题型枚举 + 修过期的说明
-- ------------------------------------------------------------

-- 2.1 出题规范：原来写着"25 道题、五种题型各 5 道"（早就不是了，现在是可调的）
--     和"改了它要同步看 examParse.js"（那句正是在提醒两份的存在）。
UPDATE `sys_config_node`
SET `remark` = '出卷时整份发给模型：题量结构拼在前面，难度和考生附加要求拼在后面，这里只写通用规则。**题型清单只有一处（examParse.js 的 EXAM_TYPES），这里不要再写一遍题型枚举**'
WHERE `id` = 47
  AND `config_key` = 'ai.prompt.exam.paper';

-- 2.2 题型与道数：去掉题型枚举；顺便把格式说明改对
--     （原来写的是「题型|道数」两段，实际早就是「题型|道数|分值」三段了）
UPDATE `sys_config_node`
SET `remark` = '出卷的题量结构：每行「题型|道数|分值」，分值可省（省了按 1 分/题）。**题型名要用解析器认的那几个**（examParse.js 的 EXAM_TYPES），写别的会被忽略。考试页可以改，默认取这里'
WHERE `id` = 50
  AND `config_key` = 'ai.prompt.exam.types';

-- 2.3 孤儿节点归位：挂到「考试」分类下面（它自己是配置项，不进分类树，
--     挂在 0 上面就等于在管理后台里消失了）
UPDATE `sys_config_node`
SET `parent_id` = (
      SELECT p.id FROM (
        SELECT id FROM `sys_config_node` WHERE config_key = 'ai.prompt.exam'
      ) p
    )
WHERE `id` = 50
  AND `config_key` = 'ai.prompt.exam.types'
  AND `parent_id` = 0;


-- ============================================================
-- 回滚
-- ============================================================

-- R1. 提示词枚举还原
UPDATE `sys_config`
SET `config_value` = REPLACE(
      `config_value`,
      '括号里写中文题型（就用题量结构里那些词）',
      '括号里写中文题型：单选 / 多选 / 填空 / 概念 / 面试'
    )
WHERE `user_id` = 0
  AND `config_key` = 'ai.prompt.exam.paper'
  AND `config_value` LIKE '%括号里写中文题型（就用题量结构里那些词）%';

-- R2. 配置树备注还原
UPDATE `sys_config_node`
SET `remark` = '出卷时整份发给模型：25 道题、五种题型各 5 道。改了它要同步看 examParse.js'
WHERE `id` = 47
  AND `config_key` = 'ai.prompt.exam.paper';

UPDATE `sys_config_node`
SET `remark` = '出卷的题量结构：每行「题型|道数」，题型只能是单选/多选/填空/概念/面试。考试页可以改，默认取这里'
WHERE `id` = 50
  AND `config_key` = 'ai.prompt.exam.types';

-- R3. 孤儿节点放回根级
UPDATE `sys_config_node`
SET `parent_id` = 0
WHERE `id` = 50
  AND `config_key` = 'ai.prompt.exam.types';
