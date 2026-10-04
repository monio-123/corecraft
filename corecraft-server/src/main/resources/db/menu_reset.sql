-- 工具脚本：完全重置菜单（先清空 + 再插默认配置）
-- ⚠️ 警告：会清空 sys_permission 表所有数据（含资源管理页面手动配置的菜单）
-- 适用场景：
--   1. 菜单配乱 / 测试环境想重置回默认
--   2. 新环境部署（首次部署时 sys_permission 为空，TRUNCATE 无副作用）
--
-- 行为：
--   1. 清空 sys_role_permission 中所有角色-菜单关联（外键依赖）
--   2. TRUNCATE sys_permission（菜单本体）
--   3. 插默认配置：系统管理 GROUP + 4 MENU（shell=admin）
--   4. 为 admin role（默认 id=1）分配系统管理权限
--   5. 插默认配置：corecraft-web GROUP + 2 MENU（shell=app）
--   6. 为 admin role 分配 corecraft-web 权限
-- 注：知识点详情（app/topic/:id）不做菜单项——详情是"记录"的延伸操作，从记录页卡片点进，
-- 不需要独立菜单入口；登录即可访问，不挂 sys_permission
-- 保留：sys_user / sys_role / sys_dict 等基础数据不动
--
-- meta.shell（2026-10-03 新增）——决定菜单项渲染在哪个壳里：
--   'admin'（缺省）→ 管理后台 Layout（顶部菜单 + 页面 tabs，密集）
--   'app'          → 业务界面 AppLayout（左侧栏 + 居中窄栏，沉浸式）
-- 分组和菜单都读这个字段，由超管在「资源管理」页配置。
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
-- 「用户管理」单独插入：它下面要挂 OP 类型的 user:add，需要先拿到它的 id
INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
VALUES (@system_group_id, 'user:manage', '用户管理', 1, 1, 'MENU', '{"path":"/users","icon":"User"}', NOW(), NOW(), 0);

SET @user_manage_menu_id = LAST_INSERT_ID();

INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
VALUES
(@system_group_id, 'role:manage', '角色管理', 2, 1, 'MENU', '{"path":"/roles","icon":"UserFilled"}', NOW(), NOW(), 0),
(@system_group_id, 'permission:manage', '资源管理', 3, 1, 'MENU', '{"path":"/permissions","icon":"Lock"}', NOW(), NOW(), 0),
(@system_group_id, 'dict:manage', '字典管理', 4, 1, 'MENU', '{"path":"/dicts","icon":"CollectionTag"}', NOW(), NOW(), 0);

-- ============================================================
-- 3.5 OP 节点：接口操作权限（不是菜单，不进任何导航）
-- ============================================================
-- user:add 是当前唯一接入接口鉴权体系的权限码。
--   后端 SysUserController#createSysUser 上有 @PreAuthorize("@auth.hasPermission('user:add')")
--   前端 Users.vue 的「新建用户」按钮由 hasPermission('user:add') 控制显隐
-- ⚠️ 本脚本会 TRUNCATE sys_permission，漏掉这一行会导致该端点对所有人 403。

INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
VALUES (@user_manage_menu_id, 'user:add', '新建用户', 1, 1, 'OP', NULL, NOW(), NOW(), 0);

-- 为管理员角色分配菜单权限（假设管理员角色 ID 为 1）
-- 请根据实际管理员角色 ID 修改
SET @admin_role_id = 1;

-- 系统管理权限（含 OP 节点 user:add）
INSERT INTO sys_role_permission (role_id, permission_id, create_time, update_time, is_delete)
SELECT @admin_role_id, id, NOW(), NOW(), 0
FROM sys_permission
WHERE code IN ('system', 'user:manage', 'role:manage', 'permission:manage', 'dict:manage', 'user:add')
AND is_delete = 0;

-- ============================================================
-- 4. corecraft-web 业务界面 GROUP（shell=app）
-- ============================================================

INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
VALUES (NULL, 'corecraft-web', '知识库', 3, 1, 'GROUP', '{"icon":"MagicStick","shell":"app"}', NOW(), NOW(), 0);

SET @corecraft_web_group_id = LAST_INSERT_ID();

-- 业务界面下的 MENU 节点（"详情"从记录页卡片进入，不做独立菜单入口）
INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
VALUES
(@corecraft_web_group_id, 'corecraft-web:home', '首页', 1, 1, 'MENU', '{"path":"/app","icon":"House","shell":"app"}', NOW(), NOW(), 0),
(@corecraft_web_group_id, 'corecraft-web:learn', '随手记', 2, 1, 'MENU', '{"path":"/app/learn","icon":"EditPen","shell":"app"}', NOW(), NOW(), 0);

-- corecraft-web 权限
INSERT INTO sys_role_permission (role_id, permission_id, create_time, update_time, is_delete)
SELECT @admin_role_id, id, NOW(), NOW(), 0
FROM sys_permission
WHERE code IN ('corecraft-web', 'corecraft-web:home', 'corecraft-web:learn')
AND is_delete = 0;
