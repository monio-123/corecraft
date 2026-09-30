-- 清除 corecraft-web 业务相关数据（保留表结构）
-- 包括：
--   1. 知识点业务数据（kp_topic / kp_tag / kp_topic_tag / kp_topic_quiz / kp_topic_relation）
--   2. corecraft-web 菜单权限（sys_permission 中 code 以 'corecraft-web' 开头的 + 关联的 sys_role_permission）
-- 不动：系统管理、用户/角色、字典等基础数据

-- ============================================================
-- 1. 知识点业务数据
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE `kp_topic_relation`;
TRUNCATE TABLE `kp_topic_quiz`;
TRUNCATE TABLE `kp_topic_tag`;
TRUNCATE TABLE `kp_topic`;
TRUNCATE TABLE `kp_tag`;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 2. corecraft-web 菜单权限
-- ============================================================

-- 先删角色-权限关联（外键依赖）
DELETE srp FROM sys_role_permission srp
INNER JOIN sys_permission sp ON srp.permission_id = sp.id
WHERE sp.code LIKE 'corecraft-web%' AND sp.is_delete = 0;

-- 再删权限本身
DELETE FROM sys_permission
WHERE code LIKE 'corecraft-web%' AND is_delete = 0;
