-- =============================================================================
-- 2026-10-17 自测题：答题记录表
--
-- ⚠️ **2026-10-22 这张表已整块撤掉**（旧自测题那套：题存在库里 + 本地判分 +
--    每日复习）。旧那套和新的「考试」（临时、AI 判分、题量/难度可配）是两种东西，
--    留着就一直是两套题型表、两份解析器、两份出题提示词。
--    **撤表脚本：2026-10-22_drop_old_quiz_feature.sql**（数据备份在 _archive/）。
--    这个脚本留着只为让"按顺序重放全部脚本"的新库也有同样的历史；
--    2026-10-22 那个脚本会把表再删掉。
--
-- 新建 kp_topic_quiz_record —— 每日复习的抽题依据（2026-10-17 用户拍板建表）
--
-- ⚠️ **本脚本原本还带一条 UPDATE，给 `make_quiz` 提示词加「概念题 / 面试模拟题」，
--    已按用户 2026-10-17 的决定撤掉**（那一轮定的是「先只做现有 4 种题型的筛选」）。
--    撤掉之后库里那段提示词已逐字还原成 2026-10-10 那一版，
--    读回核对 `CHAR_LENGTH` = 733（与原值一致，说明还原是准的）。
--    文件名里的 `concept_interview_types` 是历史遗留，
--    **别按文件名以为这里该有那两种题型**。
--
-- ⚠️ 没有 DDL 改 sys_dict_item 的列：remark 是 varchar(1000)，原值 733 字符。
--    **别为了塞提示词去扩列** —— 上次为 `ai_topic_question` 的判据扩到 1000
--    就是逼着人删字。
-- ⚠️ 也没有 DDL 改 kp_topic_quiz.question_type：varchar(20)，四种题型绰绰有余。
--    该列 COMMENT 里的「题型：single / multiple / blank / essay」是**准确**的。
--
-- 📌 **本表有 3 个「预留、当前无人读」的字段**（question_type / user_answer / update_time）。
--    用户 2026-10-18 的原话是"如果是预留的就先不删，但以后要能通过迭代记录找到并删掉"，
--    所以清单登记在 `FEATURES.md` 的 #175 里 —— **要收敛时照那份清单删，别靠记忆**。
--
-- 执行时机：部署后端新版本前/后均可（纯新增表，无数据破坏）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 答题记录表
--
-- **一行 = 一次作答**，不是一次复习会话。会话级统计（今天做了几道 / 用了几分钟）
-- 以后要加再说，现在不需要 —— 这是掌握度系统的地基，表结构定错后面全要返工，
-- 所以只记「答过什么、对不对、什么时候」这三件确定要用的。
--
-- is_right 允许 NULL：**简答不自动判对错**（没有唯一答案，自动判定只会给假结果），
-- 但「做过」这件事本身要记下来，否则简答题永远不会被抽进复习。
-- 写成 NOT NULL DEFAULT 0 就等于把简答一律记成「答错」，
-- 而抽题规则里「上次做错」的优先级高于「上次做对」。
--
-- ⚠️ **刻意没有 is_delete**：这张表没有任何删除路径 —— 答过就是答过了，那条记录就是
-- 历史，不存在「撤回来」。实体因此不继承 BaseEntity（见 KpTopicQuizRecord 类注释），
-- 否则会白带一个 @TableLogic deleted、让每条查询恒真地多一个 WHERE。
-- 2026-10-18 已把建表时多加的那一列去掉，**已执行**：
--     ALTER TABLE `kp_topic_quiz_record` DROP COLUMN `is_delete`;
--
-- update_time 保留着：和全库 15 张表形状一致，DDL 的 ON UPDATE CURRENT_TIMESTAMP
-- 会在真发生更新时兜住。**当前没有任何更新路径**，所以它恒等于 create_time。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kp_topic_quiz_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `topic_id` bigint NOT NULL COMMENT '知识点ID',
  `quiz_id` bigint NOT NULL COMMENT '题目ID（kp_topic_quiz.id）',
  `question_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题型快照（题被删后仍可按题型统计）。当前无人读，是为出题规则/掌握度统计预留',
  `is_right` tinyint COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '是否答对；NULL=不判（简答）',
  `user_answer` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '用户作答原文。当前无人读，预留',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_quiz` (`user_id`,`quiz_id`),
  KEY `idx_user_create` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自测题答题记录（只增不删不改）';
