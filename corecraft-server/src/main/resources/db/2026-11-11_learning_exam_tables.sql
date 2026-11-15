-- ============================================================
-- 2026-11-11 学习掌握度与每日复习（一期：数据地基）
--
-- ## 为什么
--
-- 现在考试/随堂测验整条链路是前端内存态：出题、作答、判分都只活在浏览器里，
-- 刷新即丢，考过什么、答得对错、当时讲解一律无档可查，掌握度更是无从谈起。
-- 这六张表把「卷子 → 作答 → 判定 → 证据」落成数据，掌握度只是这些证据的一个投影。
--
--   kp_topic_learning    学习投影（每用户每知识点一行：学过标记 / 掌握度缓存 / 复习调度）
--   kp_exam_paper        一份试卷（随堂测验 / 目录考试 / 复习卷共用一张表）
--   kp_exam_question     试卷中的一道题（题目快照 + 知识归属快照 + 指纹）
--   kp_exam_attempt      一次作答（重做 = 新 attempt 指向原记录）
--   kp_exam_answer       一道题的作答与判定（按 grade_revision 留痕）
--   kp_mastery_evidence  不可变知识证据（掌握度的唯一权威来源）
--
-- 另给 kp_topic 补两列：content_hash / content_epoch（内容版本锚点）。
--
-- ## 设计口径（细节见 TODO-learning-mastery.md §10）
--
-- ① **证据表是唯一权威，学习投影全是缓存**：ability / review_count 等字段
--    随时可以从证据表重放重建；但「学过」标记（learned_at）是用户显式操作，
--    不能从证据反推。重放修复可以安全覆盖投影，不能覆盖 learned_at。
-- ② **证据不可变、不软删**：重新判分/重新归属产生新 revision 的新证据，
--    旧证据打 superseded_by 指针留痕，绝不物理改写历史。
--    （kp_exam_answer 同理物理删：唯一键 (attempt_id, question_id) 撞不起软删墓碑。）
-- ③ **判分为空就是未判定**：verdict / score_ratio 可空，未判定不落 0，
--    partial 成绩只算已判定的题，整卷成绩才不会和知识点证据打架。
-- ④ **内容版本判据只有一条**：evidence.content_epoch == topic.content_epoch。
--    hash 只负责 epoch 内一般编辑的可信度打折，任何接口不得另造「当前版本」定义。
-- ⑤ **算法字段集中在这里**：参数不进用户配置页（H 翻倍/减半、封顶 180、K 封顶 4），
--    全在后端常量里，调参只动代码不动库。
-- ⑥ JSON 一律 TEXT 存 + JsonUtils 读写（项目里 MySQL JSON 类型零先例）。
--
-- 执行时机：一次性建表 + 补列（幂等，可重复跑）。只动 corecraft 库。
-- 新库直接跑 knowledge_platform.sql（那边已收录本文件全部结构），不必重放本文件。
-- ============================================================


-- ========== ① 跑前自检 ==========
-- 期望：全部报 0（Table ... doesn't exist / 空结果）＝ 干净库，往下跑。
-- 若已存在说明本文件跑过或跑了一半：CREATE TABLE IF NOT EXISTS 与补列段都是幂等的，直接重跑即可。
SELECT COUNT(*) AS 已存在的学习考试表 FROM information_schema.tables
 WHERE table_schema = 'corecraft'
   AND table_name IN ('kp_topic_learning','kp_exam_paper','kp_exam_question',
                      'kp_exam_attempt','kp_exam_answer','kp_mastery_evidence');


-- ============================================================
-- ② 学习投影（每用户每知识点一行）
-- ============================================================

CREATE TABLE IF NOT EXISTS `corecraft`.`kp_topic_learning` (
  `id`                      BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`                 BIGINT        NOT NULL COMMENT '所属用户ID',
  `topic_id`                BIGINT        NOT NULL COMMENT '知识点ID',
  `learned_at`              DATETIME      DEFAULT NULL COMMENT '标记已学时间；NULL=未学。是否已学只看这一列，不再维护意思重复的布尔字段',
  `review_state`            VARCHAR(20)   NOT NULL DEFAULT 'inactive' COMMENT '调度姿态：active=正常调度 / pending_initial=待首次评估 / pending_confirm=待确认当前状态 / pending_reassessment=待复测 / inactive=停用（未学或取消已学）',
  `pending_reason`          VARCHAR(200)  DEFAULT NULL COMMENT '进入待办状态的原因（人看得懂；不靠时间字段猜用户为什么进队列）',
  `ability`                 DECIMAL(5,2)  DEFAULT NULL COMMENT '能力 A（0-100）投影缓存；NULL=当前 epoch 未评估。权威在证据表',
  `confidence_level`        TINYINT       NOT NULL DEFAULT 0 COMMENT '可信度档：0=未评估 / 1=初步 / 2=较可信 / 3=稳定（判据见设计 §6.4）',
  `memory_stability_days`   DECIMAL(6,2)  NOT NULL DEFAULT 1 COMMENT '记忆稳定度 H（天）。半衰期≈复习间隔的简化：好表现翻倍封顶 180，薄弱减半保底 1',
  `last_mastery_at`         DATETIME      DEFAULT NULL COMMENT '最近具备回忆测量资格的主考证据时间（R(t) 的 t0）',
  `last_schedule_at`        DATETIME      DEFAULT NULL COMMENT '最近调度合格事件时间',
  `next_review_at`          DATETIME      DEFAULT NULL COMMENT '下次复习到期时间；NULL=不调度（未学/取消已学）',
  `review_count`            INT           NOT NULL DEFAULT 0 COMMENT '生命周期复习次数（调度合格事件累计）',
  `lapse_count`             INT           NOT NULL DEFAULT 0 COMMENT '生命周期薄弱次数',
  `epoch_review_count`      INT           NOT NULL DEFAULT 0 COMMENT '当前 epoch 复习次数（重放只重建这一列）',
  `epoch_lapse_count`       INT           NOT NULL DEFAULT 0 COMMENT '当前 epoch 薄弱次数',
  `effective_event_weight`  DECIMAL(6,3)  NOT NULL DEFAULT 0 COMMENT '当前 epoch 有效事件权重之和（K 的原料；消费时封顶 4，这里存原值）',
  `active_content_epoch`    INT           NOT NULL DEFAULT 1 COMMENT '投影当前反映的正文 epoch（证据统一判据的投影侧）',
  `algorithm_version`       VARCHAR(20)   NOT NULL DEFAULT 'v1' COMMENT '投影由哪版算法重放得出',
  `version`                 BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本号：多端同时答题/重放防互相覆盖',
  `create_time`             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete`               TINYINT       NOT NULL DEFAULT 0 COMMENT '软删对齐项目约定。只随知识点删除而隐藏——topic_id 不复用，唯一键不会和墓碑打架；取消已学不是删除，走 review_state',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_topic` (`user_id`, `topic_id`),
  KEY `idx_review_queue` (`user_id`, `learned_at`, `next_review_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='学习投影：算法字段全是缓存，权威=证据表+learned 标记；重放可安全覆盖';


-- ============================================================
-- ③ 试卷（随堂测验 / 目录考试 / 复习卷共用）
-- ============================================================

CREATE TABLE IF NOT EXISTS `corecraft`.`kp_exam_paper` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`            BIGINT        NOT NULL COMMENT '所属用户ID（试卷是用户私有数据，查询一律带 user_id 校验）',
  `kind`               VARCHAR(16)   NOT NULL DEFAULT 'quiz' COMMENT 'quiz=随堂测验 / exam=目录考试 / review=复习卷',
  `category_id`        BIGINT        DEFAULT NULL COMMENT '目录考试有值；复习卷跨目录允许 NULL，范围以 scope_snapshot 为准',
  `title`              VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '卷名（人看得懂）',
  `status`             VARCHAR(16)   NOT NULL DEFAULT 'generating' COMMENT 'generating=出卷中 / ready=可作答 / failed=出卷失败（生成完才进答题）',
  `scope_snapshot`     TEXT          COMMENT 'JSON：当时范围——知识点 id、标题、内容 hash/epoch、目录路径（快照，不回查现状）',
  `settings_snapshot`  TEXT          COMMENT 'JSON：题型、道数、分值、难度、附加要求、模型等出卷参数',
  `gen_task_id`        BIGINT        DEFAULT NULL COMMENT '出卷走任务中心时的任务 id（可空）',
  `create_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete`          TINYINT       NOT NULL DEFAULT 0 COMMENT '软删（考试记录可从历史里删掉；证据不动）',
  PRIMARY KEY (`id`),
  KEY `idx_user_kind_time` (`user_id`, `kind`, `create_time`),
  KEY `idx_user_category` (`user_id`, `category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='试卷：标准答案属用户私有数据，不提供含答案的公开分享';


-- ============================================================
-- ④ 题目（不可变快照 + 归属快照）
-- ============================================================

CREATE TABLE IF NOT EXISTS `corecraft`.`kp_exam_question` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT,
  `paper_id`              BIGINT        NOT NULL COMMENT '所属试卷',
  `question_no`           INT           NOT NULL DEFAULT 0 COMMENT '卷内题号（稳定标识靠 id，题号只管展示顺序）',
  `type`                  VARCHAR(16)   NOT NULL DEFAULT '' COMMENT '题型 code；全站唯一一份题型表在前端 examParse.EXAM_TYPES（single/multiple/judge/concept/interview…）',
  `max_score`             DECIMAL(6,2)  NOT NULL DEFAULT 0 COMMENT '满分',
  `question_payload`      TEXT          NOT NULL COMMENT 'JSON：题干、选项、参考答案、评分点、场景与认知层次（当次 AI 产物快照）',
  `question_fingerprint`  CHAR(64)      DEFAULT NULL COMMENT '题干归一化哈希（与 contentHash 同源归一化+哈希）；跨卷撞车比对用。只存哈希不存题干副本；比对在应用层做',
  `attribution_payload`   TEXT          COMMENT 'JSON：题目级/评分点级知识归属、权重、归属方式与原因。按 attribution_revision 留痕，不能只留最新版',
  `attribution_status`    VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending / attributed / partial / unattributed / failed（unattributed 合法：融合题允许没有归属，绝不硬凑）',
  `attribution_revision`  INT           NOT NULL DEFAULT 0 COMMENT '归属版本，单调递增；0=尚未归属。判分消费哪一版由当前指针明确',
  `create_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_paper_no` (`paper_id`, `question_no`),
  KEY `idx_fingerprint` (`question_fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='题目：不软删、随卷子走；需要查询计算的关系在证据表规范化，不扫 JSON';


-- ============================================================
-- ⑤ 作答（一次考试一行）
-- ============================================================

CREATE TABLE IF NOT EXISTS `corecraft`.`kp_exam_attempt` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT,
  `paper_id`              BIGINT        NOT NULL COMMENT '所属试卷',
  `user_id`               BIGINT        NOT NULL COMMENT '所属用户ID',
  `replay_of_attempt_id`  BIGINT        DEFAULT NULL COMMENT '重做指向原 attempt；NULL=首次作答。重做=新建 attempt 指向原记录，原记录不动，不做卷子去重',
  `status`                VARCHAR(16)   NOT NULL DEFAULT 'answering' COMMENT 'answering=作答中 / grading=判分中 / partial=部分判定 / completed=完成 / failed=失败',
  `started_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始作答时间',
  `submitted_at`          DATETIME      DEFAULT NULL COMMENT '交卷时间',
  `completed_at`          DATETIME      DEFAULT NULL COMMENT '判分完成时间',
  `score`                 DECIMAL(6,2)  DEFAULT NULL COMMENT '实得分；partial 时只算已判定题，未判定不计入也不落 0（NULL=还没分）',
  `max_score`             DECIMAL(6,2)  NOT NULL DEFAULT 0 COMMENT '整卷满分',
  `grade_task_id`         BIGINT        DEFAULT NULL COMMENT '判分走任务中心时的任务 id（可空）',
  `create_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_paper` (`paper_id`),
  KEY `idx_replay_of` (`replay_of_attempt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='作答：不软删（历史记录，与答案一样物理留痕）';


-- ============================================================
-- ⑥ 答案与判定（按 grade_revision 留痕，物理删）
-- ============================================================

CREATE TABLE IF NOT EXISTS `corecraft`.`kp_exam_answer` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `attempt_id`      BIGINT        NOT NULL COMMENT '所属作答',
  `question_id`     BIGINT        NOT NULL COMMENT '所属题目',
  `answer_payload`  TEXT          COMMENT 'JSON：考生作答原文（选择项、文字答案）',
  `verdict`         VARCHAR(16)   DEFAULT NULL COMMENT '判定结果；NULL=未判定。未判定不能落 0，缺失判定不进 paperScore→mastery 的隐含零分路径',
  `score_ratio`     DECIMAL(5,4)  DEFAULT NULL COMMENT '得分率 0~1；NULL=未判定',
  `score`           DECIMAL(6,2)  DEFAULT NULL COMMENT '实得分；NULL=未判定',
  `explanation`     TEXT          COMMENT '讲解（判分产物，回看用）',
  `grade_payload`   TEXT          COMMENT 'JSON：评分点级判定快照；按 grade_revision 留痕',
  `grade_revision`  INT           NOT NULL DEFAULT 0 COMMENT '判分版本，单调递增；0=未判。新 revision 才产生替代证据',
  `result_id`       VARCHAR(64)   DEFAULT NULL COMMENT '判分回调幂等键：相同 result_id 幂等返回，重试不重复产证据',
  `graded_at`       DATETIME      DEFAULT NULL COMMENT '判分时间',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attempt_question` (`attempt_id`, `question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='答案与判定：物理删不用软删——唯一键 (attempt_id,question_id) 撞不起软删墓碑（实施计划 §1.4）';


-- ============================================================
-- ⑦ 掌握度证据（不可变，不软删，永远保留）
-- ============================================================

CREATE TABLE IF NOT EXISTS `corecraft`.`kp_mastery_evidence` (
  `id`                     BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`                BIGINT        NOT NULL COMMENT '所属用户ID（冗余列；挂证据前校验与 attempt/answer 一致，防跨用户挂证据）',
  `topic_id`               BIGINT        NOT NULL COMMENT '归属知识点',
  `attempt_id`             BIGINT        NOT NULL COMMENT '来源作答',
  `answer_id`              BIGINT        NOT NULL COMMENT '来源答案',
  `rubric_key`             VARCHAR(64)   NOT NULL DEFAULT '__question__' COMMENT '评分点 key；整题证据固定 __question__。非空是刻意的：避免 MySQL 唯一键里 NULL 放行重复行',
  `grade_revision`         INT           NOT NULL COMMENT '判分版本（证据诞生于哪一版判定）',
  `attribution_revision`   INT           NOT NULL COMMENT '归属版本（证据诞生于哪一版归属）',
  `source`                 VARCHAR(16)   NOT NULL DEFAULT 'paper' COMMENT '证据来源；v1 只有 paper（随堂/考试/复习卷同链路）',
  `score_ratio`            DECIMAL(5,4)  NOT NULL COMMENT '得分率 0~1',
  `topic_weight`           DECIMAL(5,4)  NOT NULL COMMENT '知识归属权重：主考 1 / 辅考 <1；同题各考点之和 ≤1；unattributed 不产证据',
  `reliability_weight`     DECIMAL(6,4)  NOT NULL DEFAULT 1.0000 COMMENT '归属可靠性权重；hash 与证据不一致（epoch 内一般编辑）时打折 ×0.5，重放自然压低可信度',
  `novelty_weight`         DECIMAL(6,4)  NOT NULL DEFAULT 1.0000 COMMENT '新颖性权重：跨卷撞车/重复提交打折。同卷重做是合法复习，只降新颖性、不撤调度资格',
  `ability_eligible`       TINYINT       NOT NULL DEFAULT 1 COMMENT '计入能力 A 的资格',
  `retention_eligible`     TINYINT       NOT NULL DEFAULT 1 COMMENT '回忆测量资格（主考且测回忆）——推进 last_mastery_at / R(t)',
  `schedule_eligible`      TINYINT       NOT NULL DEFAULT 1 COMMENT '推进复习调度的资格（H、next_review_at）',
  `content_hash`           CHAR(64)      DEFAULT NULL COMMENT '评估时点的知识点内容指纹快照',
  `content_epoch`          INT           NOT NULL DEFAULT 1 COMMENT '评估时点的正文 epoch 快照；与 topic.content_epoch 相等才算当前有效——当前有效判据只有这一条',
  `assessed_at`            DATETIME      NOT NULL COMMENT '评估时间（确定性重放的顺序锚点之一）',
  `superseded_by`          BIGINT        DEFAULT NULL COMMENT '被哪条新证据替代（重新判分/重新归属时打标）；NULL=当前有效。重归属遇 rubric 结构变化时按 question 粒度整体替代',
  `create_time`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evidence` (`answer_id`, `rubric_key`, `topic_id`, `grade_revision`, `attribution_revision`),
  KEY `idx_user_topic_time` (`user_id`, `topic_id`, `assessed_at`),
  KEY `idx_attempt` (`attempt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='掌握度证据：不可变、不软删、永远保留。重放顺序 (assessed_at, attempt_id, id)';


-- ============================================================
-- ⑧ kp_topic 补内容版本锚点（幂等补列）
-- ============================================================

-- content_hash：标题+正文归一化指纹，任何内容变化都更新。
--   两条写路径都要维护（KpTopicServiceImpl.updateTopic + KpGenStepMapper.updateTopicContent），
--   算法与题目指纹 question_fingerprint 同源（同一套归一化+哈希）。
--   ⚠️ 存量行回填 NULL 是**有意的**：归一化算法住在 Java 里，SQL 里 SHA2 一把
--   必然和应用算的对不上（空白折叠/首尾修剪语义不同）⇒ 留 NULL，
--   由写路径在下一次保存/生成正文时补算；证据生成前若为 NULL 必须先补算（幂等）。
-- content_epoch：重大正文版本号，初始 1，只有大幅重写/重新生成才递增。
--   ADD COLUMN 自动给存量行填 1（DEFAULT 1），不用单独回填。
--
-- MySQL 的 ADD COLUMN 没有 IF NOT EXISTS ⇒ 用 information_schema 判断一次再执行。
-- （ai_vendor_catalog.sql 同款写法。）
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'corecraft' AND table_name = 'kp_topic' AND column_name = 'content_hash') > 0,
  'SELECT ''content_hash 列已存在，跳过'' AS notice',
  'ALTER TABLE `corecraft`.`kp_topic` ADD COLUMN `content_hash` CHAR(64) DEFAULT NULL COMMENT ''标题+正文归一化指纹（任何内容变化都更新；NULL=存量行未计算，写路径补算）'' AFTER `content`');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'corecraft' AND table_name = 'kp_topic' AND column_name = 'content_epoch') > 0,
  'SELECT ''content_epoch 列已存在，跳过'' AS notice',
  'ALTER TABLE `corecraft`.`kp_topic` ADD COLUMN `content_epoch` INT NOT NULL DEFAULT 1 COMMENT ''重大正文版本号：大幅重写/重新生成才递增。证据是否当前有效只看 evidence.content_epoch == 本列'' AFTER `content_hash`');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ========== ⑨ 回读校验 ==========
-- 期望：六张表各一行（InnoDB / utf8mb4_unicode_ci）。
SELECT table_name, engine, table_collation, table_comment
  FROM information_schema.tables
 WHERE table_schema = 'corecraft'
   AND table_name IN ('kp_topic_learning','kp_exam_paper','kp_exam_question',
                      'kp_exam_attempt','kp_exam_answer','kp_mastery_evidence')
 ORDER BY table_name;

-- 期望：content_hash / content_epoch 各一行；content_epoch 的 default = 1。
SELECT column_name, column_type, is_nullable, column_default, column_comment
  FROM information_schema.columns
 WHERE table_schema = 'corecraft' AND table_name = 'kp_topic'
   AND column_name IN ('content_hash','content_epoch')
 ORDER BY column_name;


-- ============================================================
-- 回滚（确认不要之后手动执行；证据/作答不可逆，删了就没了）
-- ============================================================
-- DROP TABLE IF EXISTS `corecraft`.`kp_mastery_evidence`;
-- DROP TABLE IF EXISTS `corecraft`.`kp_exam_answer`;
-- DROP TABLE IF EXISTS `corecraft`.`kp_exam_attempt`;
-- DROP TABLE IF EXISTS `corecraft`.`kp_exam_question`;
-- DROP TABLE IF EXISTS `corecraft`.`kp_exam_paper`;
-- DROP TABLE IF EXISTS `corecraft`.`kp_topic_learning`;
-- ALTER TABLE `corecraft`.`kp_topic` DROP COLUMN `content_hash`;
-- ALTER TABLE `corecraft`.`kp_topic` DROP COLUMN `content_epoch`;
