-- =============================================================================
-- 2026-10-18 交换 mo 与 admin 的用户 id：admin 变 1、mo 变 6
--
-- 为什么改：id=1 这个位置在项目里被当成"根用户"，admin 才应该是它。
-- mo 只是先注册的个人账号，占着 1 纯属历史顺序。
--
-- **数据跟着人走**：换完之后，原来挂在 mo 名下的 83 条知识点 / 32 个目录 /
-- 48 个标签 / 3 条 AI 配置**仍然归 mo**，只是 mo 的 id 变成了 6。
-- admin 变 1 之后名下是空的（2026-10-18 实测：换之前 admin 名下就是 0 条）。
--
-- 执行前已核实（2026-10-18）：
--   - sys_user: id=1 是 mo、id=6 是 admin
--   - **全库没有任何指向 sys_user 的外键约束**，改主键不会有级联副作用
--   - 引用 user_id 的列一共 7 张表，见下方第 2 段
--   - 后端没有 @Cacheable，不存在缓存里的旧 id
--
-- 为什么要绕临时值：直接 `SET id = 6 WHERE id = 1` 之后，下一句
-- `SET id = 1 WHERE id = 6` 会把刚搬过去的那批又搬回来（1↔6 是互换，不是搬家）。
-- 先把 1 挪到一个没人占用的临时值，再各归各位。
--
-- ⚠️ **临时值必须是正数**：`sys_user.id` 和 `sys_user_role.user_id` 的列类型是
-- `bigint unsigned`（其余 5 张表的 user_id 是有符号 bigint）。用 -1 的话第一句就
-- 报 `Out of range value for column 'id'`，而此时前一句可能已经把数据挪走了 ——
-- 所以别为了"看着干净"用负数。999999 是 2026-10-18 库里的空闲值（max(id)=7）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) 账号本身：mo 挪到临时值 → admin 占 1 → mo 落 6
-- -----------------------------------------------------------------------------
UPDATE `sys_user` SET `id` = 999999 WHERE `id` = 1;
UPDATE `sys_user` SET `id` = 1      WHERE `id` = 6;
UPDATE `sys_user` SET `id` = 6      WHERE `id` = 999999;

-- -----------------------------------------------------------------------------
-- 2) 引用表：同样三段式，顺序不能换
--    （`sys_user_role` 里 admin 原来就有一行 user_id=6，是唯一两个 id 都有的表；
--      其余 6 张表只有 user_id=1，三段式里的第 2 句是空操作）
-- -----------------------------------------------------------------------------
UPDATE `sys_user_role`        SET `user_id` = 999999 WHERE `user_id` = 1;
UPDATE `sys_user_role`        SET `user_id` = 1      WHERE `user_id` = 6;
UPDATE `sys_user_role`        SET `user_id` = 6      WHERE `user_id` = 999999;

UPDATE `kp_topic`             SET `user_id` = 999999 WHERE `user_id` = 1;
UPDATE `kp_topic`             SET `user_id` = 6      WHERE `user_id` = 999999;

UPDATE `kp_category`          SET `user_id` = 999999 WHERE `user_id` = 1;
UPDATE `kp_category`          SET `user_id` = 6      WHERE `user_id` = 999999;

UPDATE `kp_tag`               SET `user_id` = 999999 WHERE `user_id` = 1;
UPDATE `kp_tag`               SET `user_id` = 6      WHERE `user_id` = 999999;

UPDATE `ai_config`            SET `user_id` = 999999 WHERE `user_id` = 1;
UPDATE `ai_config`            SET `user_id` = 6      WHERE `user_id` = 999999;

UPDATE `ai_endpoint`          SET `user_id` = 999999 WHERE `user_id` = 1;
UPDATE `ai_endpoint`          SET `user_id` = 6      WHERE `user_id` = 999999;

-- 答题记录表 2026-10-18 实测 0 行，保留是为了将来出题后重跑不漏
UPDATE `kp_topic_quiz_record` SET `user_id` = 999999 WHERE `user_id` = 1;
UPDATE `kp_topic_quiz_record` SET `user_id` = 6      WHERE `user_id` = 999999;

-- -----------------------------------------------------------------------------
-- 3) 头像路径跟着新主人走
--    两条各只命中一行（WHERE 的 id 不同），所以顺序无所谓、也不会来回替换。
--    ⚠️ 头像**文件本体此刻仍然不在桶里**（还留在后端容器可写层，见 FEATURES #182），
--    换完之后两个人的头像都是裂的，需要各自重新上传一次。
-- -----------------------------------------------------------------------------
UPDATE `sys_user` SET `avatar` = REPLACE(`avatar`, '/u/1/avatars/', '/u/6/avatars/') WHERE `id` = 6;
UPDATE `sys_user` SET `avatar` = REPLACE(`avatar`, '/u/6/avatars/', '/u/1/avatars/') WHERE `id` = 1;
