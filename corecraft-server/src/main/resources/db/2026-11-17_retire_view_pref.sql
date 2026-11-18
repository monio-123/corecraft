-- ============================================================
-- 2026-11-17 退役视图偏好（ui.view.category / ui.view.category.<id>）
-- 1. 清理 sys_config 中 ui.view.category% 的系统默认与个人覆盖
-- 2. 清理 sys_config_node 中 ui.view.category 与 ui.view 容器节点
--
-- 执行时机：本次迁移在 dev/线上库执行一次；幂等守卫支持安全重跑。
-- ============================================================

-- ---------- 1. 跑前自检 ----------
SELECT id, user_id, config_key, config_value
  FROM corecraft.sys_config
 WHERE config_key LIKE 'ui.view%';

SELECT id, parent_id, name, config_key
  FROM corecraft.sys_config_node
 WHERE config_key LIKE 'ui.view%';

-- ---------- 2. 幂等变更 ----------
DELETE FROM corecraft.sys_config
 WHERE config_key LIKE 'ui.view.category%';

DELETE FROM corecraft.sys_config_node
 WHERE config_key LIKE 'ui.view.category%';

DELETE FROM corecraft.sys_config_node
 WHERE config_key = 'ui.view';

-- ---------- 3. 跑后校验 ----------
SELECT COUNT(*) AS remaining_cfg FROM corecraft.sys_config WHERE config_key LIKE 'ui.view.category%';
SELECT COUNT(*) AS remaining_node FROM corecraft.sys_config_node WHERE config_key LIKE 'ui.view%';
