-- ============================================================================
-- kp_category 唯一键从「全局同名」收窄到「同一层同名」
--
-- 2026-10-05
--
-- 背景：要按知识领域系统地学（比如把「操作系统」拆成 进程/内存/文件系统 这些子目录），
--       结果撞上一个从一开始就在的限制：
--
--       uk_user_name = (user_id, name)     ← 不含 parent_id
--
--       也就是**一个用户下目录名全局唯一**，「操作系统/内存」和「计算机网络/内存」
--       不能同时存在。层级一深就必然撞。
--
-- 改成：
--       uk_user_parent_name = (user_id, parent_id, name)
--
-- 语义：**只有同一层不许重名**，不同父目录下的同名目录是允许的、也是必要的
--       （"内存" 同时属于操作系统和计算机网络，这很正常）。
--
-- ⚠️ 这条配套的后端改动不是可选的：rename 之前是 findByKey(userId, oldName)
--    按名字定位的，那套逻辑建立在"名字全局唯一"上，同名之后根本认不出改的是哪一个。
--    已同步改成按 id 定位（KpCategoryRenameReq 由 {oldName,newName} 改为 {id,newName}）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 第 1 步：历史 parent_id = NULL 归一成 0
--
-- 必须**先**做这步再换唯一键：库里有一部分老目录 parent_id 是 NULL，
-- 新写的一律是 0。不归一的话，同一个用户在 NULL 和 0 两个"逻辑上的同一层"
-- 各自能建出一个同名目录，唯一键照样拦不住。
--
-- 现状核对（2026-10-06 执行前）：17 条，2 条 NULL（JAVA / 计算机网络），
-- 4 条是 0（中间件 / 工作 / 操作系统 / 数据库），名字互不冲突，归一不会撞。
-- ---------------------------------------------------------------------------
UPDATE kp_category
SET parent_id = 0
WHERE parent_id IS NULL;

-- ---------------------------------------------------------------------------
-- 第 2 步：换唯一键
--
-- 执行前已核对不存在重复的 (user_id, parent_id, name)，ALTER 不会失败：
--   SELECT user_id, COALESCE(parent_id,-1), name, COUNT(*)
--   FROM kp_category GROUP BY 1,2,3 HAVING COUNT(*) > 1;   -- 返回空
-- ---------------------------------------------------------------------------
ALTER TABLE kp_category
    DROP INDEX uk_user_name,
    ADD UNIQUE KEY uk_user_parent_name (user_id, parent_id, name);

-- ---------------------------------------------------------------------------
-- 回查
-- ---------------------------------------------------------------------------
SHOW INDEX FROM kp_category WHERE Key_name = 'uk_user_parent_name';

SELECT id, name, parent_id FROM kp_category ORDER BY COALESCE(parent_id, 0), name;

-- 冒烟：同一层建同名应当失败，不同层建同名应当成功
-- INSERT INTO kp_category(user_id,name,parent_id) VALUES (1,'内存',  /* 操作系统 */20);  -- OK
-- INSERT INTO kp_category(user_id,name,parent_id) VALUES (1,'内存',  /* 操作系统 */20);  -- 应报 Duplicate entry
-- INSERT INTO kp_category(user_id,name,parent_id) VALUES (1,'内存',  /* 计算机网络 */2);  -- OK
