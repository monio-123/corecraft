-- 删除两棵 AI 生成的测试树：操作系统（525）+ 初级会计实务（587）—— 目录与知识点全清
-- 2026-10-24
--
-- 背景：用户要按优化后的提示词重新生成 ⇒ 这两棵当测试数据，整体删掉重来。
--   授权原话："顺便把初级会计实务和操作系统中的所有内容（包块知识点）都先删除 我要重新生成"
--
-- 范围（跑前已核，逐项查过）：
--   · 操作系统 525：34 个目录（1 主题 + 8 章 + 25 节）、**109** 条知识点
--   · 初级会计实务 587：41 个目录（1 主题 + 8 章 + 32 节）、**117** 条知识点
--   · 合计 **75 个目录 + 226 条知识点**
--   · 知识点**全部是存活行**（is_delete=0，软删 0 条）⇒ 界面里看得见的全在这 226 条里
--   · 外部引用 **0**（kp_topic_tag 命中 0 条、kp_topic_relation 全库 0 条）⇒ 删完不留孤儿
--   · 全库本来就无孤儿（"目录对不上的知识点" 0 条、"父级对不上的目录" 0 条）
--   · **全程按 id 收口，不按名字匹配**：用户自己早年的 54 条软删笔记（ids 2-99）不在任何一棵
--     树里（它们的 category_id 指向 0/别的目录），全程不碰
--
-- 做法：**先删知识点，再自底向上删目录**（节 → 章 → 主题）。
--   `kp_category` 没有软删列 ⇒ 目录只能物理删；而 `DELETE /kp/category/{id}` 后端是
--   "整棵子树一起硬删"，这里用 SQL 直接按层级删，便于逐层核对。
--
-- ⚠️ **物理 DELETE 不可逆**：内容级还原靠跑前的 mysqldump 备份（下面给了命令）。
--
-- 跑前建议（内容级还原）：
--   mysqldump -uroot -p<pwd> corecraft kp_topic kp_category \
--     --where="id BETWEEN 0" > <工作区外>/corecraft_pre_two_trees_20261024.sql
--   （更稳的做法：整表 dump，不带 where）
--
-- 执行时机：随时（只动 corecraft 库）。


-- ========== ① 跑前自检（应当输出两行：34/109 与 41/117）============
WITH RECURSIVE t AS (
  SELECT id, id AS root FROM corecraft.kp_category WHERE id IN (525, 587)
  UNION ALL
  SELECT c.id, t.root FROM corecraft.kp_category c JOIN t ON c.parent_id = t.id
)
SELECT t.root AS 主题, COUNT(DISTINCT t.id) AS 目录数, COUNT(DISTINCT k.id) AS 知识点
FROM t LEFT JOIN corecraft.kp_topic k ON k.category_id = t.id
GROUP BY t.root;

-- 跑前必须为 0 的一行（有引用就不能删，删了留孤儿）
WITH RECURSIVE t AS (
  SELECT id FROM corecraft.kp_category WHERE id IN (525, 587)
  UNION ALL
  SELECT c.id FROM corecraft.kp_category c JOIN t ON c.parent_id = t.id
)
SELECT COUNT(*) AS 两棵树里的标签关联
FROM corecraft.kp_topic_tag tt
WHERE tt.topic_id IN (SELECT k.id FROM corecraft.kp_topic k WHERE k.category_id IN (SELECT id FROM t));


-- ========== ② 删知识点（226 条，含软删行的也在内）============
WITH RECURSIVE t AS (
  SELECT id FROM corecraft.kp_category WHERE id IN (525, 587)
  UNION ALL
  SELECT c.id FROM corecraft.kp_category c JOIN t ON c.parent_id = t.id
)
DELETE FROM corecraft.kp_topic WHERE category_id IN (SELECT id FROM t);


-- ========== ③ 删目录：先节（57 个）→ 再章（16 个）→ 最后主题（2 个）============
-- 节
DELETE FROM corecraft.kp_category WHERE id IN (
  -- 操作系统
  527,528,529,531,532,533,534,536,537,538,540,541,542,544,545,546,
  548,549,550,552,553,554,556,557,558,
  -- 初级会计实务
  589,590,591,592,594,595,596,597,599,600,601,602,604,605,606,607,
  609,610,611,612,614,615,616,617,619,620,621,622,623,625,626,627
);

-- 章
DELETE FROM corecraft.kp_category WHERE id IN (
  526,530,535,539,543,547,551,555,
  588,593,598,603,608,613,618,624
);

-- 主题
DELETE FROM corecraft.kp_category WHERE id IN (525, 587);


-- ========== ④ 跑后校验 ==========
-- ① 这两棵树残留：目录 0、知识点 0
SELECT COUNT(*) AS 残留目录 FROM corecraft.kp_category WHERE id IN (525, 587)
  OR id IN (526,527,528,529,530,531,532,533,534,535,536,537,538,539,540,541,542,543,544,545,546,547,548,549,550,551,552,553,554,555,556,557,558,
            588,589,590,591,592,593,594,595,596,597,598,599,600,601,602,603,604,605,606,607,608,609,610,611,612,613,614,615,616,617,618,619,620,621,622,623,624,625,626,627);
SELECT COUNT(*) AS 残留知识点 FROM corecraft.kp_topic WHERE category_id IN (
  525,526,527,528,529,530,531,532,533,534,535,536,537,538,539,540,541,542,543,544,545,546,547,548,549,550,551,552,553,554,555,556,557,558,
  587,588,589,590,591,592,593,594,595,596,597,598,599,600,601,602,603,604,605,606,607,608,609,610,611,612,613,614,615,616,617,618,619,620,621,622,623,624,625,626,627);

-- ② 全库剩下的（应当是用户自己的笔记 + 那 54 条软删）
SELECT COUNT(*) AS 目录_全库 FROM corecraft.kp_category;
SELECT COUNT(*) AS 知识点_全库, SUM(is_delete = 0) AS 可见, SUM(is_delete = 1) AS 软删 FROM corecraft.kp_topic;

-- ③ 孤儿检查（两条都应为 0）
SELECT COUNT(*) AS 目录对不上的知识点 FROM corecraft.kp_topic
 WHERE category_id NOT IN (SELECT id FROM corecraft.kp_category) AND category_id <> 0;
SELECT COUNT(*) AS 父级对不上的目录 FROM corecraft.kp_category
 WHERE parent_id <> 0 AND parent_id NOT IN (SELECT id FROM corecraft.kp_category);


-- ========== 回滚 ==========
-- **物理 DELETE 不可逆**，内容级还原靠跑前的 mysqldump 备份：
--   mysql -uroot -p<pwd> corecraft < <工作区外>/corecraft_pre_two_trees_20261024.sql
-- （备份是整表 dump，还原后 id 会原样回来；只导 kp_topic + kp_category 两张表。）
-- 目录与知识点的**父子关系**也在备份里，还原后不用重建。
