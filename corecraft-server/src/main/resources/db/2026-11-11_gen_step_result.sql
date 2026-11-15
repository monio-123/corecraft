-- ============================================================================
-- kp_gen_step 加「结果」列 —— AI 归类异步化的落点
--
-- 2026-11-11
--
-- 为什么需要：
--   任务步骤（kp_gen_step）原来只存**输入**（material 材料、prompt_snapshot 提示词快照）
--   和**计量**（token / 耗时 / finish_reason），**没有存"模型答了什么"**。
--   那是因为唯一在跑的任务类型是 `content`（写正文）—— 它的产物直接落进 kp_topic，
--   步骤表不需要留答案。
--
--   AI 归类不一样：它的产物是**建议**（归到哪个目录 / 起什么名字），
--   而"目录归属"是**需要人工确认的主观判断** ⇒ 建议不能自动写库，
--   得先存起来等人采纳。而这张表是唯一现成的"每次调用一条"的落点。
--
-- 存的是什么：
--   一段 JSON 文本，形如 {"answer":"名字：xxx\n目录：数据库/MySQL\n标签：\n- 分页"}
--   **存原始回答，不存解析后的结构** —— 解析规则（"名字：/目录：/标签："这三段怎么读）
--   在前端（aiAdopt.js）只有一份，解析结果依赖**当下的目录清单**：
--   同一个"目录：数据库/MySQL"，今天这个目录存在、明天被删了，解析结果就不一样。
--   ⇒ 存原始文本 + 采纳时现解析，才是"采纳时按当下目录清单重新校验"的前提。
--
-- 为什么不用 task 表的 result 列：
--   kp_gen_task.result 是**任务级**的摘要（整批一个字符串）。AI 归类是逐条给建议、
--   逐条采纳，一条一个结果 ⇒ 必须落在**步骤**这一层。
--
-- ⚠️ 存量行不用回填：老步骤都是 content 类型，产物在 kp_topic 里，result 留 NULL 即可。
-- ============================================================================

ALTER TABLE kp_gen_step
    ADD COLUMN `result` text COLLATE utf8mb4_unicode_ci COMMENT '步骤产物（AI 归类：模型的原始回答 JSON；content 类型留空）' AFTER `material`;

-- 回查
SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'corecraft' AND TABLE_NAME = 'kp_gen_step' AND COLUMN_NAME = 'result';
