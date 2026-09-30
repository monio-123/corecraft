-- 2026-09-24 改菜单项「记录知识点」→「随手记」
--
-- 背景：用户希望把"记录知识点"改为更口语化的"随手记"
-- 影响：仅改 sys_permission.name，不动 code / id / parent_id / meta / is_delete，
--       sys_role_permission 关联表不受影响（按 code 关联）
-- 定位策略：按 code='corecraft-web:learn' 定位（不硬编码 id=12，跨环境稳定）
-- 守门条件：is_delete=0（避免误改已软删的历史记录）
-- 跑法：dev 库手动跑一次，跑前 mysqldump 备份 sys_permission 表
-- 回滚：UPDATE sys_permission SET name='记录知识点', update_time=原值 WHERE code='corecraft-web:learn';

UPDATE sys_permission
SET name = '随手记', update_time = NOW()
WHERE code = 'corecraft-web:learn' AND is_delete = 0;