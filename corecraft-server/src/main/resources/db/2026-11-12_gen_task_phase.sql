-- 2026-11-12 任务阶段化（设计笔记 id 3026）
-- 任务 → 阶段 → 步骤：步骤补 4 列，任务补 1 列；state 增加 awaiting（不改列定义）
-- 回滚：
--   ALTER TABLE kp_gen_step DROP COLUMN phase, DROP COLUMN phase_seq,
--     DROP COLUMN output_summary, DROP COLUMN need_confirm;
--   ALTER TABLE kp_gen_task DROP COLUMN stage;

ALTER TABLE kp_gen_step
  ADD COLUMN phase VARCHAR(32) NOT NULL DEFAULT '' COMMENT '阶段 key：skeleton/expand/verify/confirm/build/write/finish' AFTER seq,
  ADD COLUMN phase_seq INT NOT NULL DEFAULT 0 COMMENT '阶段内序号（1 起）' AFTER phase,
  ADD COLUMN output_summary VARCHAR(200) NOT NULL DEFAULT '' COMMENT '一句话输出摘要（列表直接显示）' AFTER topic_id,
  ADD COLUMN need_confirm TINYINT NOT NULL DEFAULT 0 COMMENT '1=建议人工复核（不阻断流程）' AFTER output_summary;

ALTER TABLE kp_gen_task
  ADD COLUMN stage VARCHAR(32) NOT NULL DEFAULT '' COMMENT '当前阶段 key（卡片显示 阶段 2/4）' AFTER status;

-- 老任务：步骤没有 phase 时前端归到「执行」组，这里统一补成 write，避免空串到处判
UPDATE kp_gen_step SET phase = 'write' WHERE phase = '' AND task_id IN (SELECT id FROM kp_gen_task WHERE type = 'content');
UPDATE kp_gen_step SET phase = 'classify' WHERE phase = '' AND task_id IN (SELECT id FROM kp_gen_task WHERE type = 'classify');
