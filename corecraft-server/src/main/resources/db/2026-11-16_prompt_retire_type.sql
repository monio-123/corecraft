-- ============================================================
-- 2026-11-16 提示词变更：类型轴退役（删 organize / 改 exam.paper）
--
-- 为什么：
--   ① 删除 `ai.prompt.question.organize:categories,tags`——AI 全树归类退役
--      （D2）。它是「补全这条知识：标题/目录/标签」那条固定配置，
--      只被归类任务使用；归类任务型（classify）同批退役。
--      ⚠️ 删它之前查过引用：没有任何其它配置【引用】它 ⇒ 删了不会让
--      别人的【引用】断链（断了后端会抛错）。
--      ⚠️ `pick_category:categories` **保留**：它是"选目录"的判据，
--      将来「笔记目录整理」任务（只往既有结构归）还要用它。
--   ② `ai.prompt.exam.paper` 去掉【学习】/【笔记】两组分工（D11）：
--      材料不再按类型分组（后端 digestOf 也不再分组），出题规范里
--      还留着"优先用【学习】里的原文"就会指向不存在的东西。
--      改成统一口径：材料是一条条编号的知识，哪些是结论由模型读全文自判。
--
-- 执行时机：一次性 DML，已在 dev 库执行（2026-11-16）。
--           重放新库时 knowledge_platform.sql 已含同样结果，无需再跑。
--
-- 自检：
--   SELECT config_key FROM corecraft.sys_config WHERE config_key LIKE 'ai.prompt.question.organize%';
--     -- 期望：0 行
--   SELECT config_value FROM corecraft.sys_config WHERE config_key='ai.prompt.exam.paper';
--     -- 期望：不含「【学习】」「【笔记】」「材料按类型分组」
--
-- 回滚：
--   INSERT INTO corecraft.sys_config (user_id, config_key, config_value, remark)
--   VALUES (0, 'ai.prompt.question.organize:categories,tags',
--           '【引用】rename:none\n【引用】pick_category:categories\n【引用】pick_tags:tags\n【格式】这次一次给全，严格按下面三段输出，不要写别的解释，也不要写理由、加括号或引号。三段的值分别按上面引用到的规则来：\n名字：\n目录：\n标签：\n- <每行一个标签名>',
--           '由字典 ai_topic_question 迁移');
--   exam.paper 的回滚见 _archive/2026-11-16_prompt_retire_type.sql.bak（改前原文备份）。
-- ============================================================

-- ① 删掉 AI 归类用的引导问题
DELETE FROM corecraft.sys_config WHERE config_key = 'ai.prompt.question.organize:categories,tags';

-- ② 出题规范：去掉类型分组与两组分工（材料已是逐条编号的知识）
UPDATE corecraft.sys_config
SET config_value = REPLACE(
    REPLACE(
        REPLACE(config_value,
            '- 材料按类型分组，每组前面标着这一组的类型名：【学习】是系统整理过的知识点（有定义、原理、应用），【笔记】是随手记的零散内容',
            '- 材料是一条条知识，每条前面标着编号（知识点 1、知识点 2……）；标题带在那条前面'),
        '- 出单选、多选、填空、判断时，正确答案必须能在材料里找到明确依据，不能靠常识补：优先用【学习】里的原文；材料里没有【学习】、只有【笔记】时，就用【笔记】里写明的具体结论，只是零散罗列、没有具体结论的部分不当考点',
        '- 出单选、多选、填空、判断时，正确答案必须能在材料里找到明确依据，不能靠常识补；材料里写明的具体结论才是考点，只是零散罗列、没有具体结论的部分不当考点'),
    '- 出概念题、面试题时，优先用【学习】的框架叙述，讲清结构、边界、模块之间的关系；没有【学习】就用【笔记】的要点',
    '- 出概念题、面试题时，优先用材料里的框架叙述，讲清结构、边界、模块之间的关系')
WHERE config_key = 'ai.prompt.exam.paper';
