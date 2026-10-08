-- ============================================================
-- 迁移留档：2026-10-03
-- 目的：去掉 createSysUser 上的角色硬编码，补齐 user:add 权限资源与 admin 账号
-- ============================================================
--
-- 【背景】
--   SysUserController#createSysUser 原注解为：
--     @PreAuthorize("@auth.hasRoleOrPermission(T(...SecurityUtil).ROLE_ADMIN, 'user:add')")
--   两个问题：
--     1. 角色编码 "ROLE_ADMIN" 硬编码在注解里 —— 违反「不设超管、菜单与鉴权纯配置驱动」
--     2. 权限码 'user:add' 在 sys_permission 表里根本不存在，前端 Users.vue 却在用
--        hasPermission('user:add') —— 后端注解的权限分支永远为 false，实际只认角色
--
-- 【本期决策】只补这一个样本端点，其余接口暂不接入鉴权体系。
--   注解改为纯权限码判定：@PreAuthorize("@auth.hasPermission('user:add')")
--   user:add 作为 OP 类型权限入库并授予 ROLE_ADMIN，代码里不再出现任何角色常量。
--
-- 【重要】本脚本含明文口令哈希，请勿提交到远端仓库。
--   下文 ${ADMIN_PASSWORD_HASH} 为占位符，由执行者自行用 BCryptPasswordEncoder 生成：
--     new BCryptPasswordEncoder().encode("你的口令")
--
-- 【幂等】全部使用 INSERT ... SELECT NOT EXISTS 判重，可重复执行。

-- ------------------------------------------------------------
-- 1. 建 user:add 权限资源（OP 类型，挂在「用户管理」菜单下）
-- ------------------------------------------------------------
INSERT INTO sys_permission (name, code, sort, is_enabled, type, parent_id, meta)
SELECT '新建用户', 'user:add', 1, 1, 'OP', p.id, NULL
FROM sys_permission p
WHERE p.code = 'user:manage' AND p.is_delete = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'user:add' AND x.is_delete = 0);

-- ------------------------------------------------------------
-- 2. 授予 ROLE_ADMIN
-- ------------------------------------------------------------
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'ROLE_ADMIN' AND r.is_delete = 0
  AND p.code = 'user:add' AND p.is_delete = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ------------------------------------------------------------
-- 3. 建 admin 账号并关联 ROLE_ADMIN
--    注意 sys_user.mobile 是 NOT NULL 且无默认值，必须给值（空串即可）
--    account_status：0=正常 1=禁用
-- ------------------------------------------------------------
INSERT INTO sys_user (username, password, nickname, account_status, dept_id, avatar, email, mobile)
SELECT 'admin', '${ADMIN_PASSWORD_HASH}', '管理员', 0, NULL, NULL, '', ''
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.username = 'admin' AND u.is_delete = 0);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
CROSS JOIN sys_role r
WHERE u.username = 'admin' AND u.is_delete = 0
  AND r.code = 'ROLE_ADMIN' AND r.is_delete = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur
    WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- ------------------------------------------------------------
-- 4. 把既有用户降为普通角色：管理员只有一个，就是 admin
-- ------------------------------------------------------------
-- 此前 mo 同时挂 ROLE_ADMIN + ROLE_USER。用户诉求：mo 就是普通角色，
-- 管理员是独立账号 admin。因此解除 mo 的 ROLE_ADMIN 关联。
--
-- ⚠️ 必须【物理删】，不能软删（UPDATE is_delete=1）：
--    SysUserMapper.selectOne（用户列表那条 SQL）的三个 join 全都没过滤 is_delete
--    （selectByUsername 那条过滤了），软删的关联行仍会被查出来，
--    用户列表里 mo 会继续显示 ROLE_ADMIN。物理删后每人只返回一行。
--    另外该表无唯一键，软删墓碑也不会撞键，纯关联表本就应物理删。

DELETE ur FROM sys_user_role ur
JOIN sys_user u ON u.id = ur.user_id
JOIN sys_role r ON r.id = ur.role_id
WHERE u.username = 'mo' AND r.code = 'ROLE_ADMIN';

-- ------------------------------------------------------------
-- 5. 回查
-- ------------------------------------------------------------
-- SELECT id, code, name, type, parent_id FROM sys_permission WHERE code = 'user:add';
-- SELECT u.username, GROUP_CONCAT(r.code) AS roles
--   FROM sys_user u
--   LEFT JOIN sys_user_role ur ON ur.user_id = u.id AND ur.is_delete = 0
--   LEFT JOIN sys_role r ON r.id = ur.role_id AND r.is_delete = 0
--   WHERE u.is_delete = 0 GROUP BY u.id, u.username ORDER BY u.id;
-- 期望：admin=ROLE_ADMIN（10 权限）；mo/timo/77712/999=ROLE_USER（3 业务菜单）
