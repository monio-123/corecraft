-- 2026-09-20 清理脚本
-- 目标：删 sys_permission 表中 corecraft-web:topic-detail 菜单项 + cascade 删 sys_role_permission 关联
-- 涉及行：sys_permission.id = 13 (code='corecraft-web:topic-detail')
--         sys_role_permission.srp_id = 159 (role_id=1 ROLE_ADMIN → permission_id=13)
--
-- 前置：前端路由 /corecraft-web/topic/:id 已删，TopicDetailView.vue → TopicDetail.vue，
--       Layout.vue getTabTitle 映射已清，menu_reset.sql 同步去 topic-detail 行（FEATURES #34）
-- 原因：详情页是"记录"的延伸操作，不应做独立菜单入口；登录即可访问，不挂 sys_permission
-- 风险：仅影响 id=13 这 1 行 + 它在 sys_role_permission 的 1 条关联；其他菜单不动
-- 物理删顺序：先 sys_role_permission（避免孤儿引用）→ 再 sys_permission
-- 不动：sys_role / sys_user / sys_dict 等基础数据
-- 跑前建议：mysqldump -uroot -p<your_pwd> corecraft sys_permission sys_role_permission > /tmp/corecraft_pre_menu_20260920.sql

-- ============================================================
-- 1. 先删角色-权限关联（避免孤儿引用）
-- ============================================================

DELETE FROM sys_role_permission
WHERE permission_id IN (
  SELECT id FROM sys_permission WHERE code = 'corecraft-web:topic-detail'
);

-- ============================================================
-- 2. 再删权限/菜单本身
-- ============================================================

DELETE FROM sys_permission
WHERE code = 'corecraft-web:topic-detail';

-- ============================================================
-- 验证（期望：sys_permission 返回 0 行；sys_role_permission 引用 permission_id=13 的为 0）
-- ============================================================

-- SELECT COUNT(*) AS topic_detail_remain FROM sys_permission
--   WHERE code = 'corecraft-web:topic-detail';
-- SELECT COUNT(*) AS orphan_srp FROM sys_role_permission srp
--   LEFT JOIN sys_permission sp ON srp.permission_id = sp.id
--   WHERE sp.id IS NULL;