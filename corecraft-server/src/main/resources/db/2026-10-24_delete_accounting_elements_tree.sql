-- 物理删除：「会计要素」目录树（28 个目录）+ 下面全部知识点（91 条）
-- 2026-10-24
--
-- 背景：用户要求"**删除会计要素整个目录树以及下面的所有知识点，九十几个。
--       注意是会计要素不是初会**" ⇒ 严格只删「会计要素」这一棵。
--
-- 现在库里并存三棵 AI 生成的树（删前已逐一核对，**别认错**）：
--   操作系统      根 id 525 ｜ 34 个目录 ｜ 109 条知识点（id 317-425）  ← 不动
--   会计要素      根 id 559 ｜ 28 个目录 ｜  91 条知识点（id 426-516）  ← 本脚本删这棵
--   初级会计实务  根 id 587 ｜ 41 个目录 ｜ 117 条知识点（id 517-633）  ← 不动
--   （"初会" = 初级会计实务，**不是**会计要素 —— 用户特意点名要区分）
--
-- 删除范围（**只删会计要素**）：
--   目录：ids 559-586，共 28 个 = 1 根 + 8 模块 + 19 章节，连续无洞、无孤儿
--         559「会计要素」→ 560「1. 会计基础与要素体系」→ 561「1.1 会计基本假设与基础」
--         → 562「1.2 会计要素体系」→ … → 584「8. 要素计量与报表列示」→ 585/586
--   知识点：ids 426-516，共 91 条
--         426「会计要素总览」/ 427「会计基础与要素体系总览」/ 428「会计主体假设」
--         / 429「持续经营假设」/ 430「会计分期假设」/ 431「货币计量假设」
--         / 432「权责发生制与收付实现制」/ 433「资产、负债、所有者权益」
--
-- 安全前提（删前已核）：
--   · id 区间 426-516 里 **91 条全部属于会计要素** ⇒ 没有别的树的知识点混在区间内
--   · 会计要素的知识点**没有跑到区间外**（0 条）⇒ 不会漏删
--   · 区间内 91 条**全是存活行**（界面里看得见的）⇒ 删的正是用户要的那批
--   · **无外部引用**（kp_topic_tag 0 条、kp_topic_relation 0 条）⇒ 不留孤儿数据
--
-- 跑前建议（内容级还原用，跟同目录其它清理脚本一个路子）：
--   mysqldump -uroot -p<pwd> corecraft kp_topic --where="id BETWEEN 426 AND 516" \
--     > <工作区外>/corecraft_pre_kjys_delete_20261024_topic.sql
--   mysqldump -uroot -p<pwd> corecraft kp_category --where="id BETWEEN 559 AND 586" \
--     > <工作区外>/corecraft_pre_kjys_delete_20261024_category.sql
--
-- 执行时机：随时（只动 corecraft 库，不动其它 schema）。


-- ========== ① 跑前自检（应当输出 91 / 91 / 0 / 0 / 28）============
SELECT COUNT(*) AS 区间内知识点 FROM corecraft.kp_topic WHERE id BETWEEN 426 AND 516;
SELECT COUNT(*) AS 其中属于本树 FROM corecraft.kp_topic t
 WHERE t.id BETWEEN 426 AND 516
   AND t.category_id IN (
     SELECT id FROM (
       WITH RECURSIVE tree AS (
         SELECT id FROM corecraft.kp_category WHERE id = 559
         UNION ALL SELECT c.id FROM corecraft.kp_category c JOIN tree t ON c.parent_id = t.id
       ) SELECT id FROM tree
     ) x
   );
SELECT COUNT(*) AS 外部引用_标签 FROM corecraft.kp_topic_tag WHERE topic_id BETWEEN 426 AND 516;
SELECT COUNT(*) AS 外部引用_关联 FROM corecraft.kp_topic_relation
 WHERE topic_id BETWEEN 426 AND 516 OR related_topic_id BETWEEN 426 AND 516;
SELECT COUNT(*) AS 目录数 FROM corecraft.kp_category WHERE id BETWEEN 559 AND 586;


-- ========== ② 物理删除（先知识点，后目录）============
DELETE FROM corecraft.kp_topic WHERE id BETWEEN 426 AND 516;

DELETE FROM corecraft.kp_category WHERE id BETWEEN 559 AND 586;


-- ========== ③ 跑后校验 ==========
SELECT COUNT(*) AS 残留_知识点 FROM corecraft.kp_topic WHERE id BETWEEN 426 AND 516;
SELECT COUNT(*) AS 残留_目录 FROM corecraft.kp_category WHERE id BETWEEN 559 AND 586;
-- 另外两棵树必须原封不动（操作系统 34 目录 / 109 知识点；初级会计实务 41 目录 / 117 知识点）
SELECT COUNT(*) AS 操作系统_目录 FROM corecraft.kp_category
 WHERE id IN (SELECT id FROM (
   WITH RECURSIVE tree AS (
     SELECT id FROM corecraft.kp_category WHERE id = 525
     UNION ALL SELECT c.id FROM corecraft.kp_category c JOIN tree t ON c.parent_id = t.id
   ) SELECT id FROM tree) x);
SELECT COUNT(*) AS 操作系统_知识点 FROM corecraft.kp_topic WHERE category_id IN (
  SELECT id FROM (
    WITH RECURSIVE tree AS (
      SELECT id FROM corecraft.kp_category WHERE id = 525
      UNION ALL SELECT c.id FROM corecraft.kp_category c JOIN tree t ON c.parent_id = t.id
    ) SELECT id FROM tree) x);
SELECT COUNT(*) AS 初会_目录 FROM corecraft.kp_category
 WHERE id IN (SELECT id FROM (
   WITH RECURSIVE tree AS (
     SELECT id FROM corecraft.kp_category WHERE id = 587
     UNION ALL SELECT c.id FROM corecraft.kp_category c JOIN tree t ON c.parent_id = t.id
   ) SELECT id FROM tree) x);
SELECT COUNT(*) AS 初会_知识点 FROM corecraft.kp_topic WHERE category_id IN (
  SELECT id FROM (
    WITH RECURSIVE tree AS (
      SELECT id FROM corecraft.kp_category WHERE id = 587
      UNION ALL SELECT c.id FROM corecraft.kp_category c JOIN tree t ON c.parent_id = t.id
    ) SELECT id FROM tree) x);


-- ========== 回滚 ==========
-- 内容级还原：跑 ① 之前先 mysqldump（命令见上），还原用
--   mysql -uroot -p<pwd> corecraft < <工作区外>/corecraft_pre_kjys_delete_20261024_topic.sql
--   mysql -uroot -p<pwd> corecraft < <工作区外>/corecraft_pre_kjys_delete_20261024_category.sql
-- ⚠️ 目录要先还原（父级 id 559），知识点再还原（category_id 指向目录）。
--
-- ⚠️ **这批内容是 AI 生成的**（用户正要重导）⇒ 脚本不附内容级备份，
--    真需要时按上面的 mysqldump 走。
