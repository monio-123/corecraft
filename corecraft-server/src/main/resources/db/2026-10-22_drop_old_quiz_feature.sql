-- =============================================================================
-- 2026-10-22 撤掉旧的自测题那套
--
-- 【为什么全撤】
-- 用户原话："把旧的完全撤掉吧 旧的非常不好用 缺失的功能我后续会用新的来补回来"：
-- 旧的**题存在库里 + 本地判分 + 每日复习**这套，和新的「考试」（临时、AI 判分、
-- 题量/难度可配）是两种东西。继续留着，就一直是**两套题型表、两份解析器、
-- 两份出题提示词、两处判分口径** —— 后面每加一次题型都要在两处各改一遍。
-- 后续的功能在**新的可配置能力**上重做，不在这套上补。
--
-- 【撤掉什么】
--   1. 表：kp_topic_quiz（题）、kp_topic_quiz_record（每日复习的作答记录）
--   2. 配置：引导问题「生成几道测验题」（ai.prompt.question.make_quiz:none）——
--      它是详情页「+ 几道」按钮的入口，页面撤了它就成了一个没人点的按钮
--   3. 代码：前端 QuizPanel / QuizCard / DailyReview / utils/quizView /
--      utils/quizAnswer；后端 KpTopicQuiz(Record) 实体 / 仓储 / Mapper /
--      KpQuizRecordController / Service / Req / Resp；KpTopic 链路上的
--      quizQuestions 字段（请求/响应 DTO + 落库 + 读库拼 JSON）
--
-- 【首页那个「今日待复习」入口没撤】
-- 用户原话："首页的复习只保留页面的入口就行 所有逻辑都撤掉" ⇒ 入口留着，
-- 抽题规则、每日复习的单题流、答题记录全撤。入口现在指向知识点列表
-- （新的考试在那儿选范围：/exam?ids=…）。
--
-- ⚠️ **DROP TABLE 是不可逆的**：库里那 27 道题 + 5 条作答记录一起没了。
--    要留就**先跑这段**把数据导出来（MySQL 客户端里执行，结果贴进文件）：
--        SELECT * FROM `kp_topic_quiz`;
--        SELECT * FROM `kp_topic_quiz_record`;
--    导出件见 _archive/2026-10-22_old_quiz_data.sql。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 两张表
-- -----------------------------------------------------------------------------
-- 顺序：先记录表（它外键指着题目表，虽然库里没有真的外键约束，但读起来是那个意思）。
DROP TABLE IF EXISTS `kp_topic_quiz_record`;
DROP TABLE IF EXISTS `kp_topic_quiz`;

-- -----------------------------------------------------------------------------
-- 2. 引导问题「生成几道测验题」
-- -----------------------------------------------------------------------------
-- 节点和值一起删：节点没了、值还在的话，系统配置页上会多出一个没有说明书的孤儿配置项。
DELETE FROM `sys_config` WHERE `user_id` = 0 AND `config_key` = 'ai.prompt.question.make_quiz:none';
DELETE FROM `sys_config_node` WHERE `config_key` = 'ai.prompt.question.make_quiz:none';

-- -----------------------------------------------------------------------------
-- 3. 引导问题重新编号（去掉 make_quiz 之后留下的空档）
-- -----------------------------------------------------------------------------
-- 原来：rename=1 pick_category=2 pick_tags=3 make_quiz=4 comb=5 organize=6
-- 现在：rename=1 pick_category=2 pick_tags=3 comb=4 organize=5
-- ⚠️ 初始化脚本 2026-10-21_sys_config_node_tree.sql 里的编号也同步改了
--    （两边必须一样，否则重放那个脚本会和这里对不上）。
UPDATE `sys_config_node` SET `sort` = 4 WHERE `config_key` = 'ai.prompt.question.comb:none';
UPDATE `sys_config_node` SET `sort` = 5 WHERE `config_key` = 'ai.prompt.question.organize:categories,tags';

-- -----------------------------------------------------------------------------
-- 4. 字典里那条不用动
-- -----------------------------------------------------------------------------
-- sys_dict_item 里的 make_quiz 早就 is_delete=1（提示词搬进 sys_config 时就下线了），
-- 而且 2026-10-21 那个初始化脚本的 4.2 段已经加了 `i.value NOT LIKE 'make_quiz%'`，
-- 重放不会把它从字典里迁回来。
