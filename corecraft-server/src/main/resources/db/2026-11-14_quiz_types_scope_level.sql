-- =====================================================================
-- 2026-11-14 随堂测验：题量/范围/难度配置重构 + 判分规范多选部分对
--
-- 背景（TODO-exam-lifecycle 后续，用户 2026-11-14 拍板）：
--   随堂 quiz.types 原为 单选3/多选3/概念1/面试1（上限 8、满分 24），对
--   「一条知识」偏重：考点立不住 8 个（一个考点一题 + 题型错开）⇒ 实际
--   只能出 4~6 道；面试题单条撑不起；多选 3 道质量最易塌；62.5% 分值压在
--   两道 AI 主观判分题上；缺判断题（quiz.scope 点名的「常见误解」没题型接）。
--
-- 改动（全部幂等，可重复执行）：
--   1. quiz.types  → 单选2/多选1/判断2/概念1（上限 6、满分 11），删面试
--   2. quiz.scope  → 补「核心考点 3~5 个、不逐段拆正文」+「常见误解优先判断题」
--   3. 新增 quiz.level（系统默认 = 基础档）——随堂是学完立刻的自检，不再蹭
--      目录考试的「进阶」默认档（ai.prompt.exam.level 第一行）；配置树
--      sys_config_node 同步挂节点（管理员可改）
--   4. grade 多选判定：少选（没选错误选项）判部分对——原「少选也是错」让
--      三档判定的「部分对」在多选上永远空转；少选是「知道一半」的真实信号
--
-- 回滚段在文件尾部。
-- =====================================================================

-- ---------- 1. 题量结构 ----------
UPDATE corecraft.sys_config
SET config_value = '单选|2|1\n多选|1|2\n判断|2|1\n概念|1|5'
WHERE config_key = 'ai.prompt.exam.quiz.types' AND user_id = 0;

-- ---------- 2. 出题范围（补随堂专属取点规则） ----------
UPDATE corecraft.sys_config
SET config_value = '范围是当前这条知识。\n\n考点从这条知识自己立得住的地方取：定义、原理、用法、边界、常见误解。\n\n随堂是学完立刻的自检：考点只取这条知识真正立得住的核心点（3 到 5 个），不要把正文逐段拆成考点。\n「常见误解」优先用判断题考。'
WHERE config_key = 'ai.prompt.exam.quiz.scope' AND user_id = 0;

-- ---------- 3. 新增 quiz.level（系统默认 + 配置树节点） ----------
INSERT INTO corecraft.sys_config (config_key, config_value, user_id, remark)
SELECT 'ai.prompt.exam.quiz.level', '基础|只考定义和直接结论，考的是材料里写明的东西', 0,
       '随堂测验的难度档（与目录考试解耦：学完立刻的自检以基础为主）。行格式同 ai.prompt.exam.level'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM corecraft.sys_config WHERE config_key = 'ai.prompt.exam.quiz.level' AND user_id = 0);

INSERT INTO corecraft.sys_config_node (parent_id, name, code, config_key, value_type, sort, remark)
SELECT 66, '难度档位', 'level', 'ai.prompt.exam.quiz.level', 'text', 3,
       '随堂测验的难度档，行格式「档位名|这个档位考什么」（只认第一行）。留空 = 跟随 ai.prompt.exam.level 的默认档'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM corecraft.sys_config_node WHERE config_key = 'ai.prompt.exam.quiz.level');

-- ---------- 4. 判分规范：多选少选判部分对 ----------
UPDATE corecraft.sys_config
SET config_value = REPLACE(
    config_value,
    '- 多选：选的字母与标准答案完全一致才是对；少选、多选、错选都是错',
    '- 多选：与标准答案完全一致就是对；少选（没选错误选项、但没选全）判部分对，讲解里写清漏了哪个；多选了、选了错误选项都是错')
WHERE config_key = 'ai.prompt.exam.grade' AND user_id = 0
  AND config_value LIKE '%少选、多选、错选都是错%';

-- =====================================================================
-- 回滚段（出问题时手工执行；quiz.level 是新键，回滚直接删）：
--
-- UPDATE corecraft.sys_config SET config_value = '单选|3|1\n多选|3|2\n概念|1|5\n面试|1|10'
--   WHERE config_key = 'ai.prompt.exam.quiz.types' AND user_id = 0;
-- UPDATE corecraft.sys_config SET config_value = '范围是当前这条知识。\n\n考点从这条知识自己立得住的地方取：定义、原理、用法、边界、常见误解。'
--   WHERE config_key = 'ai.prompt.exam.quiz.scope' AND user_id = 0;
-- UPDATE corecraft.sys_config SET config_value = REPLACE(config_value,
--   '- 多选：与标准答案完全一致就是对；少选（没选错误选项、但没选全）判部分对，讲解里写清漏了哪个；多选了、选了错误选项都是错',
--   '- 多选：选的字母与标准答案完全一致才是对；少选、多选、错选都是错')
--   WHERE config_key = 'ai.prompt.exam.grade' AND user_id = 0;
-- DELETE FROM corecraft.sys_config WHERE config_key = 'ai.prompt.exam.quiz.level' AND user_id = 0;
-- DELETE FROM corecraft.sys_config_node WHERE config_key = 'ai.prompt.exam.quiz.level';
-- =====================================================================
