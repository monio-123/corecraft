-- 2026-10-03 菜单分壳：业务界面从管理后台壳里拆出来
--
-- 背景：
--   业务页面此前挂在管理后台 Layout（顶部菜单 + 可拖拽页面 tabs）下，
--   整套 admin 交互范式（密集、字段全露、表格流）压在"随手记"这类内容型功能上，
--   既不好看也不是产品该有的样子。
--
-- 引入 meta.shell（存在 sys_permission.meta 这个 JSON 里，不加新列）：
--   'admin'（缺省）→ 管理后台 Layout，只给超管
--   'app'          → 业务界面 AppLayout（左侧栏 + 居中窄栏）
-- 由超管在「资源管理」页的"界面类型"字段配置，代码里不判角色。
--
-- 本脚本做四件事：
--   1. corecraft-web 分组标 shell=app，重命名为"知识库"
--   2. 随手记 path 从 /corecraft-web/learn 迁到 /app/learn，标 shell=app
--   3. 知识点详情 path 迁到 /app/topic/:id，routeName 改 AppTopicDetail
--      （它本来就不进导航——path 含 ':' 会被 isPlaceholderPath 过滤）
--   4. 新增"首页"菜单（/app），并授予系统管理员角色
--
-- 数据影响面（迁移前已在 corecraft 库核对）：
--   sys_permission 共 8 行有效数据，只动 id ∈ {11, 12, 14} 三行 + 新增 1 行。
--   系统管理分组及其 4 个子菜单（id 1-5）不动——它们没有 shell 字段，
--   前端按缺省 'admin' 处理，行为不变。
--   sys_user / sys_role / sys_dict 等基础数据不动。
--
-- 触发时机：本次迁移跑一次即可。全新环境直接跑 menu_reset.sql 即可，已含新结构。
-- 回滚：把三行 meta 改回去即可（见下方注释里的原值），并删除新增的首页菜单。

-- ============================================================
-- 1. 分组：标 app 壳，重命名
-- ============================================================

-- 原 meta: {"icon":"MagicStick"}
UPDATE sys_permission
SET name = '知识库',
    meta = '{"icon":"MagicStick","shell":"app"}',
    update_time = NOW()
WHERE code = 'corecraft-web' AND type = 'GROUP' AND is_delete = 0;

-- ============================================================
-- 2. 随手记：路径迁到 /app
-- ============================================================

-- 原 meta: {"path":"/corecraft-web/learn","icon":"MagicStick"}
UPDATE sys_permission
SET sort = 2,
    meta = '{"path":"/app/learn","icon":"EditPen","shell":"app"}',
    update_time = NOW()
WHERE code = 'corecraft-web:learn' AND is_delete = 0;

-- ============================================================
-- 3. 知识点详情：路径与 routeName 跟随新壳
-- ============================================================

-- 原 meta: {"path":"/corecraft-web/topic/:id","routeName":"CorecraftWebTopicDetail"}
UPDATE sys_permission
SET meta = '{"path":"/app/topic/:id","routeName":"AppTopicDetail","shell":"app"}',
    update_time = NOW()
WHERE code = 'corecraft-web:topic-detail' AND is_delete = 0;

-- ============================================================
-- 4. 新增业务界面首页菜单
-- ============================================================

-- 幂等：已存在就跳过
-- （MySQL 不允许 INSERT ... SELECT 直接读被插入的同一张表，故用派生表包一层）
INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
SELECT g.id, 'corecraft-web:home', '首页', 1, 1, 'MENU',
       '{"path":"/app","icon":"House","shell":"app"}', NOW(), NOW(), 0
FROM (SELECT id FROM sys_permission
      WHERE code = 'corecraft-web' AND type = 'GROUP' AND is_delete = 0) g
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT code FROM sys_permission) e
  WHERE e.code = 'corecraft-web:home'
);

SET @home_perm_id = (SELECT id FROM (SELECT id, code FROM sys_permission) t
                     WHERE t.code = 'corecraft-web:home' LIMIT 1);

-- 授予系统管理员角色（沿用库里"谁有 corecraft-web 就有谁"的规则，不写死 role_id=1）
INSERT INTO sys_role_permission (role_id, permission_id, create_time, update_time, is_delete)
SELECT rp.role_id, @home_perm_id, NOW(), NOW(), 0
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.code = 'corecraft-web' AND rp.is_delete = 0 AND p.is_delete = 0
  AND @home_perm_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, permission_id FROM sys_role_permission) e
    WHERE e.role_id = rp.role_id AND e.permission_id = @home_perm_id
  );
