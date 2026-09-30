-- 工具脚本：手动重置菜单用，非初始化。首次部署不要跑。
-- 适用场景：菜单配乱/想完全重置时，手工跑本脚本清空，再重跑 menu_init.sql。
--
-- ⚠️ 注意：corecraft-web_menu.sql 已下线（合并到 menu_init.sql），不要去找它
--
-- 行为：
--   1. 清空 sys_role_permission 中所有角色-菜单关联（外键依赖）
--   2. TRUNCATE sys_permission（菜单本体）
-- 保留：sys_user / sys_role / sys_dict 等基础数据不动

-- ============================================================
-- 1. 先删角色-权限关联（外键依赖）
-- ============================================================

DELETE srp FROM sys_role_permission srp
INNER JOIN sys_permission sp ON srp.permission_id = sp.id;

-- ============================================================
-- 2. 再删权限/菜单本身
-- ============================================================

TRUNCATE TABLE `sys_permission`;
