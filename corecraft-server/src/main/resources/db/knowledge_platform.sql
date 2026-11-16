-- corecraft-web 业务表：单一 SQL 入口
--
-- 本文件建的表（共 13 张）：
--   kp_topic         = 知识点（标题、内容、类型、层级归属、所属目录、内容版本锚点）
--   kp_category      = 知识目录（树的骨架；知识点单选归属，标签不参与建树）
--   kp_tag           = 标签主数据（按 user 隔离；多选关联点，只做检索，不进树）
--   kp_topic_tag     = 知识点 ↔ 标签 纯关联表（无字段冗余）
--   kp_topic_relation = 知识点之间的关联
--   ai_config        = AI 总开关（每人一行）
--   ai_endpoint      = AI 接口（每人多个）
--   kp_topic_learning   = 学习投影（学过标记 / 掌握度缓存 / 复习调度，2026-11-11）
--   kp_exam_paper       = 试卷：随堂测验 / 目录考试 / 复习卷共用（2026-11-11）
--   kp_exam_question    = 试卷中的一道题（题目快照 + 归属快照 + 指纹，2026-11-11）
--   kp_exam_attempt     = 一次作答（重做 = 新 attempt 指向原记录，2026-11-11）
--   kp_exam_answer      = 一道题的作答与判定（按 grade_revision 留痕，2026-11-11）
--   kp_mastery_evidence = 不可变知识证据（掌握度唯一权威来源，2026-11-11）
--
-- ⚠️ **这里的表结构 = 线上库的现状**（2026-10-26 逐列比对 information_schema 核过）。
--    以前这里缺 kp_topic.type_id、缺 ai_endpoint.params、kp_category 的唯一键还是老的
--    ⇒ 照着这个文件建出来的新库，代码一查就 Unknown column。
--    **结构只存这一份**：以后加列 = 改这里 + 另写一个一次性迁移脚本给"已存在的库"用。
--
-- 用法：
--   首次部署 / 完全重置 = 跑这一个文件（DROP + CREATE，**可重复跑**）
--   业务表清空在文件顶部，表按"被引用→引用方"顺序 DROP，再 CREATE
--
--   ⚠️ **跑完这个文件不要再按文件名重放其它一次性迁移脚本** —— 那些是给"已存在的库"用的
--      （各自带跑前自检），重放会撞 Duplicate column / Duplicate key。
--      唯一要跟着一块跑的：**2026-10-26_ai_vendor_catalog.sql**（灌厂商 + 模型的字典数据，
--      它的补列段是幂等的，见那个文件）。
--
-- 不在本文件内：
--   - 菜单 / 权限初始化（见 corecraft-web_menu.sql / menu_init.sql）
--   - 系统管理表（用户/角色/字典等）由 corecraft 脚手架自带 SQL 负责
--   - 厂商 / 模型字典数据（见 2026-10-26_ai_vendor_catalog.sql）

-- ============================================================
-- 清理
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `kp_topic_relation`;
-- 学习 / 考试 / 证据那六张（2026-11-11）：引用方先删
DROP TABLE IF EXISTS `kp_mastery_evidence`;
DROP TABLE IF EXISTS `kp_exam_answer`;
DROP TABLE IF EXISTS `kp_exam_attempt`;
DROP TABLE IF EXISTS `kp_exam_question`;
DROP TABLE IF EXISTS `kp_exam_paper`;
DROP TABLE IF EXISTS `kp_topic_learning`;
-- kp_topic_quiz：旧自测题那套已经整块撤掉（2026-10-22），这里只做清理
-- —— 老库里可能还留着，重置时顺手删掉。
DROP TABLE IF EXISTS `kp_topic_quiz`;
DROP TABLE IF EXISTS `kp_topic_tag`;
DROP TABLE IF EXISTS `kp_tag`;
DROP TABLE IF EXISTS `kp_topic`;
DROP TABLE IF EXISTS `kp_category`;
DROP TABLE IF EXISTS `ai_endpoint`;
DROP TABLE IF EXISTS `ai_config`;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 知识点表（业务核心）
-- ============================================================

CREATE TABLE IF NOT EXISTS `kp_topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `title` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '知识点标题（未填写时前端展示"未命名"）',
  `content` TEXT COMMENT '知识点内容（Markdown）',
  `content_hash` CHAR(64) DEFAULT NULL COMMENT '标题+正文归一化指纹（任何内容变化都更新；与题目指纹同源算法。NULL=存量行未计算，写路径补算）',
  `content_epoch` INT NOT NULL DEFAULT 1 COMMENT '重大正文版本号：大幅重写/重新生成才递增。证据是否当前有效只看 evidence.content_epoch == 本列',
  `type_id` BIGINT DEFAULT NULL COMMENT '知识点类型（NULL=笔记，默认档）',
  `parent_topic_id` BIGINT DEFAULT NULL COMMENT '父知识点ID（自关联层级）',
  `category_id` BIGINT DEFAULT NULL COMMENT '所属知识目录ID（NULL=未归类；单选，互斥）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_delete` (`user_id`, `is_delete`),
  KEY `idx_parent_topic` (`parent_topic_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_type_id` (`type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点';

-- ============================================================
-- 知识目录表（树的骨架）
-- ============================================================

-- 目录与标签是两个正交概念，别混用：
--   目录 = 归属（这条知识属于哪），单选互斥，树视图的节点
--   标签 = 关联（这条知识涉及什么），多选平铺，只做检索筛选
--
-- 刻意不带 is_delete：目录没有独立价值（删了就是没了），物理删更干净。
-- 若做软删，唯一键会让"删过的目录名"永久无法重建。
--
-- ⚠️ **唯一键带 parent_id**（uk_user_parent_name）：目录是有层级的，
--    同名目录可以出现在不同父目录下（"索引"可以在"MySQL"下也可以在"Redis"下）。
--    老键 uk_user_name(user_id, name) 会把这种情况挡掉。
CREATE TABLE IF NOT EXISTS `kp_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `name` VARCHAR(50) NOT NULL COMMENT '目录名',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父目录ID（NULL=顶层；实体 getParentId() 把历史 NULL 归一成 0）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_parent_name` (`user_id`, `parent_id`, `name`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识目录';

-- ============================================================
-- 附属表
-- ============================================================

-- 标签主表（独立实体：按 user 隔离；同 (user, name) 唯一）
-- 仅 name 一个字段；前端用"category::name"展示分组，但 category 不落库
-- ⚠️ 唯一键名跟线上库一致（`uk_user_category_name`）：键名是当年 category 还落库时留下的，
--    列没变（user_id + name）。这里跟线上库同名，两边才对得上。
CREATE TABLE IF NOT EXISTS `kp_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_category_name` (`user_id`, `name`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签主数据';

-- 知识点 ↔ 标签 纯关联表（无字段冗余；改 tag 名 = 改 kp_tag 一行）
CREATE TABLE IF NOT EXISTS `kp_topic_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `topic_id` BIGINT NOT NULL COMMENT '知识点ID',
  `tag_id` BIGINT NOT NULL COMMENT '标签ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_topic_tag` (`topic_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点-标签关联';

-- 关联关系
CREATE TABLE IF NOT EXISTS `kp_topic_relation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `topic_id` BIGINT NOT NULL COMMENT '知识点ID',
  `related_topic_id` BIGINT NOT NULL COMMENT '关联知识点ID',
  `relation_type` VARCHAR(20) NOT NULL DEFAULT 'related' COMMENT '关联类型：prerequisite/extends/contrast/related',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_topic_relation` (`topic_id`, `related_topic_id`),
  KEY `idx_related_topic` (`related_topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点关联';

-- ⚠️ **旧自测题那套（kp_topic_quiz）已经整块撤掉**（2026-10-22）：题存在库里 + 本地判分
--    + 每日复习。旧那套和新的「考试」（临时、AI 判分、题量/难度可配）是两种东西，
--    留着就一直是两套题型表、两份解析器。**出题现在由 `ai.prompt.exam.*` 那组提示词负责。**
--    ⇒ **这里不再建它**（以前建了、又让 2026-10-22 那个脚本删掉，纯属绕一圈；
--      而"跑这一个文件 = 当前结构"的前提也要求它不存在 —— 线上库里它早没了）。
--    撤表脚本：2026-10-22_drop_old_quiz_feature.sql（数据备份在 _archive/）。

-- ============================================================
-- AI 设置 + AI 接口（2026-10-06 新增，2026-10-07 拆成两张表）
-- 详细取舍见同目录 2026-10-07_ai_endpoint_multi.sql 的头部注释。
--
-- **两张表各管一件事**：
--   ai_config   —— 一人一行，只有一个总开关。enabled 默认 0：没开启就不出现任何 AI 入口。
--   ai_endpoint —— 一人多行，每个 base_url + model + api_key 一行，提问时选一个用。
--   name 是自己起的别名，列表里靠它区分（同一个厂商可能配两个不同模型）。
--
-- 不设 provider 字段（厂商靠 base_url 决定）；
-- 不放 sys_dict（字典表无 user_id，放进去就是全局一份，与"每人一份"矛盾）。
-- 「当前选中哪个接口」不落库，存前端 localStorage——那是当下的临时选择，不是用户资料。
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_config` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户ID',
  `enabled`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用AI功能：0关闭 1开启',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI设置：每人一行，只管开关。接口在 ai_endpoint';

CREATE TABLE IF NOT EXISTS `ai_endpoint` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户ID',
  `name`        VARCHAR(64)  NOT NULL COMMENT '自己起的名字，列表里靠它区分',
  `base_url`    VARCHAR(255)          DEFAULT NULL COMMENT 'OpenAI兼容接口地址，例：https://api.deepseek.com/v1',
  `model`       VARCHAR(100)          DEFAULT NULL COMMENT '模型名，例：deepseek-chat',
  `params`      TEXT                   DEFAULT NULL COMMENT '端点附加参数 JSON：{"vendor":"厂商code","custom":{...}}。custom 展开进 chat 请求体（不能覆盖 model/stream/messages）',
  `api_key`     VARCHAR(255)          DEFAULT NULL COMMENT 'API Key。只存后端，绝不返回给前端',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI接口：每人多个，提问时选一个用';

-- ============================================================
-- 学习掌握度与每日复习（2026-11-11）
-- 设计：TODO-learning-mastery.md §10；已存在库的迁移脚本：2026-11-11_learning_exam_tables.sql。
--
-- 一句话：**证据表是唯一权威，学习投影全是缓存**（ability / review_count 等可从证据
-- 重放重建；learned_at 是用户显式标记，不能从证据反推）。证据不可变、不软删、
-- 重放顺序 (assessed_at, attempt_id, id)；判分为空 = 未判定，不落 0。
-- ============================================================

CREATE TABLE IF NOT EXISTS `kp_topic_learning` (
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

CREATE TABLE IF NOT EXISTS `kp_exam_paper` (
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

CREATE TABLE IF NOT EXISTS `kp_exam_question` (
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

CREATE TABLE IF NOT EXISTS `kp_exam_attempt` (
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

CREATE TABLE IF NOT EXISTS `kp_exam_answer` (
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

CREATE TABLE IF NOT EXISTS `kp_mastery_evidence` (
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