-- corecraft-web 知识点重构：表结构增量更新 + 详情页菜单挂载
-- 执行前请备份数据库；可重复执行（已加 IF NOT EXISTS 判断）
-- 适用于 kp_topic 已有数据的库——不删表，只增量加列、加索引、加菜单

-- ============================================================
-- 1. kp_topic 表新增 tree_id 字段
-- ============================================================

-- 加列（MySQL 8.0+ 支持 IF NOT EXISTS，低版本请手动判断）
ALTER TABLE `kp_topic`
  ADD COLUMN `tree_id` BIGINT DEFAULT NULL COMMENT '所属知识树ID（自动树逻辑回填，便于前端快速过滤）' AFTER `parent_topic_id`;

-- 加索引
ALTER TABLE `kp_topic`
  ADD KEY `idx_tree_id` (`tree_id`);

-- ============================================================
-- 2. 挂载"知识点详情"菜单（路由 /corecraft-web/topic/:id）
--    corecraft-web 已经在 corecraft-web_menu.sql 中初始化过，这里只加新菜单项
-- ============================================================

-- 找到 corecraft-web GROUP 的 id
SET @corecraft_web_group_id = (
  SELECT id FROM sys_permission
  WHERE code = 'corecraft-web' AND type = 'GROUP' AND is_delete = 0
  LIMIT 1
);

-- 找到 corecraft-web 现有的"学习主题"菜单项 id（用作锚点，决定新菜单的 sort 顺序）
SET @learn_topic_id = (
  SELECT id FROM sys_permission
  WHERE code = 'corecraft-web:learn' AND type = 'MENU' AND is_delete = 0
  LIMIT 1
);

-- 在"学习主题"和"知识树"之间插入"知识点详情"菜单项
-- sort 取学习主题(1) 和知识树(2) 之间 = 1.5（菜单按 sort 升序）
INSERT INTO sys_permission
  (parent_id, code, name, sort, is_enabled, type, meta, create_time, update_time, is_delete)
SELECT
  @corecraft_web_group_id,
  'corecraft-web:topic-detail',
  '知识点详情',
  1,
  1,
  'MENU',
  '{"path":"/corecraft-web/topic/:id","icon":"Document","hidden":true}',
  NOW(), NOW(), 0
FROM DUAL
WHERE @corecraft_web_group_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_permission
    WHERE code = 'corecraft-web:topic-detail' AND is_delete = 0
  );

-- 为所有已分配 corecraft-web:learn 的角色，也分配 corecraft-web:topic-detail
-- （详情页是从记录页/树视图跳转进入的，权限应跟随父页）
INSERT INTO sys_role_permission (role_id, permission_id, create_time, update_time, is_delete)
SELECT srp.role_id, sp_new.id, NOW(), NOW(), 0
FROM sys_role_permission srp
JOIN sys_permission sp_old
  ON srp.permission_id = sp_old.id
  AND sp_old.code = 'corecraft-web:learn'
  AND sp_old.is_delete = 0
  AND srp.is_delete = 0
JOIN sys_permission sp_new
  ON sp_new.code = 'corecraft-web:topic-detail'
  AND sp_new.is_delete = 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission srp2
  WHERE srp2.role_id = srp.role_id
    AND srp2.permission_id = sp_new.id
    AND srp2.is_delete = 0
);
