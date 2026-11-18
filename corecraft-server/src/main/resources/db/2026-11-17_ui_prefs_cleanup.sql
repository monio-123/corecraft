-- ============================================================================
-- 2026-11-17_ui_prefs_cleanup.sql
--
-- 目的：系统待办-1115 第8点「设置弹窗收敛与配置同源」数据库同步
--   1. 删除已下线的界面偏好分组与叶子节点（ui.home* / ui.learn* / sidePadX/Y /
--      detailTagsGap / detailTextInset / headingScale / headingGap / paraIndent）
--      以及 sys_config 中对应的孤儿值；
--   2. 补齐新接入的界面偏好节点（ui.detail.codeWrap / ui.browse.showMastery /
--      ui.view.category），使管理后台 /configs 与前端 PREF_SPECS 100% 同源。
--
-- 执行时机：本次迁移在 dev/线上库执行一次；新装库如已跑过前置脚本，可按顺序重放。
-- ============================================================================

-- ---------- 1. 跑前自检 ----------
SELECT id, parent_id, name, config_key, value_type, sort
  FROM corecraft.sys_config_node
 WHERE config_key LIKE 'ui%'
 ORDER BY parent_id, sort, id;

SELECT id, user_id, config_key, config_value
  FROM corecraft.sys_config
 WHERE config_key IN (
   'ui.side.sidePadX', 'ui.side.sidePadY',
   'ui.home.homeQuick', 'ui.home.homePadX', 'ui.home.homeGap',
   'ui.learn.learnGap', 'ui.learn.learnPadY', 'ui.learn.learnPadX',
   'ui.learn.learnGridGap', 'ui.learn.learnPillPy',
   'ui.detail.detailTagsGap', 'ui.detail.detailTextInset',
   'ui.detail.headingScale', 'ui.detail.headingGap', 'ui.detail.paraIndent'
 );

-- ---------- 2. 幂等变更 ----------

-- 2.1 删除 sys_config 中已下线键的孤儿值
DELETE FROM corecraft.sys_config
 WHERE config_key IN (
   'ui.side.sidePadX', 'ui.side.sidePadY',
   'ui.home.homeQuick', 'ui.home.homePadX', 'ui.home.homeGap',
   'ui.learn.learnGap', 'ui.learn.learnPadY', 'ui.learn.learnPadX',
   'ui.learn.learnGridGap', 'ui.learn.learnPillPy',
   'ui.detail.detailTagsGap', 'ui.detail.detailTextInset',
   'ui.detail.headingScale', 'ui.detail.headingGap', 'ui.detail.paraIndent'
 );

-- 2.2 删除已下线的叶子节点（先子后父）
DELETE FROM corecraft.sys_config_node
 WHERE config_key IN (
   'ui.side.sidePadX', 'ui.side.sidePadY',
   'ui.home.homeQuick', 'ui.home.homePadX', 'ui.home.homeGap',
   'ui.learn.learnGap', 'ui.learn.learnPadY', 'ui.learn.learnPadX',
   'ui.learn.learnGridGap', 'ui.learn.learnPillPy',
   'ui.detail.detailTagsGap', 'ui.detail.detailTextInset',
   'ui.detail.headingScale', 'ui.detail.headingGap', 'ui.detail.paraIndent'
 );

-- 2.3 删除已下线的分组节点
DELETE FROM corecraft.sys_config_node
 WHERE config_key IN ('ui.home', 'ui.learn');

-- 2.4 补齐分组节点 ui.browse 与 ui.view（父节点均为 ui）
INSERT INTO corecraft.sys_config_node (parent_id, name, code, config_key, value_type, sort, remark)
SELECT p.id, '浏览与布局', 'browse', 'ui.browse', NULL, 2, '知识树与卡片列表的浏览行为'
  FROM corecraft.sys_config_node p
 WHERE p.config_key = 'ui'
   AND NOT EXISTS (SELECT 1 FROM corecraft.sys_config_node x WHERE x.config_key = 'ui.browse');

INSERT INTO corecraft.sys_config_node (parent_id, name, code, config_key, value_type, sort, remark)
SELECT p.id, '视图偏好', 'view', 'ui.view', NULL, 3, '目录默认展示格式'
  FROM corecraft.sys_config_node p
 WHERE p.config_key = 'ui'
   AND NOT EXISTS (SELECT 1 FROM corecraft.sys_config_node x WHERE x.config_key = 'ui.view');

-- 2.5 补齐叶子节点：ui.detail.codeWrap / ui.browse.showMastery / ui.view.category
INSERT INTO corecraft.sys_config_node (parent_id, name, code, config_key, value_type, sort, remark)
SELECT p.id, '代码折行', 'codeWrap', 'ui.detail.codeWrap', 'text', 9, '代码块是否自动换行（1=折行 / 0=横向滚动，默认 0）'
  FROM corecraft.sys_config_node p
 WHERE p.config_key = 'ui.detail'
   AND NOT EXISTS (SELECT 1 FROM corecraft.sys_config_node x WHERE x.config_key = 'ui.detail.codeWrap');

INSERT INTO corecraft.sys_config_node (parent_id, name, code, config_key, value_type, sort, remark)
SELECT p.id, '掌握度指示', 'showMastery', 'ui.browse.showMastery', 'text', 1, '是否显示掌握度圆点与进度条（1=显示 / 0=隐藏，默认 1）'
  FROM corecraft.sys_config_node p
 WHERE p.config_key = 'ui.browse'
   AND NOT EXISTS (SELECT 1 FROM corecraft.sys_config_node x WHERE x.config_key = 'ui.browse.showMastery');

INSERT INTO corecraft.sys_config_node (parent_id, name, code, config_key, value_type, sort, remark)
SELECT p.id, '默认视图', 'category', 'ui.view.category', 'text', 1, '目录默认展示格式（tree=目录树 / card=卡片栏，默认 tree）'
  FROM corecraft.sys_config_node p
 WHERE p.config_key = 'ui.view'
   AND NOT EXISTS (SELECT 1 FROM corecraft.sys_config_node x WHERE x.config_key = 'ui.view.category');

-- ---------- 3. 跑后校验 ----------
SELECT id, parent_id, name, code, config_key, value_type, sort
  FROM corecraft.sys_config_node
 WHERE config_key LIKE 'ui%'
 ORDER BY parent_id, sort, id;

-- ---------- 4. 回滚段（按需手动执行） ----------
-- DELETE FROM corecraft.sys_config_node WHERE config_key IN ('ui.detail.codeWrap', 'ui.browse.showMastery', 'ui.view.category');
-- DELETE FROM corecraft.sys_config_node WHERE config_key IN ('ui.browse', 'ui.view');
-- （被删除的历史节点与 sys_config 行见 _backup/2026-11-17_ui_prefs_backup.json）
