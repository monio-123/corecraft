-- 2026-11-13 修复「计算机网络」树（根 id=1637，任务 49）的两处生成瑕疵
-- 性质：一次性数据迁移（skill §7 ⇒ 落 _archive/）
--
-- 背景（取证见 FEATURES-history.md 当日流水）：
--   任务 49 的 expand 步骤（step 1780）模型输出把第 3 章整块多缩进了一层
--   （章头自带「├── 」前缀、子树 4 空格缩进），拼接代码无条件 depth+1
--   ⇒「3. 数据链路层与局域网」(id=1651) 被挂进「2. 物理层与数据通信」(id=1644) 下。
--   同章第 8 章模型把骨架「｜ 定位注」改写成括号拼进了章名。
--   知识点全部挂在叶目录（category_id 不变），改目录 parent / name 对正文任务零影响。
--
-- 回滚：UPDATE corecraft.kp_category SET parent_id = 1644 WHERE id = 1651;
--       UPDATE corecraft.kp_category SET name = '8. 网络编程与工程实践（Socket编程、抓包排查与性能优化）' WHERE id = 1690;

-- ① 第 3 章提为一级（id 顺序天然落在 2. 与 4. 之间，界面顺序直接正确）
UPDATE corecraft.kp_category SET parent_id = 1637 WHERE id = 1651 AND parent_id = 1644;

-- ② 第 8 章章名去掉定位注括号（定位注本不该进名字）
UPDATE corecraft.kp_category
SET name = '8. 网络编程与工程实践'
WHERE id = 1690 AND name = '8. 网络编程与工程实践（Socket编程、抓包排查与性能优化）';
