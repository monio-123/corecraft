-- 2026-11-14 剥掉正文小标题里的占位词「段名：」（一次性迁移，跑完归档）
--
-- 背景：正文生成的结构校验不过会「带问题清单重写一次」，问题文案原文喂回模型。
-- 原文案是『没有「### 序号. 段名」的段落结构』——「段名」是占位词，模型把它当
-- 字面抄进了每个小标题（如 ### 1. 段名：一句话定位）。生成侧已改文案（同日代码），
-- 本脚本只清存量：3 篇 × 共 20 处，全部是「段名：」精确串（跑前已核对：这 3 篇里
-- 「段名」只以「段名：」这一种形态出现）。
--
-- 受影响行（跑前核对）：3329 Java程序结构总览(5) / 3513 自定义异常类(7) / 3558 File类操作总览(8)
--
-- 原文备份：_backup/2026-11-14_duanming_topics.json（回滚 = 按备份整篇 UPDATE 回去 + epoch 再 +1）
--
-- ⚠️ content_hash 置 NULL 而不是 SQL 里 SHA2 一把：ContentHash 的归一化口径
--    （连续 ASCII 空白折叠）与 MySQL 的空白语义对不上，类注释明写「留 NULL，由写
--    路径补算」。content_epoch +1 必须带上：掌握度证据按 epoch 判断「是不是对旧版
--    正文采的」，改了内容不 bump 等于告诉它「没变过」。

-- 跑前自检：应返回 3 行、occ 合计 20
SELECT id, title,
       (CHAR_LENGTH(content) - CHAR_LENGTH(REPLACE(content, '段名：', ''))) / 3 AS occ
FROM corecraft.kp_topic
WHERE content LIKE '%段名：%';

-- 修数据
UPDATE corecraft.kp_topic
SET content       = REPLACE(content, '段名：', ''),
    content_hash  = NULL,
    content_epoch = content_epoch + 1,
    update_time   = NOW()
WHERE id IN (3329, 3513, 3558)
  AND content LIKE '%段名：%';

-- 跑后校验：应返回 0 行
SELECT id FROM corecraft.kp_topic WHERE content LIKE '%段名：%';

-- 回滚（如需）：按 _backup/2026-11-14_duanming_topics.json 的原文整篇写回三行，
-- 并再次 content_epoch = content_epoch + 1（内容又变了一次）。
