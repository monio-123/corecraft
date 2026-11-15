-- 2026-11-12 人工节点「已办」标记（任务 → 节点 三层的收尾）
-- 为什么需要：人工节点原来是靠「后继节点已经有步骤」判断"已越过"。
-- 链尾的人工节点（AI 归类跑完 → 确认/采纳）**没有后继** ⇒ 永远判不出"人已经办完了"，
-- 卡片会一直停在「待确认」。这一列就是那件事的时间戳。
-- 回滚：
--   ALTER TABLE kp_gen_task DROP COLUMN confirmed_at;

ALTER TABLE kp_gen_task
  ADD COLUMN confirmed_at DATETIME NULL DEFAULT NULL
  COMMENT '人工节点办完的时间（采纳 / 确认）；NULL = 还没办' AFTER finish_time;
