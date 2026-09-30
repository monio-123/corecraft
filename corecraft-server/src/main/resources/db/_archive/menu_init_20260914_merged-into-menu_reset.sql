-- 初始化菜单数据
-- type 字段存储枚举 name：GROUP, MENU, API, OP
-- 已移除"自学平台" GROUP：旧版前端（knowledge_platform/*）已下线，路由/菜单同步移除
--   后续如需恢复，从 _archive/ 拉回；本脚本只负责系统管理 + corecraft-web 两段

-- 清除现有菜单数据（可选，谨慎使用）
-- DELETE FROM sys_permission WHERE type IN ('GROUP', 'MENU');

-- 系统管理 GROUP
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

-- corecraft-web 自主学习平台 GROUP
INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
VALUES (NULL, 'corecraft-web', 'corecraft-web', 3, 1, 'GROUP', '{"icon":"MagicStick"}', NOW(), NOW(), 0);

SET @corecraft_web_group_id = LAST_INSERT_ID();

-- corecraft-web 下的 MENU 节点（"树"是 topic 派生视图，不再是独立入口）
INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
VALUES
(@corecraft_web_group_id, 'corecraft-web:learn', '记录知识点', 1, 1, 'MENU', '{"path":"/corecraft-web/learn","icon":"MagicStick"}', NOW(), NOW(), 0),
(@corecraft_web_group_id, 'corecraft-web:topic-detail', '知识点详情', 2, 1, 'MENU', '{"path":"/corecraft-web/topic/:id","icon":"Document","hidden":true}', NOW(), NOW(), 0);

-- corecraft-web 权限
INSERT INTO sys_role_permission (role_id, permission_id, create_time, update_time, is_delete)
SELECT @admin_role_id, id, NOW(), NOW(), 0
FROM sys_permission
WHERE code IN ('corecraft-web', 'corecraft-web:learn', 'corecraft-web:topic-detail')
AND is_delete = 0;
