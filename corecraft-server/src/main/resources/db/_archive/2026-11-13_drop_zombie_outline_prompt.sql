-- 2026-11-13 删除僵尸提示词配置 ai.prompt.knowledge.outline（sys_config id=59, 1897 字）
-- 性质：一次性迁移（skill §7 ⇒ 落 _archive/）
--
-- 依据（删除前已核，见 FEATURES-history.md 当日流水）：
--   1. 老主题生成链路已于 #845 删除；新链路（任务协议 v3）用 plan / plan.skeleton / plan.expand / point，
--      见 AiStagePolicy.java 的键登记 —— outline 不在其中。
--   2. 后端 Java（含拼键常量）、前端 src/、全库【引用】均无调用方；update_time 停在 2026-10-27。
--   3. 原文已备份：_backup/prompt/ai.prompt.knowledge.outline.bak-20261113.txt（含恢复说明）。
--
-- 恢复：管理界面「系统配置」重建同名键，或按备份文件 INSERT。

DELETE FROM corecraft.sys_config WHERE config_key = 'ai.prompt.knowledge.outline';
