-- 工具脚本：完全重置菜单（先清空 + 再插默认配置）
-- ⚠️ 警告：会清空 sys_permission 表所有数据（含资源管理页面手动配置的菜单）
-- 适用场景：
--   1. 菜单配乱 / 测试环境想重置回默认
--   2. 新环境部署（首次部署时 sys_permission 为空，TRUNCATE 无副作用）
--
-- 行为：
--   1. 清空 sys_role_permission 中所有角色-菜单关联（外键依赖）
--   2. TRUNCATE sys_permission（菜单本体）
--   3. 插默认配置：系统管理 GROUP + 4 MENU
--   4. 为 admin role（默认 id=1）分配系统管理权限
--   5. 插默认配置：corecraft-web GROUP + 1 MENU（learn）
--   6. 为 admin role 分配 corecraft-web 权限
-- 注：知识点详情（topic/:id）不再做菜单项——详情是"记录"的延伸操作，从记录页卡片点进，
-- 不需要独立菜单入口；登录即可访问，不挂 sys_permission
-- 保留：sys_user / sys_role / sys_dict 等基础数据不动
--
-- type 字段存储枚举 name：GROUP, MENU, API, OP
-- 已移除"自学平台" GROUP：旧版前端（knowledge_platform/*）已下线，路由/菜单同步移除

-- ============================================================
-- 1. 先删角色-权限关联（外键依赖）
-- ============================================================

DELETE srp FROM sys_role_permission srp
INNER JOIN sys_permission sp ON srp.permission_id = sp.id;

-- ============================================================
-- 2. 再删权限/菜单本身
-- ============================================================

TRUNCATE TABLE `sys_permission`;

-- ============================================================
-- 3. 系统管理 GROUP
-- ============================================================

INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
VALUES (NULL, 'system', '系统管理', 1, 1, 'GROUP', '{"icon":"Setting"}', NOW(), NOW(), 0);

SET @system_group_id = LAST_INSERT_ID();

-- 系统管理下的 MENU 节点
INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
VALUES
(@system_group_id, 'user:manage', '用户管理', 1, 1, 'MENU', '{"path":"/users","icon":"User"}', NOW(), NOW(), 0),
(@system_group_id, 'role:manage', '角色管理', 2, 1, 'MENU', '{"path":"/roles","icon":"UserFilled"}', NOW(), NOW(), 0),
(@system_group_id, 'permission:manage', '资源管理', 3, 1, 'MENU', '{"path":"/permissions","icon":"Lock"}', NOW(), NOW(), 0),
(@system_group_id, 'dict:manage', '字典管理', 4, 1, 'MENU', '{"path":"/dicts","icon":"CollectionTag"}', NOW(), NOW(), 0);

-- 为管理员角色分配菜单权限（假设管理员角色 ID 为 1）
-- 请根据实际管理员角色 ID 修改
SET @admin_role_id = 1;

-- 系统管理权限
INSERT INTO sys_role_permission (role_id, permission_id, create_time, update_time, is_delete)
SELECT @admin_role_id, id, NOW(), NOW(), 0
FROM sys_permission
WHERE code IN ('system', 'user:manage', 'role:manage', 'permission:manage', 'dict:manage')
AND is_delete = 0;

-- ============================================================
-- 4. corecraft-web 自主学习平台 GROUP
-- ============================================================

INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
VALUES (NULL, 'corecraft-web', 'corecraft-web', 3, 1, 'GROUP', '{"icon":"MagicStick"}', NOW(), NOW(), 0);

SET @corecraft_web_group_id = LAST_INSERT_ID();

-- corecraft-web 下的 MENU 节点（"树"是 topic 派生视图，"详情"从记录页卡片进入，都不做独立菜单入口）
INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
VALUES
(@corecraft_web_group_id, 'corecraft-web:learn', '随手记', 1, 1, 'MENU', '{"path":"/corecraft-web/learn","icon":"MagicStick"}', NOW(), NOW(), 0);

-- corecraft-web 权限
INSERT INTO sys_role_permission (role_id, permission_id, create_time, update_time, is_delete)
SELECT @admin_role_id, id, NOW(), NOW(), 0
FROM sys_permission
WHERE code IN ('corecraft-web', 'corecraft-web:learn')
AND is_delete = 0;
