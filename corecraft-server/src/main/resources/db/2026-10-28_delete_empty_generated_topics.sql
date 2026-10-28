-- ============================================================================
-- 2026-10-28 清理：把「生成知识点没写完正文」留下的空占位删掉（138 条）
--
-- 目的：只留真正写了正文的知识点 —— 骨架占位（title 有、content 空）在树上
--       跟"没写完"的长得一样，看不出是缺内容还是坏了。
--
-- 背景：2026-10-28 两次生成（14:08 的 91 条、14:57 的 153 条）因**前端缓存倒退**
--       （utils/lazyLoad.js 的 loadOnce 让晚回来的旧请求覆盖了新结果），
--       生成范围只算进一部分 ⇒ 其余占位知识点**一次调用都没发出去**，一直空着。
--
-- 影响：corecraft.kp_topic 138 行（is_delete 0 → 1）。
--       不动目录、不动标签、不动知识点关联（这 138 条本来就没有标签行和关系行）。
--
-- ⚠️ **排除 id 82「TCP三次握手」**：用户 10-16 手写的（type_id=1 笔记档、
--    category_id=NULL、type 与生成物不同）⇒ 它"没有正文"是另一回事，不在本次范围。
--
-- 收口：只动 corecraft 库（106.53.205.69:3306）。
-- 执行：mysql 客户端跑本文件；跑前先看「跑前自检」，跑后看「跑后校验」。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 跑前自检
-- ---------------------------------------------------------------------------

-- ① 目标行数应为 **138**（不对就先停下核对）
SELECT COUNT(*) AS target_rows
FROM corecraft.kp_topic
WHERE is_delete = 0
  AND id BETWEEN 1468 AND 1632
  AND (content IS NULL OR content = '');

-- ② 这行应为 **1**：手写的 id 82「TCP三次握手」也是空正文，
--    但它**不在目标范围内**（目标按 id 区间 1468–1632 圈定，82 < 1468 ⇒ 碰不到它）
SELECT COUNT(*) AS hand_written_empty_rows
FROM corecraft.kp_topic
WHERE is_delete = 0
  AND id = 82
  AND (content IS NULL OR content = '');

-- ③ 这 138 条不该有标签行 / 关系行（有的话先清关联再删）
SELECT
  (SELECT COUNT(*) FROM corecraft.kp_topic_tag
    WHERE topic_id BETWEEN 1468 AND 1632) AS tag_rows,
  (SELECT COUNT(*) FROM corecraft.kp_topic_relation
    WHERE topic_id BETWEEN 1468 AND 1632
       OR related_topic_id BETWEEN 1468 AND 1632) AS relation_rows;

-- ---------------------------------------------------------------------------
-- 变更（**逻辑删**：与应用里 DELETE /kp/topic/{id} 同一套语义 —— @TableLogic）
-- ---------------------------------------------------------------------------
UPDATE corecraft.kp_topic
SET is_delete = 1,
    update_time = NOW()
WHERE is_delete = 0
  AND id BETWEEN 1468 AND 1632
  AND (content IS NULL OR content = '');

-- ---------------------------------------------------------------------------
-- 跑后校验
-- ---------------------------------------------------------------------------

-- ① 目标行数应为 **0**
SELECT COUNT(*) AS target_rows_left
FROM corecraft.kp_topic
WHERE is_delete = 0
  AND id BETWEEN 1468 AND 1632
  AND (content IS NULL OR content = '');

-- ② 全库"可见且没有正文"的应只剩 **1 条**（id 82，手写那条）
SELECT id, title, type_id, category_id, create_time
FROM corecraft.kp_topic
WHERE is_delete = 0
  AND (content IS NULL OR content = '');

-- ③ 真正写了正文的知识点一条没少（生成批次里 content 非空的行数应与清理前一致）
SELECT COUNT(*) AS written_rows
FROM corecraft.kp_topic
WHERE is_delete = 0
  AND content IS NOT NULL
  AND content <> '';

-- ---------------------------------------------------------------------------
-- 回滚
--
-- ⚠️ 只回滚**今天这批生成占位**（用 create_time 窗口卡住），别把别的误恢复。
-- ---------------------------------------------------------------------------
-- UPDATE corecraft.kp_topic
-- SET is_delete = 0,
--     update_time = NOW()
-- WHERE is_delete = 1
--   AND id BETWEEN 1468 AND 1632
--   AND (content IS NULL OR content = '')
--   AND create_time >= '2026-10-28 14:00:00'
--   AND create_time <  '2026-10-28 15:00:00';
