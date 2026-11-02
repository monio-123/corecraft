-- ============================================================================
-- 2026-10-29 清掉「整棵子树都没有知识点」的目录（71 个）
--
-- 【为什么删】
--   上一轮删掉「Java基础bak」之后，库里剩下一批**一条知识点都没有**的目录：
--   早期手工骨架（python / 数据结构 / 面向对象 / 新特性 / JVM / 多线程 /
--   Set / Map / Queue / Iterator）、「计算机网络」整棵树、层数测试树
--   （1. 一级模块 … 叶子知识点）、「JAVA 集合框架」那次生成留下的空章节（5.3-8.5）。
--   它们在侧边栏里点进去永远是空的 —— **看着像有结构，其实什么也没有**。
--
-- 【删的范围怎么定的（一条规则）】
--   **「整棵子树里一条可见知识点都没有」的目录**，全删。
--   ⚠️ 为什么不是「只删空叶子」：删掉叶子之后它的父目录也空了，
--      留着它就是又一层"看着有、其实空"的目录 ⇒ 一次删干净。
--   ⇒ 这次是 **71** 个（不是上一轮说的 54 —— 54 只算了空叶子，
--      多的 17 个是子目录删掉后自己也变空的父目录）。
--
-- 【删之前核过】
--   · 挂在这些目录下的**可见知识点 = 0**（这 71 个目录里一条内容都没有）
--   · 挂在这些目录下的 71 条知识点**本来就是软删状态**（上一轮清的正文空占位）
--     ⇒ 正文还在库里，不用导出
--   · 子知识点引用（parent_topic_id）= 0
--   · **正文里链到这些目录的链接 = 2 处**，都在 id 88「JAVA集合框架」里
--     （链到空的 Set(59) / Queue(61)）⇒ **第一步先把这两处退成普通文字**，
--     不然删完就是死链。
--
-- 【那两处链接怎么处理】
--   按系统自己的规矩来（`fixCategoryLinks`：**认不出来的目录链接退成普通文字**）：
--     `**[Set：](/app/learn?category=59)**`   → `**Set：**`
--     `**[Queue：](/app/learn?category=61)**` → `**Queue：**`
--   （同一条正文里的 List 链接指向 58「List」—— 那个目录有内容，不动。）
--
-- 【回滚】
--   目录是**物理删**（`kp_category` 刻意不带 `is_delete`）⇒ 不可逆，
--   删除前的原件在 _archive/2026-10-29_empty_category_trees.sql（71 行，id 照原样）。
--
-- 收口：只动 corecraft 库（106.53.205.69:3306）。
-- 执行：跑前先看「跑前自检」，跑完看「跑后校验」。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 跑前自检
-- ---------------------------------------------------------------------------

-- ① 待删的目录应为 **71** 个（"整棵子树没有可见知识点"的那批）
WITH RECURSIVE marked AS (
  SELECT DISTINCT category_id AS id FROM corecraft.kp_topic WHERE is_delete = 0 AND category_id IS NOT NULL
  UNION
  SELECT c.parent_id FROM corecraft.kp_category c JOIN marked m ON c.id = m.id
  WHERE c.parent_id IS NOT NULL AND c.parent_id <> 0
)
SELECT COUNT(*) AS to_delete
FROM corecraft.kp_category c LEFT JOIN marked m ON m.id = c.id
WHERE m.id IS NULL;

-- ② 这些目录里**可见知识点应为 0**、软删的 71 条（正文空占位）
WITH RECURSIVE marked AS (
  SELECT DISTINCT category_id AS id FROM corecraft.kp_topic WHERE is_delete = 0 AND category_id IS NOT NULL
  UNION
  SELECT c.parent_id FROM corecraft.kp_category c JOIN marked m ON c.id = m.id
  WHERE c.parent_id IS NOT NULL AND c.parent_id <> 0
)
SELECT
  (SELECT COUNT(*) FROM corecraft.kp_topic t JOIN corecraft.kp_category c ON c.id = t.category_id
     LEFT JOIN marked m ON m.id = c.id WHERE m.id IS NULL AND t.is_delete = 0) AS alive_in_them,
  (SELECT COUNT(*) FROM corecraft.kp_topic t JOIN corecraft.kp_category c ON c.id = t.category_id
     LEFT JOIN marked m ON m.id = c.id WHERE m.id IS NULL AND t.is_delete = 1) AS soft_deleted_in_them;

-- ③ 链到它们的正文链接应为 **2**（都在 id 88）
SELECT id, title FROM corecraft.kp_topic
WHERE is_delete = 0
  AND (content LIKE '%/app/learn?category=59)%' OR content LIKE '%/app/learn?category=61)%');

-- ④ 基线：目录 **181** 个、可见知识点 **317** 条
SELECT
  (SELECT COUNT(*) FROM corecraft.kp_category) AS cats_before,
  (SELECT COUNT(*) FROM corecraft.kp_topic WHERE is_delete = 0) AS topics_before;

-- ---------------------------------------------------------------------------
-- 变更
-- ---------------------------------------------------------------------------

-- 步骤 1：两处即将失效的目录链接**退成普通文字**（先做这步，再删目录）
UPDATE corecraft.kp_topic
SET content = REPLACE(REPLACE(content,
        '[Set：](/app/learn?category=59)', 'Set：'),
        '[Queue：](/app/learn?category=61)', 'Queue：'),
    update_time = NOW()
WHERE id = 88
  AND is_delete = 0
  AND content LIKE '%/app/learn?category=59)%'
  AND content LIKE '%/app/learn?category=61)%';

-- 步骤 2：**物理删** 71 个目录
--   （库里没有外键约束，子父一起删没问题；应用里 KpCategoryServiceImpl.deleteCategory
--     也是 deleteBatch 一次删整棵子树。）
DELETE FROM corecraft.kp_category
WHERE id IN (
  49, 50, 54, 55, 56, 57, 59, 60, 61, 62,
  107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122,
  123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138,
  139, 140,
  862, 863, 864, 865, 866, 867, 868,
  955, 956, 957, 958, 959, 960, 961, 962, 963, 964, 965, 966, 967, 968, 969,
  970, 971, 972, 973, 974
);

-- ---------------------------------------------------------------------------
-- 跑后校验
-- ---------------------------------------------------------------------------

-- ① 目录应为 **110** 个（181 − 71）
SELECT COUNT(*) AS cats_after FROM corecraft.kp_category;

-- ② **不该再有"整棵子树都是空的"目录**（这条是最强的一条）
WITH RECURSIVE marked AS (
  SELECT DISTINCT category_id AS id FROM corecraft.kp_topic WHERE is_delete = 0 AND category_id IS NOT NULL
  UNION
  SELECT c.parent_id FROM corecraft.kp_category c JOIN marked m ON c.id = m.id
  WHERE c.parent_id IS NOT NULL AND c.parent_id <> 0
)
SELECT COUNT(*) AS empty_trees_left
FROM corecraft.kp_category c LEFT JOIN marked m ON m.id = c.id
WHERE m.id IS NULL;

-- ③ 可见知识点应仍是 **317**（一条没少）
SELECT COUNT(*) AS topics_after FROM corecraft.kp_topic WHERE is_delete = 0;

-- ④ **没有孤儿**：可见知识点的 category_id 要么空、要么指向还存在的目录
SELECT COUNT(*) AS orphan_topics
FROM corecraft.kp_topic t
WHERE t.is_delete = 0
  AND t.category_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM corecraft.kp_category c WHERE c.id = t.category_id);

-- ⑤ **没有死链**：正文里链到的目录 id 都得还存在
--    ⚠️ 判据是「这条正文里**没有任何一个**链接指向存在的目录」——
--       一条正文可以有好几个链接，只要有一个还指向存在的目录，
--       上面那条 NOT EXISTS 就判不出来 ⇒ 这里直接列出**所有**链接目标，逐个核。
SELECT DISTINCT
  t.id AS topic_id,
  t.title,
  CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(
        SUBSTRING_INDEX(t.content, '/app/learn?category=', -1), ')', 1), '/', -1) AS UNSIGNED) AS linked_cat_id
FROM corecraft.kp_topic t
WHERE t.is_delete = 0
  AND t.content LIKE '%/app/learn?category=%'
ORDER BY t.id, linked_cat_id;

-- 真正"死"的那几条（上面列出来的 id 在 kp_category 里查不到）应为 **0 行**：
-- SELECT DISTINCT ... （同上）  LEFT JOIN corecraft.kp_category c ON c.id = linked_cat_id
-- WHERE c.id IS NULL;
-- 实跑结果：上面那条查出来是 id 88 → 58（58「List」有内容、还在）⇒ **死链 0**。

-- ⑥ 剩下的顶层树（应该只剩还有内容的那些）
SELECT id, name FROM corecraft.kp_category WHERE parent_id = 0 ORDER BY id;

-- ---------------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------------

-- ① 目录：把 71 行插回来（**id 必须照原样**）
--    数据在 _archive/2026-10-29_empty_category_trees.sql，整段贴过来跑。
--    INSERT INTO corecraft.kp_category
--      (id, user_id, name, parent_id, create_time, update_time)
--    VALUES ...;

-- ② 那两处链接改回原来的样子
-- UPDATE corecraft.kp_topic
-- SET content = REPLACE(REPLACE(content,
--         'Set：', '[Set：](/app/learn?category=59)'),
--         'Queue：', '[Queue：](/app/learn?category=61)'),
--     update_time = NOW()
-- WHERE id = 88 AND is_delete = 0;
