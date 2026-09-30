-- 2026-09-14 清理脚本
-- 目标：删 sys_permission 表中残留的 5 条自学平台菜单（前端已下线，菜单 100% 跳 404）
-- 涉及行：id=6 (GROUP knowledge) + id=7,8,9,10 (4 个 MENU)
--
-- 前置：自学平台前端 (knowledge_platform/*)、路由 6 条、Layout 菜单映射 6 项已全部下线
-- 风险：仅影响这 5 行；其他菜单（系统管理 + corecraft-web）不动
-- 跑前建议：mysqldump -uroot -p1qaz2wsx corecraft sys_permission sys_role_permission > /tmp/corecraft_pre_menu_20260914.sql

-- ============================================================
-- 1. 先删角色-权限关联（外键依赖）
-- ============================================================

DELETE FROM sys_role_permission
WHERE permission_id IN (6, 7, 8, 9, 10);

-- ============================================================
-- 2. 再删权限/菜单本身
-- ============================================================

DELETE FROM sys_permission
WHERE id IN (6, 7, 8, 9, 10);

-- ============================================================
-- 验证（期望：0 行）
-- ============================================================

-- SELECT COUNT(*) AS knowledge_remain FROM sys_permission
-- WHERE code LIKE 'knowledge%' OR name = '自学平台';
