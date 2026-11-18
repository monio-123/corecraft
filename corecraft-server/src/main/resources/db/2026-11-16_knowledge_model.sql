-- ============================================================
-- 2026-11-16 知识模型迁移：内置「笔记」目录 + 笔记型归位 + 归类任务清理
--
-- 为什么：
--   ① **建「笔记」目录**（随手记的家，方案 D3）：随手记要有个"家"，
--      而不是落进"待归类"那个债桶（那里的内容其实是删树遗留的孤儿）。
--      ⚠️ **懒建也是同一份逻辑**（后端 KpCategoryRepository#ensureNotesCategory）——
--      这里先建一次，是为了让下面的归位有地方放。
--   ② **笔记型的未归类条目归位**（47 条）：它们是**真笔记**，只是没目录。
--      归位之后「笔记」目录就是干净的（几十条真笔记，而不是几百条孤儿）。
--   ③ **AI 归类的任务软删**（20 条）：归类能力退役（D2）⇒ 这些任务的节点链
--      已无定义（defsOf 认不出 classify ⇒ 空链）⇒ 留着只是一张空壳卡片。
--
-- ⚠️ **为什么只迁「笔记型」不迁「学习型」**：学习型里未归类的 608 条是
--    **删掉的一级目录留下的孤儿**（2026-10-28 删 7 棵树的产物），不是"待归类的学习内容"
--    —— 它们里面大部分是几万字的长文，搬进「笔记」目录会把笔记目录变成垃圾场。
--    ⇒ 留在 null（冷存储），将来要么从备份回放、要么批量清。
--
-- 执行时机：一次性 DML，已在 dev 库执行（2026-11-16）。
--
-- 自检：
--   SELECT id, name, parent_id FROM corecraft.kp_category WHERE name='笔记';
--     -- 期望：1 行，parent_id = 0
--   SELECT COUNT(*) FROM corecraft.kp_topic t JOIN corecraft.kp_category c
--     ON c.name='笔记' AND c.parent_id=0 WHERE t.category_id=c.id;
--     -- 期望：47（迁过去的笔记型）+ 之后新建的随手记
--   SELECT COUNT(*) FROM corecraft.kp_topic WHERE is_delete=0 AND category_id IS NULL;
--     -- 期望：约 608（孤儿，冷存储）
--   SELECT COUNT(*) FROM corecraft.kp_gen_task WHERE type='classify' AND is_delete=0;
--     -- 期望：0
--
-- 回滚：
--   -- ① 笔记目录里的**笔记型**移回未归类（新建的随手记 type_id 为 NULL，不会被误伤）
--   UPDATE corecraft.kp_topic SET category_id = NULL
--   WHERE category_id = (SELECT id FROM corecraft.kp_category WHERE name='笔记' AND parent_id=0)
--     AND type_id = 1;
--   -- ② 删掉笔记目录（⚠️ 先执行 ①）
--   DELETE FROM corecraft.kp_category WHERE name='笔记' AND parent_id=0;
--   -- ③ 归类任务恢复
--   UPDATE corecraft.kp_gen_task SET is_delete = 0 WHERE type='classify';
-- ============================================================

-- ① 建「笔记」目录（幂等：已存在就不建）
INSERT INTO corecraft.kp_category (user_id, name, parent_id)
SELECT 6, '笔记', 0
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM corecraft.kp_category) c
  WHERE c.user_id = 6 AND c.name = '笔记' AND c.parent_id = 0
);

-- ② 笔记型的未归类条目 → 笔记目录（类型轴退役前的最后一班岗）
UPDATE corecraft.kp_topic t
JOIN corecraft.kp_category c ON c.user_id = 6 AND c.name = '笔记' AND c.parent_id = 0
SET t.category_id = c.id
WHERE t.is_delete = 0
  AND t.category_id IS NULL
  AND t.type_id = 1;

-- ③ AI 归类的历史任务：软删（能力退役 ⇒ 节点链已无定义，留着只是空壳卡片）
UPDATE corecraft.kp_gen_task SET is_delete = 1 WHERE type = 'classify';
