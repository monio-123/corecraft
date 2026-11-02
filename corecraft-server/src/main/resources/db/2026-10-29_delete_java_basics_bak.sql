-- ============================================================================
-- 2026-10-29 删除「Java基础bak」（根目录 id 869）
--
-- 【为什么删】
--   2026-10-27 12:51 生成的那棵 Java 树（bak）已被 2026-10-28 16:48 的新树
--   （id 1000「Java基础」）取代 —— 新树是**超集**，且质量明显更好：
--     · 同名知识点平均 3701 字 vs 2716 字（+36%）
--     · 带 mermaid 的 69% vs 5%，带表格的 69% vs 54%，术语解释 81% vs 65%
--     · bak 独有的「10. 常用类库与工具」在新树里是 11.1–11.4；
--       bak 独有的章节里有 19 个是 0 条
--     · 新树独有 10.1–10.4 并发（16 条）+ 11.5 面向面试的高频考点（4 条）
--   两棵树并存 = 同一份知识两套目录 ⇒ 检索、归类、复习都得二选一。
--
-- 【删什么】
--   1. 知识点 **240 条**（id 1175–1387、1542–1568，正文约 1.16 MB）
--      —— **逻辑删**（is_delete 0→1），与应用里 DELETE /kp/topic/{id} 同一套语义
--         （BaseEntity 的 @TableLogic）⇒ 正文还留在库里，回滚只要一条 UPDATE。
--   2. 目录 **87 个**（id 869、870–930、975–999）—— **物理删**
--      （kp_category 刻意不带 is_delete ⇒ 删了就是没了）
--      ⇒ 删除前的原件在 _archive/2026-10-29_java_basics_bak_categories.sql。
--   3. 顺手清掉新树里同类的空占位 **id 1756「Lambda 语法与目标类型」**
--      （同一个缺陷类：生成写到一半、正文空着。上一轮清过 138 条同类的，
--      这条是 10-28 16:48 那次生成留下的，在新树里 ⇒ 单独一段，单独回滚。）
--
-- 【删之前逐条核过（都是 0，所以删了不会留孤儿、不会留死链）】
--   · kp_topic_tag 指向这 240 条的行           = 0
--   · kp_topic_relation 指向这 240 条的行       = 0（两个方向都查了）
--   · 别的知识点正文里指向这 240 条的链接       = 0（/app/topic/<id>）
--   · 别的知识点正文里指向这 87 个目录的链接    = 0（/app/learn?category=<id>）
--   · 这 240 条没有子知识点（parent_topic_id）、没有别的用户的行
--
-- 【这 87 个目录里已软删的 64 条占位（id 1569–1632）】
--   上一轮（2026-10-28）已经清过一次（正文空、只占位置）⇒ 本次不动，
--   它们留在库里是 is_delete=1，回滚也不该复活（那 64 条没有正文）。
--
-- 收口：只动 corecraft 库（106.53.205.69:3306）。
-- 执行：跑前先看「跑前自检」，跑完看「跑后校验」。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 跑前自检
-- ---------------------------------------------------------------------------

-- ① 目录应为 **87** 个（1 根 + 16 模块 + 70 章）
SELECT COUNT(*) AS bak_categories
FROM corecraft.kp_category
WHERE id = 869 OR (id BETWEEN 870 AND 930) OR (id BETWEEN 975 AND 999);

-- ② 挂在下面的知识点：alive **240** / 已软删 **64**（上一轮清的那批）
SELECT t.is_delete, COUNT(*) AS cnt
FROM corecraft.kp_topic t
WHERE t.category_id = 869
   OR (t.category_id BETWEEN 870 AND 930)
   OR (t.category_id BETWEEN 975 AND 999)
GROUP BY t.is_delete;

-- ③ 标签行 / 关系行都应为 **0**（有的话先清关联再删）
SELECT
  (SELECT COUNT(*) FROM corecraft.kp_topic_tag tt
    WHERE tt.topic_id BETWEEN 1175 AND 1387 OR tt.topic_id BETWEEN 1542 AND 1568) AS tag_rows,
  (SELECT COUNT(*) FROM corecraft.kp_topic_relation r
    WHERE (r.topic_id BETWEEN 1175 AND 1387 OR r.topic_id BETWEEN 1542 AND 1568)
       OR (r.related_topic_id BETWEEN 1175 AND 1387 OR r.related_topic_id BETWEEN 1542 AND 1568)) AS relation_rows;

-- ④ 基线：全库可见知识点 **558** → 删完应是 **317**（558 - 240 - 1）
SELECT COUNT(*) AS alive_before FROM corecraft.kp_topic WHERE is_delete = 0;

-- ⑤ 基线：Java基础（id 1000 那棵）**192** 条 → 删完 **191**（少掉 id 1756 那条空占位）
--    集合框架（id 931 那棵）**79** 条 → 删完一条不少
WITH RECURSIVE t1 AS (
  SELECT id FROM corecraft.kp_category WHERE id = 1000
  UNION ALL SELECT c.id FROM corecraft.kp_category c JOIN t1 p ON c.parent_id = p.id
), t2 AS (
  SELECT id FROM corecraft.kp_category WHERE id = 931
  UNION ALL SELECT c.id FROM corecraft.kp_category c JOIN t2 p ON c.parent_id = p.id
)
SELECT
  (SELECT COUNT(*) FROM corecraft.kp_topic WHERE is_delete = 0 AND category_id IN (SELECT id FROM t1)) AS java_basics,
  (SELECT COUNT(*) FROM corecraft.kp_topic WHERE is_delete = 0 AND category_id IN (SELECT id FROM t2)) AS collections;

-- ---------------------------------------------------------------------------
-- 变更
-- ---------------------------------------------------------------------------

-- 步骤 1：知识点 **逻辑删** 240 条
--   范围用「挂在这 87 个目录下」来圈，不用 id 区间 —— id 区间是抄出来的，
--   目录才是这件事的真相（目录里只有它的知识点）。
--   ⚠️ **不动 category_id**：软删后它还指着这些目录，回滚时目录一插回来就归位。
UPDATE corecraft.kp_topic
SET is_delete = 1,
    update_time = NOW()
WHERE is_delete = 0
  AND (category_id = 869
       OR (category_id BETWEEN 870 AND 930)
       OR (category_id BETWEEN 975 AND 999));

-- 步骤 2：目录 **物理删** 87 个（子目录和父目录一起删 —— 库里没有外键约束，
--   应用里 KpCategoryServiceImpl.deleteCategory 也是 deleteBatch 一次删整棵子树）
DELETE FROM corecraft.kp_category
WHERE id = 869 OR (id BETWEEN 870 AND 930) OR (id BETWEEN 975 AND 999);

-- 步骤 3：新树里同类的空占位 1 条（id 1756）
--   ⚠️ 这是**另一个目录**里的东西，单独一段：跟 bak 无关，
--      跟 2026-10-28 那轮清掉的 138 条是同一个缺陷类（正文空、只占位置）。
UPDATE corecraft.kp_topic
SET is_delete = 1,
    update_time = NOW()
WHERE id = 1756
  AND is_delete = 0
  AND (content IS NULL OR content = '');

-- ---------------------------------------------------------------------------
-- 跑后校验
-- ---------------------------------------------------------------------------

-- ① bak 的目录应剩 **0**
SELECT COUNT(*) AS bak_categories_left
FROM corecraft.kp_category
WHERE id = 869 OR (id BETWEEN 870 AND 930) OR (id BETWEEN 975 AND 999);

-- ② bak 子树下的知识点应剩 **0**（alive）
SELECT COUNT(*) AS bak_topics_left
FROM corecraft.kp_topic
WHERE is_delete = 0
  AND (category_id = 869
       OR (category_id BETWEEN 870 AND 930)
       OR (category_id BETWEEN 975 AND 999));

-- ③ 全库可见知识点应为 **317**（558 - 240 - 1）
SELECT COUNT(*) AS alive_after FROM corecraft.kp_topic WHERE is_delete = 0;

-- ④ 别的树一条没少：Java基础 191、集合框架 79
WITH RECURSIVE t1 AS (
  SELECT id FROM corecraft.kp_category WHERE id = 1000
  UNION ALL SELECT c.id FROM corecraft.kp_category c JOIN t1 p ON c.parent_id = p.id
), t2 AS (
  SELECT id FROM corecraft.kp_category WHERE id = 931
  UNION ALL SELECT c.id FROM corecraft.kp_category c JOIN t2 p ON c.parent_id = p.id
)
SELECT
  (SELECT COUNT(*) FROM corecraft.kp_topic WHERE is_delete = 0 AND category_id IN (SELECT id FROM t1)) AS java_basics,
  (SELECT COUNT(*) FROM corecraft.kp_topic WHERE is_delete = 0 AND category_id IN (SELECT id FROM t2)) AS collections;

-- ⑤ **没有孤儿**：可见知识点的 category_id 要么为空，要么指向还存在的目录
--    （目录物理删了、知识点还可见 = 界面上哪儿都查不到）
SELECT COUNT(*) AS orphan_topics
FROM corecraft.kp_topic t
WHERE t.is_delete = 0
  AND t.category_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM corecraft.kp_category c WHERE c.id = t.category_id);

-- ⑥ 可见且没有正文的应只剩 **1 条**（id 82「TCP三次握手」，用户手写的）
SELECT id, title, type_id, category_id
FROM corecraft.kp_topic
WHERE is_delete = 0
  AND (content IS NULL OR content = '');

-- ---------------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------------

-- ① 目录：把 87 行插回来（**id 必须照原样**，知识点软删后还指着它们）
--    数据在 _archive/2026-10-29_java_basics_bak_categories.sql，整段贴过来跑。
--    INSERT INTO corecraft.kp_category
--      (id, user_id, name, parent_id, create_time, update_time)
--    VALUES ...;

-- ② 知识点：恢复 240 条 + id 1756
--    ⚠️ id 范围是**当初删掉的那 240 条**（1175–1387、1542–1568），
--    不是这 87 个目录下的全部 —— 上一轮清掉的 64 条占位（1569–1632）没有正文，
--    不该跟着回来。
-- UPDATE corecraft.kp_topic SET is_delete = 0, update_time = NOW()
-- WHERE is_delete = 1 AND id BETWEEN 1175 AND 1387;
-- UPDATE corecraft.kp_topic SET is_delete = 0, update_time = NOW()
-- WHERE is_delete = 1 AND id BETWEEN 1542 AND 1568;
-- UPDATE corecraft.kp_topic SET is_delete = 0, update_time = NOW()
-- WHERE id = 1756;
