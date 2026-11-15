-- ============================================================
-- 2026-11-10 生成任务中心（二期）
--
-- ## 为什么
--
-- 现在几百条正文的循环跑在浏览器标签页里：关页面 / 断网 / 睡眠 = 同一个死法，
-- 骨架和占位已落库、正文没写，留下一堆空正文；而且「参数 + 提示词」只存在弹窗内存里，
-- 重跑一定漂移（提示词在 sys_config 里随时会改）。
--
-- 这两张表把「任务」和「每次 AI 调用」落成数据：
--   kp_gen_task —— 一个任务一行（进度 / 参数快照 / 提示词指纹）
--   kp_gen_step —— 一次调用一行（材料 / 提示词快照 / token / 耗时 / 结果）
--
-- ## 设计口径
--
-- ① **任务 = 一串「材料 → 一次调用 → 一个落库动作」**，后端不理解材料语义。
--    材料由前端用 `buildMaterial` 算好传上来 ⇒ 服务端不必把 JS 那套拼装重写一份
--    （两份定义必然漂移），任务中心也才能通用（写正文 / 出卷 / 判分都是这个形状）。
-- ② **提示词快照存「实际发出去的 messages 原文」** —— 后端 `AiDelta.prompt` 本来就是
--    开流前原样发出去的那份，直接存进 `kp_gen_step.prompt_snapshot`，不用重拼。
-- ③ **材料也落库**（`kp_gen_step.material`）：断点续写 / 重跑不用前端再算一遍，
--    而「任务创建那一刻的树」也才有据可查。
-- ④ token 拿得到：DeepSeek 的 SSE 最后一帧带 `usage`，以前我们只读 delta 把它扔了。
--    拿不到就不记（留空），**不本地估算后当真数用**。
--
-- 执行时机：一次性建表（幂等，可重复跑）。
-- ============================================================

CREATE TABLE IF NOT EXISTS `corecraft`.`kp_gen_task` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT       NOT NULL COMMENT '归属用户',
  `type`          VARCHAR(32)  NOT NULL DEFAULT 'content' COMMENT '任务类型：content=写正文 / outline=生成目录 / exam_paper=出卷 …',
  `title`         VARCHAR(200) NOT NULL DEFAULT '' COMMENT '人看得懂的任务名（主题 / 目录 / 卷名）',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'queued' COMMENT 'queued / running / succeeded / failed / canceling / canceled',
  `total`         INT          NOT NULL DEFAULT 0 COMMENT '总步数',
  `done_count`    INT          NOT NULL DEFAULT 0 COMMENT '已尝试（成功+失败）',
  `failed_count`  INT          NOT NULL DEFAULT 0,
  `params`        TEXT         NULL COMMENT '参数快照 JSON（主题 / 目录 / 开关 / 模型 / 是否思考 / maxTokens）',
  `prompt_key`    VARCHAR(64)  NULL COMMENT '提示词配置键（ai.prompt.*）',
  `prompt_len`    INT          NULL COMMENT '提示词长度（指纹的一部分）',
  `prompt_md5`    CHAR(32)     NULL COMMENT '提示词 md5 —— 重跑时能看出「提示词已经变了」',
  `result`        TEXT         NULL COMMENT '结果摘要 JSON',
  `error`         TEXT         NULL COMMENT '任务级错误',
  `start_time`    DATETIME     NULL,
  `finish_time`   DATETIME     NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生成任务';

CREATE TABLE IF NOT EXISTS `corecraft`.`kp_gen_step` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `task_id`          BIGINT       NOT NULL,
  `seq`              INT          NOT NULL COMMENT '从 1 开始，决定执行与重试顺序',
  `title`            VARCHAR(200) NOT NULL DEFAULT '' COMMENT '这一步叫什么（知识点标题 / 题号…）',
  `topic_id`         BIGINT       NULL COMMENT '结果写到哪条知识点（content 类型用）',
  `status`           VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending / running / ok / failed / skipped',
  `material`         TEXT         NULL COMMENT '喂给模型的材料（前端算好传的）',
  `prompt_snapshot`  LONGTEXT     NULL COMMENT '实际发给模型的 messages 原文',
  `content_len`      INT          NULL,
  `token_prompt`     INT          NULL COMMENT '拿不到就留空，不估算',
  `token_completion` INT          NULL,
  `duration_ms`      INT          NULL,
  `finish_reason`    VARCHAR(16)  NULL COMMENT 'stop / length …（length = 被截断）',
  `error`            VARCHAR(500) NULL,
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_task_seq` (`task_id`, `seq`),
  KEY `idx_topic` (`topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生成任务的每一步（一次 AI 调用）';


-- ============================================================
-- 回滚（确认不要之后手动执行）
-- ============================================================
-- DROP TABLE IF EXISTS `corecraft`.`kp_gen_step`;
-- DROP TABLE IF EXISTS `corecraft`.`kp_gen_task`;
