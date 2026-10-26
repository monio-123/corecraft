-- 物理删除：知识包生成的「操作系统」知识点（ids 100-316，共 217 条）
-- 2026-10-24
--
-- 背景：用户要求"把上次的这个和这次的操作系统生成的这些都给删了，
--       **只删除操作系统的目录和知识点，因为这个只是这次的测试**"。
--   · 目录侧：**上一步已经硬删完**（操作系统树 ids 281-311，共 31 个目录），
--     库里现在已无任何操作系统目录 ⇒ 本脚本只管知识点这一侧。
--   · 知识点侧：之前只做了**软删**（is_delete=1），行还躺在 kp_topic 表里。
--     这次要的是"真删干净" ⇒ 物理 DELETE。
--
-- 删除范围（**只删操作系统这批**，逐簇核对过）：
--   ids 100-102  ：3 条  ｜ 10-23 17:01 那轮 OS 测试（含 1 条"ZZ测试知识点"）
--   ids 103-180  ：78 条 ｜ 10-24 03:25-03:33 上一批操作系统知识点（陈述句命名）
--   ids 181-190  ：10 条 ｜ 10-24 04:25-04:35 两轮规范命名的操作系统知识点
--   ids 191-302  ：112 条｜ 挂在操作系统树（category_id 281-311）上的知识点
--   ids 303-316  ：14 条 ｜ 10-24 13:00 起 v4.2/v4.3 真跑复验的测试卡
--   合计 217 条
--   样本标题：100「进程是资源分配单位，线程是CPU调度单位」/ 107「进程上下文切换需要保存寄存器」
--            181「操作系统总览」/ 299「…」/ 303「操作系统定义与目标」
--
-- ⛔ **ids 2-99 的 54 条软删一律不动** —— 那是用户自己早年的笔记
--    （MyBatis / JPA / K8s / TCP 之类），跟操作系统无关。
--
-- 安全前提（删前已核）：
--   · 范围内**没有存活行**（is_delete=0 的 0 条）⇒ 删了不影响界面上的任何内容
--   · **没有外部引用**（kp_topic_tag 引用 0 条、kp_topic_relation 引用 0 条）
--     ⇒ 不会留下孤儿数据
--   · 带 `is_delete = 1` 条件 ⇒ 即使将来 id 区间被复用，也只删软删行
--
-- 跑前建议（内容级还原用，跟同目录其它清理脚本一个路子）：
--   mysqldump -uroot -p<pwd> corecraft kp_topic --where="id BETWEEN 100 AND 316" \
--     > <工作区外>/corecraft_pre_os_delete_20261024.sql
--
-- 执行时机：随时（只动 corecraft 库，不动其它 schema）。


-- ========== ① 跑前自检（应当输出 217 / 0 / 0）============
SELECT COUNT(*) AS 目标行数 FROM corecraft.kp_topic WHERE id BETWEEN 100 AND 316;
SELECT COUNT(*) AS 范围内存活行 FROM corecraft.kp_topic WHERE id BETWEEN 100 AND 316 AND is_delete = 0;
SELECT COUNT(*) AS 外部引用_标签 FROM corecraft.kp_topic_tag WHERE topic_id BETWEEN 100 AND 316;
SELECT COUNT(*) AS 外部引用_关联 FROM corecraft.kp_topic_relation
 WHERE topic_id BETWEEN 100 AND 316 OR related_topic_id BETWEEN 100 AND 316;


-- ========== ② 物理删除 ==========
DELETE FROM corecraft.kp_topic
WHERE id BETWEEN 100 AND 316
  AND is_delete = 1;


-- ========== ③ 跑后校验（应当输出 0 / 0）============
SELECT COUNT(*) AS 剩余_目标区间 FROM corecraft.kp_topic WHERE id BETWEEN 100 AND 316;
SELECT COUNT(*) AS 剩余_用户自己的软删 FROM corecraft.kp_topic WHERE id BETWEEN 2 AND 99 AND is_delete = 1;
SELECT COUNT(*) AS 可见知识点 FROM corecraft.kp_topic WHERE is_delete = 0;
SELECT COUNT(*) AS 目录数 FROM corecraft.kp_category;


-- ========== 回滚 ==========
-- 内容级还原：跑 ① 之前先 mysqldump（命令见上），还原用
--   mysql -uroot -p<pwd> corecraft < <工作区外>/corecraft_pre_os_delete_20261024.sql
-- 只还原"软删状态"（不想把内容找回来、只是想让 id 区间重新可查）：
--   不可行 —— 行已物理删除；只能从 dump 里捞。
--
-- ⚠️ **这批内容是 AI 生成的测试数据**（用户正要按新规范重新生成），
--    所以脚本不附内容级备份（≈480KB）—— 真需要时按上面的 mysqldump 走。
