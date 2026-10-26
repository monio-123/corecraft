-- 批量改名：卡片正文第五段小标题「5. 面试问答」→「5. 常见考点」（226 条）
-- 2026-10-24
--
-- 背景：用户定了"**不要叫 面试问答 就叫常见考点之类的就行了**"。
--   · **新生成的卡片**：提示词 `ai.prompt.knowledge.point` 已改（v4.4，见
--     `2026-10-24_knowledge_pack_v4_book_style.sql`）⇒ 不用发版，配置即时生效。
--   · **已存在的卡片不会自动改名** ⇒ 本脚本把存量那批的第五段小标题一并换掉。
--
-- 范围（跑前已核）：
--   · 命中 `content LIKE '%### 5. 面试问答%'` 的**恰好 226 条**
--     （= 操作系统 109 + 初级会计实务 117），涉及 74 个目录
--   · 库里"面试问答"**只出现在这一种小标题形式**（含"面试问答"的行数与上面完全相同，
--     别的位置没有）⇒ 一次 REPLACE 就够，不用处理别的变体
--   · 全部是存活行（is_delete=0）
--
-- 做法：**只换小标题那一行，正文一个字不动** ——
--   「常见考点」下面用问答形式列要点是合理的，那部分内容留着。
--
-- 跑前建议（内容级还原）：
--   mysqldump -uroot -p<pwd> corecraft kp_topic \
--     --where="content LIKE '%### 5. 面试问答%'" \
--     > <工作区外>/corecraft_pre_sec5_rename_20261024.sql
--
-- 执行时机：随时（只动 corecraft 库）。


-- ========== ① 跑前自检（应当输出 226 / 0）============
SELECT COUNT(*) AS 命中 FROM corecraft.kp_topic WHERE content LIKE '%### 5. 面试问答%';
SELECT COUNT(*) AS 其它位置的面试问答 FROM corecraft.kp_topic
 WHERE content LIKE '%面试问答%' AND content NOT LIKE '%### 5. 面试问答%';


-- ========== ② 改名 ==========
UPDATE corecraft.kp_topic
SET content = REPLACE(content, '### 5. 面试问答', '### 5. 常见考点')
WHERE content LIKE '%### 5. 面试问答%';


-- ========== ③ 跑后校验（应当输出 0 / 226）============
SELECT COUNT(*) AS 残留_旧名 FROM corecraft.kp_topic WHERE content LIKE '%### 5. 面试问答%';
SELECT COUNT(*) AS 新名 FROM corecraft.kp_topic WHERE content LIKE '%### 5. 常见考点%';
SELECT COUNT(*) AS 可见知识点 FROM corecraft.kp_topic WHERE is_delete = 0;


-- ========== 回滚 ==========
-- UPDATE corecraft.kp_topic
-- SET content = REPLACE(content, '### 5. 常见考点', '### 5. 面试问答')
-- WHERE content LIKE '%### 5. 常见考点%';
