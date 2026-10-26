-- 系统配置表：系统默认（user_id=0）+ 每个用户自己的覆盖（user_id=该用户）
--
-- 【背景】
-- 界面偏好（侧边栏宽度、间距、正文字号……）原来只存在浏览器 localStorage 里：
--   1. 换台机器就没了 → 每次都得重新拖一遍；
--   2. "系统默认"写死在代码里 → 想改默认值只能改代码重新发版。
-- 这张表把两件事都搬到库里：
--   - 系统默认（user_id=0）= 管理员在「系统管理 → 系统配置」里改；
--   - 用户自己的（user_id=当前用户）= 用户在业务界面拖滑块时写；
--   - 生效优先级：用户自己的 > 系统默认 > 代码里的兜底。
--
-- 【为什么不用字典表 sys_dict_item】
-- 字典表没有 user_id，放进去就是全局一份，和"每人一份"矛盾
-- （与 ai_config / ai_endpoint 同一个理由，那两个也没有 user_id 之外的分层）。
-- 这张表是通用 KV：以后 AI 的系统预设（base_url / model / api_key）也能往里放。
--
-- 【键的格式】
--   "命名空间.键名"，例：ui.sideWidth / ui.editorFs / ai.defaultApiKey
--   命名空间用来分域，前端按前缀取（?prefix=ui）。
--
-- 【user_id=0 的含义】
-- 0 = 平台级默认，不属于任何用户。真实的用户 id 从 1 开始，不会撞。
-- 这样 (user_id, config_key) 上的唯一键能真正约束住"系统默认一行、每个用户每个键一行"。

-- ============================================================
-- 1. 建表
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '0=系统默认；>0=该用户自己的覆盖值',
  `config_key` varchar(64) NOT NULL COMMENT '配置键，格式「命名空间.键名」，例 ui.sideWidth',
  `config_value` varchar(255) NOT NULL COMMENT '配置值（数字/字符串）',
  `remark` varchar(255) DEFAULT NULL COMMENT '说明（给人看的）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_key` (`user_id`, `config_key`),
  KEY `idx_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置：系统默认(user_id=0) + 每用户覆盖';

-- ============================================================
-- 2. 菜单：系统管理 → 系统配置
-- ============================================================
-- path 必须和前端路由对上（corecraft-web/src/router/index.js 里的 configs 子路由）。
-- icon 用 Element Plus 的 Operation（滑块图形），和"配置"这件事对得上。
--
-- ⚠️ **下面几条都不依赖会话变量**（用子查询取 id）：这样脚本能拆成一条条单独执行，
-- 也不怕中途失败重跑时变量已经没了。

INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
SELECT id, 'config:manage', '系统配置', 5, 1, 'MENU', '{"path":"/configs","icon":"Operation"}', NOW(), NOW(), 0
FROM sys_permission
WHERE code = 'system' AND is_delete = 0
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM sys_permission) x WHERE x.code = 'config:manage' AND x.is_delete = 0);

-- ============================================================
-- 3. OP 权限码：管理端接口用它
-- ============================================================
-- config:api 挂在「系统配置」菜单下（OP 类型不进任何导航，只做接口鉴权）。
-- 后端 SysConfigController 的管理端三个接口（查列表 / 改系统默认 / 删一行）
-- 都有 @PreAuthorize("@auth.hasPermission('config:api')")。

INSERT INTO sys_permission (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
SELECT id, 'config:api', '系统配置管理', 1, 1, 'OP', NULL, NOW(), NOW(), 0
FROM sys_permission
WHERE code = 'config:manage' AND is_delete = 0
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM sys_permission) x WHERE x.code = 'config:api' AND x.is_delete = 0);

-- ============================================================
-- 4. 授权给管理员角色
-- ============================================================
-- 管理员角色 code=ROLE_ADMIN（见 sys_role：系统管理员）。
-- 用 code 取 id，不写死菜单 id（重跑本脚本也不会错位）。

INSERT INTO sys_role_permission (role_id, permission_id, create_time, update_time, is_delete)
SELECT r.id, p.id, NOW(), NOW(), 0
FROM sys_role r
JOIN sys_permission p ON p.code IN ('config:manage', 'config:api') AND p.is_delete = 0
WHERE r.code = 'ROLE_ADMIN' AND r.is_delete = 0;
