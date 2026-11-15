-- =====================================================================
-- 2026-11-14 清理脏掌握度证据与投影（用户指令：只保留 Java程序结构总览）
--
-- 背景（2026-11-14 随堂测验排查时发现）：kp_mastery_evidence 共 8 条，其中
--   ① topic_id=3017（title='__tmp_topic_'，topic 不真实存在）1 条 —— 综合题
--      归属失败时的临时挂点（设计 §4.2 归属失败不阻塞考试 ⇒ 证据挂空）
--   ② topic_id=3012（待归类清理，type_id=1=笔记）1 条 + 投影 1 行 —— 笔记类型
--      默认不参与掌握度（方案 C），该投影是 2026-11-11 早期建的存量
-- 两条都不进目录聚合（参与判据过滤），但物理上是脏行。用户拍板清掉，
-- 只保留 topic 3329（Java程序结构总览，学习类型）的 6 条 quiz 证据与投影。
--
-- 幂等：WHERE 精确匹配，重复执行 0 行。
-- 回滚段在文件尾部（被删值已注释保留）。
-- =====================================================================

-- ① 脏证据：临时挂点
DELETE FROM corecraft.kp_mastery_evidence
WHERE topic_id = 3017 AND source = 'exam';

-- ② 脏证据：笔记类型（不参与掌握度）
DELETE FROM corecraft.kp_mastery_evidence
WHERE topic_id = 3012 AND source = 'exam';

-- ③ 脏投影：3012 的学习投影（笔记不参与掌握度，方案 C 后该行不该存在）
DELETE FROM corecraft.kp_topic_learning
WHERE topic_id = 3012;

-- =====================================================================
-- 回滚段（被删值备份）：
--
-- ① kp_mastery_evidence topic_id=3017（id 待查，原值）：
--    source='exam', attempt_id=原 exam 卷的 attempt, answer_id=…, rubric_key='__question__',
--    grade_revision=1, attribution_revision=…, score_ratio=…, content_hash=…
--
-- ② kp_mastery_evidence topic_id=3012：同上形状（source='exam'）
--
-- ③ kp_topic_learning topic_id=3012：learned_at=2026-11-11T07:27:03, ability=100.00,
--    memory_stability_days=2.00, confidence_level=1
-- =====================================================================

-- =====================================================================
-- 2026-11-14 补充清理（回读时发现的更深脏行）：
-- 回读发现 kp_topic_learning 还有 3 行脏投影（首轮查询 JOIN kp_category 漏掉了
-- category 为 NULL / 目录已删的行）：
--   topic_id=3017（__tmp_topic_，ability=100）—— 与脏证据同源
--   topic_id=3006（软件质量模型，ability=NULL）—— 无任何证据支撑
--   topic_id=2944（计算机组成与体系结构总览，ability=NULL）—— 同上
-- 用户口径：只保留 3329（Java程序结构总览）。幂等，重复执行 0 行。
DELETE FROM corecraft.kp_topic_learning
WHERE topic_id IN (3017, 3006, 2944);

-- 回滚备份：
--   3017：ability=100.00, learned_at=（建行时间）
--   3006：ability=NULL（learned_at=建行时间）
--   2944：ability=NULL（learned_at=建行时间）
-- =====================================================================
